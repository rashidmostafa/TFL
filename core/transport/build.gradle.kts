// Moving sealed envelopes between phones: Nearby Connections now, Tor and the mesh later. It holds
// no cryptography of its own (that's :core:crypto); it links, queues, retries and delivers.
plugins {
    id("tfl.android.library")
    id("tfl.hilt")
}

dependencies {
    api(projects.core.session)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.nearby)
    api(libs.androidx.work.runtime.ktx)

    testImplementation(projects.core.testing)
}
