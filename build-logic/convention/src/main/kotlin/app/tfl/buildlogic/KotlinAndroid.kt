package app.tfl.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal object TflSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

/** Settings shared by the app and every Android library module. */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.compileSdk = TflSdk.COMPILE
    android.defaultConfig.minSdk = TflSdk.MIN
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    // Robolectric tests read merged resources and the manifest.
    android.testOptions.unitTests.isIncludeAndroidResources = true
    android.lint.abortOnError = true
    android.lint.checkReleaseBuilds = true
    android.packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
    configureKotlin()
}

/**
 * AGP 9 creates unit tests for the debug build type only. Modules with release-specific tests
 * (`src/testRelease`, e.g. "logging is a no-op in release") get release unit tests too.
 */
internal fun <B : VariantBuilder> AndroidComponentsExtension<*, B, *>.enableReleaseUnitTestsIfPresent(project: Project) {
    if (!project.file("src/testRelease").isDirectory) return
    beforeVariants(selector().withBuildType("release")) { variant ->
        val unitTests = checkNotNull((variant as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE])
        unitTests.enable = true
    }
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
