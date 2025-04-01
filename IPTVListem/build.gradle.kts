version = 1

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
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