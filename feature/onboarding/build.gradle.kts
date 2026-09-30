plugins {
    id("tfl.android.feature")
}

dependencies {
    implementation(projects.core.session)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
}
