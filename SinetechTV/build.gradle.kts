// use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "ItalianProvider reposu içindeki TV eklentisi"
    authors = listOf("Gian-Fr","Adippe","doGior","GitLatte")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Live")

    requiresResources = true

    iconUrl = "https://raw.githubusercontent.com/GitLatte/m3ueditor/refs/heads/site/images/kanal-gorselleri/referans/isimsizkanal.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
}
