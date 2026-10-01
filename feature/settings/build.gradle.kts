plugins {
    id("tfl.android.feature")
}

dependencies {
    implementation(projects.core.session)
    implementation(projects.core.transport)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
}
