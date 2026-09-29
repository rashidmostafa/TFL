// Shared test utilities. Other modules depend on this only through testImplementation.
plugins {
    id("tfl.android.library")
    id("tfl.android.compose")
}

dependencies {
    implementation(projects.core.designsystem)

    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.test.ext.junit)
}
