// Shared test utilities. Other modules depend on this only through testImplementation.
plugins {
    id("tfl.android.library")
    id("tfl.android.compose")
}

dependencies {
    implementation(projects.core.designsystem)

    // The Compose test artifacts below take their versions from the BOM; export it so modules
    // without Compose (like :core:crypto) can still resolve them.
    api(platform(libs.androidx.compose.bom))
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.test.ext.junit)

    // Crypto fakes and libsodium on the JVM (lazysodium-java bundles the same libsodium 1.0.20,
    // and JNA's desktop jar carries the native dispatch library for this computer).
    api(projects.core.crypto)
    api(projects.core.database)
    api(projects.core.session)
    implementation(libs.androidx.room.runtime)
    api(libs.lazysodium.java)
    api(libs.jna)
}
