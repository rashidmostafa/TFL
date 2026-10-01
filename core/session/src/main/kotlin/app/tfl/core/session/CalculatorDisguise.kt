package app.tfl.core.session

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.tfl.core.crypto.lock.PinVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Swaps the launcher entry between TFL and a working calculator. Both entries are activity-aliases
 * in the app manifest; only one is enabled at a time. The secret code is stored as a salted hash
 * in the lock state.
 */
@Singleton
class CalculatorDisguise @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: PinVault,
) {
    private val packageManager: PackageManager get() = context.packageManager
    private val defaultEntry get() = ComponentName(context.packageName, DEFAULT_ALIAS)
    private val calculatorEntry get() = ComponentName(context.packageName, CALCULATOR_ALIAS)

    fun isEnabled(): Boolean =
        packageManager.getComponentEnabledSetting(calculatorEntry) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private val switched = MutableStateFlow(isEnabled())

    /** Whether the disguise is on, as it's switched: what's already showing (notifications) follows it. */
    val enabled: StateFlow<Boolean> = switched.asStateFlow()

    fun enable(code: ByteArray) {
        vault.setDisguiseCode(code)
        setLauncherEntries(calculator = PackageManager.COMPONENT_ENABLED_STATE_ENABLED, default = PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    fun disable() {
        resetLauncher()
        vault.clearDisguiseCode()
    }

    /** True when [code] is the disguise's secret code. Never throws: the calculator must just calculate. */
    fun matches(code: ByteArray): Boolean = try {
        vault.isSetUp() && vault.matchesDisguiseCode(code)
    } catch (e: Exception) {
        false
    }

    /** Back to the manifest defaults: TFL visible, calculator hidden. Used by disable and by wipe. */
    fun resetLauncher() = setLauncherEntries(
        calculator = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        default = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
    )

    private fun setLauncherEntries(calculator: Int, default: Int) {
        packageManager.setComponentEnabledSetting(calculatorEntry, calculator, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(defaultEntry, default, PackageManager.DONT_KILL_APP)
        switched.value = isEnabled()
    }

    companion object {
        /** Activity-alias names declared in the app manifest (namespace `app.tfl`, in every build type). */
        const val DEFAULT_ALIAS = "app.tfl.LauncherDefault"
        const val CALCULATOR_ALIAS = "app.tfl.LauncherCalculator"

        /** Opened explicitly by the calculator: with the disguise on, the launcher entry is the calculator. */
        const val MAIN_ACTIVITY = "app.tfl.MainActivity"
    }
}
