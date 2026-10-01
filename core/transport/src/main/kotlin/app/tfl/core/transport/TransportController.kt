package app.tfl.core.transport

import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.database.repository.SettingKey
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.session.AppSession
import app.tfl.core.session.GateState
import app.tfl.core.session.TransportKeyring
import app.tfl.core.session.di.ApplicationScope
import app.tfl.core.transport.di.TransportDispatcher
import app.tfl.core.transport.radio.Radio
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the transport: an engine while [TransportKeyring] holds keys, Nearby while it may run, and
 * the foreground service around it. Nearby runs when it's switched on, ready (permissions,
 * Bluetooth, Location where needed) and not stopped from the notification; while TFL is locked,
 * only if "Stay reachable in the background" is on, which is also what keeps the keys through the
 * lock. The service needs only the permissions, so Bluetooth going off and on doesn't stop it (it
 * couldn't be started again from the background).
 *
 * Settings are read while unlocked and remembered through the lock. Housekeeping (expired
 * messages, old ids, undeliverable envelopes) runs whenever a profile is unlocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransportController @Inject constructor(
    private val keyring: TransportKeyring,
    private val session: AppSession,
    private val holder: DatabaseHolder,
    private val settings: SettingsRepository,
    private val messages: MessageRepository,
    private val messenger: Messenger,
    private val deps: EngineDeps,
    private val radio: Radio,
    private val readiness: NearbyReadiness,
    private val power: PowerSaving,
    private val service: TransportService,
    private val clock: DeviceClock,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:TransportDispatcher private val dispatcher: CoroutineDispatcher,
) : TransportStatus {
    private val engine = MutableStateFlow<TransportEngine?>(null)
    private val stopped = MutableStateFlow(false)

    private val nearbyOn = remembered(SettingKeys.NEARBY_ENABLED)
    private val stayReachable = remembered(SettingKeys.STAY_REACHABLE)
    private val batterySaver = remembered(SettingKeys.BATTERY_SAVER)
    private val unlocked = session.state.map { it is GateState.Unlocked }.distinctUntilChanged()

    /** Nearby may run: on, permitted, and not stopped from the notification. */
    private val allowed = combine(nearbyOn, readiness.permitted, stopped) { on, permitted, stopped -> on && permitted && !stopped }

    override val reachable: StateFlow<Set<Long>> = engine.flatMapLatest { it?.reachable ?: flowOf(emptySet()) }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    private val nearbyRunning = MutableStateFlow(false)

    override val running: StateFlow<Boolean> = nearbyRunning

    fun start() {
        appScope.launch(dispatcher) { runEngines() }
        appScope.launch(dispatcher) { housekeeping() }
        appScope.launch(dispatcher) {
            combine(allowed, stayReachable) { allowed, stay -> allowed && stay }.collect { keyring.keepWhileLocked = it }
        }
        // A stop from the notification lasts until the next unlock.
        appScope.launch(dispatcher) { unlocked.filter { it }.collect { stopped.value = false } }
    }

    /** The notification's Stop: Nearby stops until the next unlock, and while locked its keys are wiped. */
    fun stopFromNotification() {
        stopped.value = true
        if (!session.isUnlocked) keyring.release()
    }

    /** A scheduled message may be due: the outbox looks again. */
    fun wake() {
        engine.value?.let { current -> appScope.launch(dispatcher) { current.wake() } }
    }

    private suspend fun runEngines() {
        keyring.keys.collectLatest { keys ->
            if (keys == null) return@collectLatest
            coroutineScope {
                val engineScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
                val current = TransportEngine(engineScope, keys, deps)
                current.start()
                engine.value = current
                try {
                    val serviceWanted = combine(allowed, stayReachable, unlocked) { allowed, stay, unlocked -> allowed && (unlocked || stay) }
                    val saving = combine(batterySaver, power.saving) { own, system -> own || system }
                    combine(serviceWanted, readiness.ready, saving) { service, ready, save -> Triple(service, service && ready, save) }
                        .distinctUntilChanged()
                        .collect { (serviceOn, radioOn, save) ->
                            current.setSaving(save)
                            current.setRadio(if (radioOn) radio else null)
                            nearbyRunning.value = radioOn
                            service.setRunning(serviceOn)
                        }
                } finally {
                    engine.value = null
                    nearbyRunning.value = false
                    withContext(NonCancellable) {
                        current.stop()
                        service.setRunning(false)
                    }
                    engineScope.cancel()
                }
            }
        }
    }

    private suspend fun housekeeping() {
        holder.database.collectLatest { database ->
            if (database == null) return@collectLatest
            while (true) {
                val now = clock.currentTimeMillis()
                try {
                    messenger.tidy(now)
                } catch (e: DatabaseLockedException) {
                    return@collectLatest
                }
                // Until a message expires (or a new one might expire sooner), and at least hourly.
                withTimeoutOrNull(TIDY_AT_LEAST_EVERY_MILLIS) {
                    messages.nextChange(now).transformLatest { next ->
                        if (next != null) {
                            delay((next - clock.currentTimeMillis()).coerceAtLeast(0))
                            emit(Unit)
                        }
                    }.first()
                }
            }
        }
    }

    /** A setting's value while unlocked, remembered while locked (when there's nothing to read it from). */
    private fun <T> remembered(key: SettingKey<T>): StateFlow<T> =
        settings.observe(key).filter { holder.isOpen }.stateIn(appScope, SharingStarted.Eagerly, key.default)

    private companion object {
        const val TIDY_AT_LEAST_EVERY_MILLIS = 60 * 60_000L
    }
}
