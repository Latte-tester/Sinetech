package com.sinetech.latte

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities


class TvBahcesi : MainAPI() {
    override var name = "TV Bahçesi"
    override var mainUrl = "https://raw.githubusercontent.com/Latte-tester/Sinetech/refs/heads/main/TvBahcesi/src/main/resources/m3u/tvbahcesi.m3u"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    private val defaultPosterUrl = "https://raw.githubusercontent.com/GitLatte/m3ueditor/refs/heads/site/images/kanal-gorselleri/referans/isimsizkanal.png"

    private suspend fun getCountryName(countryCode: String): String {
        val countriesJson = app::class.java.getResource("/countries.json")?.readText()
            ?: return countryCode.uppercase()
        val countryNames = AppUtils.tryParseJson<Map<String, String>>(countriesJson)
            ?: return countryCode.uppercase()
        return countryNames.getOrDefault(countryCode.lowercase(), countryCode.uppercase())
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val m3uText = app.get(mainUrl).text
        val playlist = IptvPlaylistParser().parseM3U(m3uText)
        
        val groupedChannels = playlist.items.groupBy { it.attributes["tvg-country"]?.toString() ?: "other" }
        val sortedGroups = groupedChannels.entries.sortedWith(compareBy { 
            when(it.key) {
                "tr" -> "0" // Türkiye kanalları en üstte
                else -> it.key
            }
        })

        return newHomePageResponse(
            sortedGroups.map { (countryCode, channels) ->
                HomePageList(
                    name = getCountryName(countryCode),
                    list = channels.map { channel ->
                        newLiveSearchResponse(
                            name = "<b>${channel.title ?: ""}</b>",
                            url = channel.url ?: "",
                            type = TvType.Live
                        ) {
                            this.posterUrl = channel.attributes["tvg-logo"]?.toString() ?: defaultPosterUrl
                        }
                    },
                    isHorizontalImages = true
                )
            },
            hasNext = false
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = data,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = emptyMap()
            )
        )
        return true
    }
}