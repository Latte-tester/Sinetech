version = 1

cloudstream {
    authors     = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "Explore the latest TV channels and schedules."
    status      = 1
    tvTypes     = listOf("Live")
    iconUrl     = "https://www.google.com/s2/favicons?domain=tvpass.org/&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
    }
}