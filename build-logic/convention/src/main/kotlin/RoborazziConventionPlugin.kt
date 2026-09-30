import app.tfl.buildlogic.libs
import app.tfl.buildlogic.library
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * JVM Compose UI tests (Robolectric) and screenshot tests (Roborazzi).
 *
 * Golden images live in `src/test/screenshots` and are committed. Record them with
 * `./gradlew recordRoborazziDebug`; `./gradlew verifyRoborazziDebug` fails on any visual change.
 */
class RoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }

            dependencies {
                "testImplementation"(libs.library("robolectric"))
                "testImplementation"(libs.library("roborazzi"))
                "testImplementation"(libs.library("roborazzi-compose"))
                "testImplementation"(libs.library("roborazzi-junit-rule"))
                "testImplementation"(libs.library("androidx-compose-ui-test-junit4"))
                "testImplementation"(libs.library("androidx-test-ext-junit"))
                // Registers the empty ComponentActivity that createComposeRule() launches. Debug-only,
                // which is fine: AGP 9 runs unit tests against the debug build type.
                "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
