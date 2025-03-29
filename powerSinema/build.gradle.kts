version = 3

val tmdbApiKey = System.getenv("TMDB_SECRET_API") ?: ""

android {
    buildFeatures {
        buildConfig = true
    }
    
    defaultConfig {
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey}\"")
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "powerboard`un sinema arşivi"

    dependencies {
        implementation("com.google.code.gson:gson:2.8.9")
    }

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
