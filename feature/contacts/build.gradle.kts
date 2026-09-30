// Friends: pairing in person by QR code, safety numbers, key changes and each friend's profile.
plugins {
    id("tfl.android.feature")
}

dependencies {
    implementation(projects.core.session)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.zxing.core)
}
