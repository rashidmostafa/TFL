// Room over SQLCipher. The key comes from :core:session after unlock; this module never sees a PIN.
plugins {
    id("tfl.android.library")
    id("tfl.hilt")
}

androidComponents {
    onVariants { variant ->
        // Room's MigrationTestHelper reads the exported schemas as test assets.
        variant.hostTests.values.forEach { test -> test.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
