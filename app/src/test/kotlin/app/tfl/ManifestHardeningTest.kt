package app.tfl

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.session.CalculatorDisguise
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

    @Test
    fun requestsOnlyTheExpectedPermissions() {
        // Any new permission (INTERNET, Bluetooth, location…) must be added here deliberately.
        val requested = packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()
        assertEquals(
            setOf(
                "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                // From androidx.biometric: normal permissions, granted at install, no network access.
                "android.permission.USE_BIOMETRIC",
                "android.permission.USE_FINGERPRINT",
                // Asked for only when Scan opens, to read friends' QR codes.
                "android.permission.CAMERA",
            ),
            requested,
        )
        assertFalse("android.permission.INTERNET" in requested)
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
