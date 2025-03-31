version = 3

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

android {
    defaultConfig {
        buildConfigField("String", "TMDB_SECRET_API", System.getenv("TMDB_API_KEY") ?: "\"\"")
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "powerboard`un sinema arşivi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=tr.canlitv.team&sz=%size%"
}
