package app.tfl.feature.contacts

import app.tfl.core.model.contact.ContactKeys
import kotlin.random.Random

/** Keys for friends made up in tests: random bytes, since nothing is encrypted to them. */
internal object TestKeys {
    /** Keys no friend has: `othersNamed(name, TestKeys.none)` finds everyone with that name. */
    val none = ContactKeys(ByteArray(32), ByteArray(32), "0".repeat(32))

    fun random(seed: Int): ContactKeys {
        val sign = Random(seed).nextBytes(32)
        return ContactKeys(sign, Random(seed + 1_000).nextBytes(32), sign.copyOf(16).joinToString("") { "%02X".format(it) })
    }
}
