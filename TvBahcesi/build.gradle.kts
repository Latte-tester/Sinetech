version = 1

dependencies {
    implementation("com.lagradost:cloudstream3:pre-release")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}

android.libraryVariants.all {
    outputs.all {
        val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        if (outputImpl.outputFileName.endsWith(".aar"))
            outputImpl.outputFileName = "${project.name}.cs3"
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "TvGarden televizyon listesi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/refs/heads/main/img/powersinema/favicon.ico"
}