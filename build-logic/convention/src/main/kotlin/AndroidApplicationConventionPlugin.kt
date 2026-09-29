import app.tfl.buildlogic.TflSdk
import app.tfl.buildlogic.configureKotlinAndroid
import app.tfl.buildlogic.enableReleaseUnitTestsIfPresent
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TflSdk.TARGET
                testOptions.animationsDisabled = true
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                enableReleaseUnitTestsIfPresent(target)
            }
        }
    }
}
