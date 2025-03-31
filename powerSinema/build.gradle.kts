version = 3

android {
    buildFeatures {
        buildConfig = true
    }
}

buildConfig {
    buildConfigField("String", "TMDB_SECRET_API", "\"3b0b95d3f6c1c2f3c8f3c3f3c8f3c3f3\"")
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
