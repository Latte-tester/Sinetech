version = 1

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "Çoklu IPTV listelerini destekleyen kanal eklentisi"
    status      = 1
    tvTypes     = listOf("Live")
    iconUrl     = "https://raw.githubusercontent.com/GitLatte/Sinetech/refs/heads/main/img/iptvlistem/favicon.ico"
}