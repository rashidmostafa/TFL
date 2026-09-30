// App session: lock/unlock, the onboarding commit, auto-lock, wipe and the calculator disguise.
// Sits above :core:crypto and :core:database so neither has to depend on the other.
plugins {
    id("tfl.android.library")
    id("tfl.hilt")
}

dependencies {
    api(projects.core.crypto)
    api(projects.core.database)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.biometric)
    api(libs.androidx.fragment)

    testImplementation(projects.core.testing)
}
