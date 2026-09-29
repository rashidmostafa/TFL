import app.tfl.buildlogic.libs
import app.tfl.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Compose UI, Hilt ViewModels, type-safe navigation routes, and screenshot tests. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tfl.android.library")
            pluginManager.apply("tfl.android.compose")
            pluginManager.apply("tfl.hilt")
            pluginManager.apply("tfl.roborazzi")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:common"))

                "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-navigation-compose"))
                "implementation"(libs.library("kotlinx-serialization-core"))

                "testImplementation"(project(":core:testing"))
            }
        }
    }
}
