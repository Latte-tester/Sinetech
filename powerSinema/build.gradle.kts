version = 2.1

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "TMDB_API_KEY", System.getenv("TMDB_SECRET_API") ?: "\"\"")
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
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
