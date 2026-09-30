// All cryptography lives in this module: libsodium (via Lazysodium) and the Android Keystore.
plugins {
    id("tfl.android.library")
    id("tfl.hilt")
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    // Lazysodium's POM asks for JNA's desktop jar, which can't load on Android. Swap in the @aar build,
    // which carries JNA's native dispatch library for each Android ABI.
    implementation(libs.lazysodium.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.jna) {
        artifact { type = "aar" }
    }

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
