package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/refs/heads/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        // Parse episode information from titles
        val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")
        val processedItems = kanallar.items.map { item ->
            val title = item.title.toString()
            val match = episodeRegex.find(title)
            if (match != null) {
                val (showName, season, episode) = match.destructured
                item.copy(
                    season = season.toInt(),
                    episode = episode.toInt(),
                    attributes = item.attributes.toMutableMap().apply {
                        if (!containsKey("tvg-country")) { put("tvg-country", "TR/Altyazılı") }
                        if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                    }
                )
            } else {
                item.copy(
                    attributes = item.attributes.toMutableMap().apply {
                        if (!containsKey("tvg-country")) { put("tvg-country", "TR") }
                        if (!containsKey("tvg-language")) { put("tvg-language", "TR;EN") }
                    }
                )
            }
        }

        // Create a list for watched shows
        val watchedShows = mutableListOf<HomePageList>()
        val regularShows = mutableListOf<HomePageList>()

        // Group shows by watched status
        processedItems.groupBy { it.attributes["group-title"]?.toString()?.trim() ?: "Uncategorized" }.forEach { (title, shows) ->
            val watchedShowsList = mutableListOf<SearchResponse>()
            val unwatchedShowsList = mutableListOf<SearchResponse>()

            shows.forEach { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val chGroup = kanal.attributes["group-title"].toString()
                val nation = kanal.attributes["tvg-country"].toString()
                val watchKey = "watch_${streamurl.hashCode()}"
                val progressKey = "progress_${streamurl.hashCode()}"
                val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
                val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

                val searchResponse = newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, chGroup, nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                    if (isWatched) {
                        this.quality = SearchQuality.HD
                        this.posterHeaders = mapOf("watched" to "true")
                    }
                }

                if (isWatched && watchProgress > 0) {
                    watchedShowsList.add(searchResponse)
                } else {
                    unwatchedShowsList.add(searchResponse)
                }
            }

            if (watchedShowsList.isNotEmpty()) {
                watchedShows.add(HomePageList("${title?.toString()?.trim() ?: "Diğer"} - İzlemeye Devam Et", watchedShowsList, isHorizontalImages = true))
            }
            regularShows.add(HomePageList("${title?.toString()?.trim() ?: "Diğer"} adlı diziye ait bölümler", unwatchedShowsList, isHorizontalImages = true))
        }

        return newHomePageResponse(
            watchedShows + regularShows,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, 
                    episodeRegex.find(channelname)?.let { match ->
                        val (_, season, episode) = match.destructured
                        season.toInt() to episode.toInt()
                    }?.first ?: 1,
                    episodeRegex.find(channelname)?.let { match ->
                        val (_, season, episode) = match.destructured
                        season.toInt() to episode.toInt()
                    }?.second ?: 0
                ).toJson(),
                type = TvType.TvSeries
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)
        val nation:String = if (loadData.group == "NSFW") {
            "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
        } else {
            "» ${loadData.group} | ${loadData.nation} «"
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")
        val groupEpisodes = kanallar.items
            .filter { it.attributes["group-title"]?.toString() ?: "" == loadData.group }
            .mapNotNull { kanal ->
                val title = kanal.title.toString()
                val match = episodeRegex.find(title)
                if (match != null) {
                    val (_, season, episode) = match.destructured
                    Episode(
                        episode = episode.toInt(),
                        season = season.toInt(),
                        data = LoadData(
                            kanal.url.toString(),
                            title,
                            kanal.attributes["tvg-logo"].toString(),
                            kanal.attributes["group-title"].toString(),
                            kanal.attributes["tvg-country"]?.toString() ?: "TR",
                            season.toInt(),
                            episode.toInt()
                        ).toJson()
                    )
                } else null
            }

        return newTvSeriesLoadResponse(
            loadData.title,
            url,
            TvType.TvSeries,
            groupEpisodes.map { episode ->
                val epWatchKey = "watch_${episode.data.hashCode()}"
                val epProgressKey = "progress_${episode.data.hashCode()}"
                val epIsWatched = sharedPref?.getBoolean(epWatchKey, false) ?: false
                val epWatchProgress = sharedPref?.getLong(epProgressKey, 0L) ?: 0L
                episode.apply {
                    this.rating = if (epIsWatched) 5 else 0
                    this.description = if (epWatchProgress > 0) {
                        val seconds = epWatchProgress / 1000
                        val hours = seconds / 3600
                        val minutes = (seconds % 3600) / 60
                        val remainingSeconds = seconds % 60
                        "İzleme süresi: ${if (hours > 0) "${hours} saat " else ""}${if (minutes > 0) "${minutes} dakika " else ""}${if (remainingSeconds > 0 || (hours == 0L && minutes == 0L)) "${remainingSeconds} saniye" else ""}".trim()
                    } else null
                }
            }
        ) {
            this.posterUrl = loadData.poster
            this.plot = nation
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        val watchKey = "watch_${loadData.url.hashCode()}"
        val progressKey = "progress_${loadData.url.hashCode()}"
        
        sharedPref?.edit()?.apply {
            putBoolean(watchKey, true)
            putLong(progressKey, System.currentTimeMillis())
            apply()
        }
        
        // SharedPreferences changes will trigger a refresh automatically
        Log.d("IPTV", "loadData » $loadData")

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal    = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false
        Log.d("IPTV", "kanal » $kanal")

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = "${loadData.title} (S${loadData.season}:E${loadData.episode})",
                url     = loadData.url,
                headers = kanal.headers,
                referer = kanal.headers["referrer"] ?: "",
                quality = Qualities.Unknown.value,
                isM3u8  = true
            )
        )

        return true
    }

    data class LoadData(
    val url: String,
    val title: String,
    val poster: String,
    val group: String,
    val nation: String,
    val season: Int = 1,
    val episode: Int = 0,
    val isWatched: Boolean = false,
    val watchProgress: Long = 0
)

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal    = kanallar.items.first { it.url == data }

            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()

            return LoadData(streamurl, channelname, posterurl, chGroup, nation)
        }
    }
}

data class Playlist(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val title: String?                  = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String>    = emptyMap(),
    val url: String?                    = null,
    val userAgent: String?              = null,
    val season: Int                     = 1,
    val episode: Int                    = 0
)

class IptvPlaylistParser {

    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title      = line.getTitle()
                    val attributes = line.getAttributes()

                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item      = playlistItems[currentIndex]
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                    val referrer  = line.getTagValue("http-referrer")

                    val headers = mutableMapOf<String, String>()

                    if (userAgent != null) {
                        headers["user-agent"] = userAgent
                    }

                    if (referrer != null) {
                        headers["referrer"] = referrer
                    }

                    playlistItems[currentIndex] = item.copy(
                        userAgent = userAgent,
                        headers   = headers
                    )
                } else {
                    if (!line.startsWith("#")) {
                        val item       = playlistItems[currentIndex]
                        val url        = line.getUrl()
                        val userAgent  = line.getUrlParameter("user-agent")
                        val referrer   = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {item.headers + mapOf("referrer" to referrer)} else item.headers

                        playlistItems[currentIndex] = item.copy(
                            url       = url,
                            headers   = item.headers + urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                        currentIndex++
                    }
                }
            }

            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex     = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex     = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        
        val attributes = mutableMapOf<String, String>()
        val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"|([\\w-]+)=([^\"]+)")
        
        attrRegex.findAll(attributesString).forEach { matchResult ->
            val (quotedKey, quotedValue, unquotedKey, unquotedValue) = matchResult.destructured
            val key = quotedKey.takeIf { it.isNotEmpty() } ?: unquotedKey
            val value = quotedValue.takeIf { it.isNotEmpty() } ?: unquotedValue
            attributes[key] = value.replaceQuotesAndTrim()

        if (!attributes.containsKey("tvg-country")) {
            attributes["tvg-country"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("tvg-language")) {
            attributes["tvg-language"] = "TR/Altyazılı"
        }

        return attributes
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)

        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U     = "#EXTM3U"
        const val EXT_INF     = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {

    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}
