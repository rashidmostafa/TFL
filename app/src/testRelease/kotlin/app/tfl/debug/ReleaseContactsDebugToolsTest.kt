package app.tfl.debug

import app.tfl.feature.contacts.debug.ContactsDebugTools
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release app carries none of the contacts developer tools, nor their text. */
class ReleaseContactsDebugToolsTest {

    @Test
    fun noFakeFriendsKeyChangeSimulationOrTestCodes() {
        val methods = ContactsDebugTools::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { "DeveloperRows" in it || "addFakeFriends" in it || "testFriendCode" in it })
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("app.tfl.feature.contacts.debug.ContactsDebugEntryPoint")
        }
    }

    @Test
    fun noDebugStrings() {
        val strings = app.tfl.feature.contacts.R.string::class.java.fields.map { it.name }
        assertTrue("sanity: this is the contacts R class", "add_friend_title" in strings)
        assertFalse(strings.any { it.startsWith("contacts_debug_") })
    }
}
