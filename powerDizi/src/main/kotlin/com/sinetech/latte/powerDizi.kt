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
        processedItems.groupBy { it.attributes["group-title"] ?: "Uncategorized" }.forEach { (title, shows) ->
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
                watchedShows.add(HomePageList("${title ?: "Diğer"} - Devam Et", watchedShowsList, isHorizontalImages = true))
            }
            regularShows.add(HomePageList("${title?.toString() ?: "Diğer"} adlı diziye ait bölümler", unwatchedShowsList, isHorizontalImages = true))
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
        
        // Notify the app to refresh the main page
        HomeViewModel.updateHomePageEvent.invoke()
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

    /** Replace "" (quotes) from given string. */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /** Check if given content is valid M3U8 playlist. */
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     *
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     *
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get url parameter with key.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     *
     * If given key is `user-agent`, then
     *
     * Result: Mozilla
     */
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex     = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex     = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     *
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     * )
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex      = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        
        val attributes = attributesString
            .split(Regex("\\s"))
            .mapNotNull {
                val pair = it.split("=")
                if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
            }
            .toMap()
            .toMutableMap()

        // Set default values for missing attributes
        if (!attributes.containsKey("tvg-country")) {
            attributes["tvg-country"] = "TR"
        }
        if (!attributes.containsKey("tvg-language")) {
            attributes["tvg-language"] = "TR;EN"
        }

        return attributes
    }

    /**
     * Get value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     *
     * Result: http://example.com/
     */
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

/** Exception thrown when an error occurs while parsing playlist. */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /** Exception thrown if given file content is not valid. */
    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}
