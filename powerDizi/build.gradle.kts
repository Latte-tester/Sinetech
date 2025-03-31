version = 3

cloudstream {
    authors     = listOf("GitLatte", "patr0nq", "keyiflerolsun")
    language    = "tr"
    description = "powerboard`un yabancı dizi arşivi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/refs/heads/main/img/powerdizi/favicon.ico"
}
