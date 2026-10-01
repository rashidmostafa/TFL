package app.tfl.feature.contacts.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.pairing.PairingCodes
import app.tfl.core.crypto.pairing.SafetyNumbers
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.QrCode
import app.tfl.core.designsystem.component.QrMatrix
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SegmentedTab
import app.tfl.core.designsystem.component.SegmentedTabs
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflTextField
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.component.cornerMarks
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.session.PairingIdentity
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.fake.FakeContactsData
import app.tfl.feature.contacts.qr.QrEncoder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
internal data object TestFriendQrRoute

/** What the contacts developer tools need from the app graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactsDebugEntryPoint {
    fun contacts(): ContactRepository
    fun pairing(): PairingIdentity
    fun pairingCodes(): PairingCodes
    fun derivation(): IdentityKeyDerivation
    fun fingerprints(): Fingerprints
    fun sodium(): SodiumApi
    fun clock(): DeviceClock
    fun identities(): IdentityRepository
    fun safetyNumbers(): SafetyNumbers
}

/**
 * Contacts developer tools, compiled into debug builds only. Release builds compile the no-op
 * twin in `src/release`, so none of this code or its strings ships.
 */
object ContactsDebugTools {

    /**
     * The made-up friend behind "Test friend QR": one per process, so repeated scans find the same
     * friend. After TFL restarts, the test friend has a new key (handy for the "same person?" prompt).
     */
    private var testFriendSeed: ByteArray? = null

    /** Rows for Settings → Developer. */
    @Composable
    fun DeveloperRows(navController: NavController) {
        val tools = rememberTools()
        val scope = rememberCoroutineScope()
        var added by remember { mutableStateOf(false) }
        ListRow(
            title = stringResource(R.string.contacts_debug_fake_title),
            subtitle = stringResource(if (added) R.string.contacts_debug_fake_done else R.string.contacts_debug_fake_subtitle),
            icon = MaterialSymbols.GroupAdd,
            standalone = false,
            onClick = {
                scope.launch {
                    addFakeFriends(tools)
                    added = true
                }
            },
        )
        TflDivider()
        ListRow(
            title = stringResource(R.string.contacts_debug_test_qr_title),
            subtitle = stringResource(R.string.contacts_debug_test_qr_subtitle),
            icon = MaterialSymbols.QrCode2,
            standalone = false,
            onClick = { navController.navigate(TestFriendQrRoute) },
        )
    }

    /** A card on each friend's profile. */
    @Composable
    fun ProfileTools(contactId: Long, onKeyChanged: () -> Unit) {
        val tools = rememberTools()
        val scope = rememberCoroutineScope()
        TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            SectionHeader(
                stringResource(R.string.contacts_debug_section),
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
                icon = MaterialSymbols.DeveloperMode,
                iconTint = TflTheme.colors.warning,
            )
            ListRow(
                title = stringResource(R.string.contacts_debug_key_change_title),
                subtitle = stringResource(R.string.contacts_debug_key_change_subtitle),
                icon = MaterialSymbols.KeyOff,
                standalone = false,
                onClick = {
                    scope.launch {
                        val now = tools.clock().currentTimeMillis()
                        tools.contacts().changeKeys(contactId, randomKeys(tools), KeySource.DEBUG_SIMULATION, now)
                        onKeyChanged()
                    }
                },
            )
        }
    }

    /**
     * Under each camera: takes a code's text as if the camera had read it, so a phone can be tested
     * over adb (`adb shell input text …`) without pointing it at a screen.
     */
    @Composable
    fun ScanTools(onCode: (String) -> Unit) {
        val field = rememberTextFieldState()
        TflCard(verticalSpacing = 8.dp) {
            SectionHeader(
                stringResource(R.string.contacts_debug_section),
                icon = MaterialSymbols.DeveloperMode,
                iconTint = TflTheme.colors.warning,
            )
            TflTextField(
                field,
                placeholder = stringResource(R.string.contacts_debug_code_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )
            GhostButton(
                stringResource(R.string.contacts_debug_code_use),
                onClick = { onCode(field.text.toString().trim()) },
                size = TflButtonSize.Medium,
            )
        }
    }

    fun NavGraphBuilder.debugDestinations(navController: NavController) {
        composable<TestFriendQrRoute> { TestFriendQrScreen(onBack = { navController.popBackStack() }) }
    }

    @Composable
    private fun rememberTools(): ContactsDebugEntryPoint {
        val context = LocalContext.current
        return remember(context) { EntryPointAccessors.fromApplication(context, ContactsDebugEntryPoint::class.java) }
    }

    /** Six friends, one per state: verified three ways, unverified, key changed, blocked. */
    private suspend fun addFakeFriends(tools: ContactsDebugEntryPoint) {
        val contacts = tools.contacts()
        val now = tools.clock().currentTimeMillis()
        val names = FakeContactsData.debugFriendNames
        contacts.recordPairing(names[0], randomKeys(tools), mutual = true, nowMillis = now - 30 * DAY)
        contacts.recordPairing(names[1], randomKeys(tools), mutual = false, nowMillis = now - 20 * DAY).also {
            contacts.markVerified(it.id, VerificationMethod.SAFETY_NUMBER_QR, now - 19 * DAY)
        }
        contacts.recordPairing(names[2], randomKeys(tools), mutual = false, nowMillis = now - 10 * DAY)
        contacts.recordPairing(names[3], randomKeys(tools), mutual = true, nowMillis = now - 40 * DAY).also {
            contacts.changeKeys(it.id, randomKeys(tools), KeySource.DEBUG_SIMULATION, now - DAY)
        }
        contacts.recordPairing(names[4], randomKeys(tools), mutual = false, nowMillis = now - 5 * DAY).also {
            contacts.setBlocked(it.id, true)
        }
        contacts.recordPairing(names[5], randomKeys(tools), mutual = false, nowMillis = now - 3 * DAY).also {
            contacts.markVerified(it.id, VerificationMethod.SAFETY_NUMBER_MANUAL, now - 2 * DAY)
            contacts.setNickname(it.id, "Alex (work)")
        }
    }

    /** Random bytes stand in for keys: nothing is encrypted to fake friends. */
    private fun randomKeys(tools: ContactsDebugEntryPoint): ContactKeys {
        val sign = tools.sodium().randomBytes(KEY_BYTES)
        return ContactKeys(sign, tools.sodium().randomBytes(KEY_BYTES), tools.fingerprints().of(sign))
    }

    @Synchronized
    private fun testFriendSeed(tools: ContactsDebugEntryPoint): ByteArray =
        testFriendSeed ?: tools.sodium().randomBytes(KEY_BYTES).also { testFriendSeed = it }

    /** The test friend's seed, for the simulated nearby friend: the same friend as this run's QR code. */
    fun testFriendSeed(context: Context): ByteArray =
        testFriendSeed(EntryPointAccessors.fromApplication(context, ContactsDebugEntryPoint::class.java)).copyOf()

    /** A valid pairing code from the test friend; it answers this phone's latest code when [answerMine]. */
    internal fun testFriendCode(tools: ContactsDebugEntryPoint, answerMine: Boolean): TestCode {
        val answers = if (answerMine) tools.pairing().latestAnswerId() else null
        val text = tools.derivation().derive(testFriendSeed(tools)).use { keys ->
            tools.pairingCodes().create(keys, TEST_FRIEND_NAME, tools.clock().currentTimeMillis(), answers).text
        }
        return TestCode(QrEncoder.encode(text), answering = answers != null)
    }

    /**
     * The safety-number code the test friend's phone would show for this phone, or, with
     * [wrongKey], the one a phone holding a different key would show (a mismatch).
     */
    internal suspend fun testFriendSafetyCode(tools: ContactsDebugEntryPoint, wrongKey: Boolean): TestCode? {
        val me = tools.identities().get() ?: return null
        val theirKey = if (wrongKey) {
            tools.sodium().randomBytes(KEY_BYTES)
        } else {
            tools.derivation().derive(testFriendSeed(tools)).use { it.signPublicKey.copyOf() }
        }
        val numbers = tools.safetyNumbers()
        return TestCode(QrEncoder.encode(numbers.comparisonCode(numbers.between(theirKey, me.signPublicKey))), answering = false)
    }

    internal class TestCode(val matrix: QrMatrix, val answering: Boolean)

    private const val TEST_FRIEND_NAME = "Test friend"
    private const val KEY_BYTES = 32
    private const val DAY = 86_400_000L
}

/**
 * Codes from a made-up friend, so one phone and a laptop can run pairing and the safety-number
 * comparison: take a screenshot of this screen (Developer → Allow screenshots), open it on the
 * laptop, and scan it with this phone.
 */
@Composable
private fun TestFriendQrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tools = remember(context) { EntryPointAccessors.fromApplication(context, ContactsDebugEntryPoint::class.java) }
    var showSafetyCode by remember { mutableStateOf(false) }
    var answerMine by remember { mutableStateOf(true) }
    var wrongKey by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<ContactsDebugTools.TestCode?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(showSafetyCode, answerMine, wrongKey) {
        if (showSafetyCode) {
            code = ContactsDebugTools.testFriendSafetyCode(tools, wrongKey)
            return@LaunchedEffect
        }
        while (true) {
            code = ContactsDebugTools.testFriendCode(tools, answerMine)
            for (left in (PairingCodes.REFRESH_MILLIS / 1_000).toInt() downTo 1) {
                secondsLeft = left
                delay(1_000)
            }
        }
    }
    val colors = TflTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.contacts_debug_test_qr_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Callout(stringResource(R.string.contacts_debug_test_qr_body), tone = CalloutTone.Warning)
            SegmentedTabs(
                tabs = listOf(
                    SegmentedTab(stringResource(R.string.contacts_debug_kind_pairing)),
                    SegmentedTab(stringResource(R.string.contacts_debug_kind_safety)),
                ),
                selectedIndex = if (showSafetyCode) 1 else 0,
                onSelect = { showSafetyCode = it == 1 },
            )
            TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                if (showSafetyCode) {
                    ToggleRow(
                        title = stringResource(R.string.contacts_debug_wrong_key_title),
                        subtitle = stringResource(R.string.contacts_debug_wrong_key_subtitle),
                        checked = wrongKey,
                        onCheckedChange = { wrongKey = it },
                    )
                } else {
                    ToggleRow(
                        title = stringResource(R.string.contacts_debug_answer_title),
                        subtitle = stringResource(R.string.contacts_debug_answer_subtitle),
                        checked = answerMine,
                        onCheckedChange = { answerMine = it },
                    )
                }
            }
            if (showSafetyCode) {
                Callout(stringResource(R.string.contacts_debug_safety_hint))
            } else if (answerMine && code?.answering == false) {
                Callout(stringResource(R.string.contacts_debug_answer_none), tone = CalloutTone.Warning)
            }
            code?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .cornerMarks(colors.primary)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCode(it.matrix, contentDescription = stringResource(R.string.contacts_debug_test_qr_title), modifier = Modifier.fillMaxSize())
                }
            }
            if (!showSafetyCode) {
                Text(
                    stringResource(R.string.contacts_debug_new_code_in, secondsLeft),
                    style = TflTheme.typography.labelMd,
                    color = colors.textMuted,
                )
            }
            Text(
                "adb exec-out screencap -p > friend.png",
                style = TflTheme.typography.codeSm,
                color = colors.primary,
            )
        }
    }
}
