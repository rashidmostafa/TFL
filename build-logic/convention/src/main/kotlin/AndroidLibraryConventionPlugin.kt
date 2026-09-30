import app.tfl.buildlogic.TflSdk
import app.tfl.buildlogic.configureKotlinAndroid
import app.tfl.buildlogic.disableDeviceTestsIfAbsent
import app.tfl.buildlogic.enableReleaseUnitTestsIfPresent
import app.tfl.buildlogic.libs
import app.tfl.buildlogic.library
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // ":feature:chats" -> "app.tfl.feature.chats"
                namespace = "app.tfl" + path.replace(':', '.')
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.targetSdk = TflSdk.TARGET
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                enableReleaseUnitTestsIfPresent(target)
                disableDeviceTestsIfAbsent(target)
            }

            dependencies {
                "testImplementation"(libs.library("junit4"))
            }
        }
    }
}
