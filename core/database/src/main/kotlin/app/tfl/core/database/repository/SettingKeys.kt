package app.tfl.core.database.repository

import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.PanicTrigger

/** A typed setting stored as text. Unknown or unreadable values fall back to [default]. */
class SettingKey<T>(
    val name: String,
    val default: T,
    internal val encode: (T) -> String,
    internal val decode: (String) -> T?,
)

private fun booleanKey(name: String, default: Boolean) =
    SettingKey(name, default, { it.toString() }, { it.toBooleanStrictOrNull() })

private fun intKey(name: String, default: Int) =
    SettingKey(name, default, { it.toString() }, { it.toIntOrNull() })

private inline fun <reified E : Enum<E>> enumKey(name: String, default: E) =
    SettingKey(name, default, { it.name }, { value -> enumValues<E>().firstOrNull { it.name == value } })

/** Settings kept in the profile's encrypted database (anything not needed before unlock). */
object SettingKeys {
    val AUTO_LOCK = enumKey("security.auto_lock", AutoLockTimeout.IMMEDIATELY)

    /** Saved only: enforced in a later phase. */
    val FACE_DOWN_LOCK = booleanKey("security.face_down_lock", false)

    /** Saved only: enforced in a later phase. */
    val PANIC_TRIGGER = enumKey("security.panic_trigger", PanicTrigger.SHAKE)

    val TOR_ENABLED = booleanKey("network.tor", true)
    val NEARBY_ENABLED = booleanKey("network.nearby", true)
    val RELAY_ENABLED = booleanKey("network.relay", true)
    val BATTERY_SAVER = booleanKey("network.battery_saver", false)

    /** "Stay reachable in the background": Nearby keeps running, with its keys, while TFL is locked. */
    val STAY_REACHABLE = booleanKey("network.stay_reachable", true)

    /** New-message notifications name the friend; off, they say only "New message". */
    val NOTIFY_SHOW_SENDER = booleanKey("notifications.show_sender", false)
    val PAUSE_RELAY_ON_LOW_BATTERY = booleanKey("network.pause_relay_low_battery", true)
    val BRIDGE_MODE = enumKey("network.bridge_mode", BridgeMode.DIRECT)

    /** Absent means onboarding finished (the decoy profile is created finished). */
    val ONBOARDING_STEP = enumKey("onboarding.step", OnboardingStep.DONE)

    /** The identity came from a recovery phrase, so onboarding skips the phrase step. */
    val ONBOARDING_RESTORED = booleanKey("onboarding.restored", false)

    /** Onboarding asked for fingerprint unlock; the first screen after the commit point enrols it. */
    val BIOMETRIC_REQUESTED = booleanKey("onboarding.biometric_requested", false)

    // The decoy profile's own copies of lock settings. They make its Security screen behave
    // plausibly but never touch the real lock configuration.
    val DECOY_BIOMETRIC = booleanKey("decoy.biometric", false)
    val DECOY_WIPE_AFTER = intKey("decoy.wipe_after", 0)
    val DECOY_DURESS_MODE = enumKey("decoy.duress_mode", DuressMode.NONE)
}
