plugins {
    id("tfl.android.library")
    id("tfl.android.compose")
    id("tfl.roborazzi")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)

    testImplementation(projects.core.testing)
}
