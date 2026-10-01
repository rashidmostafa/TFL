package app.tfl

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.transport.service.TransportForegroundService
import app.tfl.feature.onboarding.stealth.CalculatorActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Security rules from CLAUDE.md that the merged manifest and the windows must satisfy. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class ManifestHardeningTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    @Test
    fun backupIsDisabled() {
        val info = packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    private fun requestedPermissions(): Set<String> = packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        .orEmpty()
        .toSet()

    @Test
    fun requestsOnlyTheExpectedPermissions() {
        // Any new permission must be added here deliberately. As Android 16 sees the manifest:
        val requested = requestedPermissions()
        assertEquals(
            setOf(
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                // androidx.biometric and WorkManager: normal permissions, granted at install. Network
                // state only reads connectivity; boot completed only reschedules WorkManager's jobs.
                "android.permission.USE_BIOMETRIC",
                "android.permission.USE_FINGERPRINT",
                "android.permission.WAKE_LOCK",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.ACCESS_NETWORK_STATE",
                // Asked for only when Scan opens, to read friends' QR codes.
                "android.permission.CAMERA",
                // Nearby Connections, asked for only when Nearby is turned on.
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.NEARBY_WIFI_DEVICES",
                "android.permission.ACCESS_LOCAL_NETWORK",
                // The background service and message notifications.
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                "android.permission.POST_NOTIFICATIONS",
            ),
            requested,
        )
        assertFalse("android.permission.INTERNET" in requested)
    }

    @Test
    @Config(sdk = [30])
    fun olderPhonesUseLocationAndLegacyBluetooth_stillNoInternet() {
        val requested = requestedPermissions()
        val nearbyBeforeAndroid12 = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
        )
        assertTrue(requested.containsAll(nearbyBeforeAndroid12))
        assertFalse("android.permission.INTERNET" in requested)
    }

    @Test
    fun unusedLibraryComponentsAreRemoved() {
        val info = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        val names = info.services.orEmpty().map { it.name } + info.receivers.orEmpty().map { it.name }
        assertFalse("com.google.android.gms.nearby.exposurenotification.WakeUpService" in names)
        assertFalse("androidx.work.impl.diagnostics.DiagnosticsReceiver" in names)
    }

    @Test
    fun theTransportServiceIsPrivate_andOtherAppsCantStartAnyServiceFreely() {
        val services = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        val transport = services.single { it.name == TransportForegroundService::class.java.name }
        assertFalse(transport.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, transport.foregroundServiceType)
        // Library services other apps could reach (WorkManager's job service) need a system permission.
        services.filter { it.exported }.forEach { assertTrue(it.name, it.permission != null) }
    }

    @Test
    fun theCameraIsOptionalHardware() {
        val features = packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures
            .orEmpty()
            .filter { it.name?.startsWith("android.hardware.camera") == true }
        assertTrue(features.isNotEmpty())
        assertTrue(features.none { it.flags and FeatureInfo.FLAG_REQUIRED != 0 })
    }

    @Test
    fun launcherEntriesAreTheAliases_withTheCalculatorOffByDefault() {
        val main = packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertFalse(main.exported)
        val calculator = packageManager.getActivityInfo(ComponentName(context, CalculatorActivity::class.java), 0)
        assertFalse(calculator.exported)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            packageManager.getComponentEnabledSetting(ComponentName(context.packageName, CalculatorDisguise.CALCULATOR_ALIAS)),
        )
        val launch = packageManager.getLaunchIntentForPackage(context.packageName)
        assertEquals(CalculatorDisguise.DEFAULT_ALIAS, launch?.component?.className)
    }

    @Test
    fun tflWindowBlocksScreenshots() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun calculatorWindowLooksLikeAnyCalculator() {
        // The disguise shows in recent apps like a normal calculator, so it's the one window without FLAG_SECURE.
        ActivityScenario.launch(CalculatorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                assertEquals("Calculator", activity.title)
            }
        }
    }
}
