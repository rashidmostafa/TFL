package app.tfl.core.model.security

/** How long TFL may sit in the background before it locks. */
enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    SECONDS_30(30_000),
    MINUTE_1(60_000),
    MINUTES_5(300_000),
}

/** What the duress PIN does when entered on the lock screen. */
enum class DuressMode {
    /** No duress PIN is configured. */
    NONE,

    /** Opens a separate, empty decoy profile. */
    DECOY,

    /** Wipes TFL from this phone and shows a fresh app. */
    WIPE,
}

/** Which of the two profiles an unlock opened. */
enum class Profile { REAL, DECOY }

/** Where Android keeps TFL's hardware keys, as reported by the Keystore. */
enum class KeyStorageLevel { STRONGBOX, TEE, SOFTWARE }

/** Physical gesture that will trigger a panic wipe (saved now, enforced in a later phase). */
enum class PanicTrigger { SHAKE, VOLUME_DOWN }
