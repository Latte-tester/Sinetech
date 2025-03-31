package com.sinetech.latte

import android.util.Log
import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class powerSinema(private val context: android.content.Context, private val sharedPref: SharedPreferences?) : MainAPI() {

    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-sinema.m3u"
    override var name                 = "powerSinema"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return newHomePageResponse(
            kanallar.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show  = group.value.map { kanal ->
                    val streamurl   = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl   = kanal.attributes["tvg-logo"].toString()
                    val chGroup     = kanal.attributes["group-title"].toString()
                    val nation      = kanal.attributes["tvg-country"].toString()

                    val watchKey = "watch_${streamurl.hashCode()}"
                    val progressKey = "progress_${streamurl.hashCode()}"
                    val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
                    val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress).toJson(),
                        type = TvType.Movie
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }


                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()

            val watchKey = "watch_${streamurl.hashCode()}"
            val progressKey = "progress_${streamurl.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress).toJson(),
                type = TvType.Movie
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext null
                }

                val encodedTitle = URLEncoder.encode(title.replace(Regex("\\([^)]*\\)"), "").trim(), "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$encodedTitle&language=tr-TR"
                
                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")
                
                if (results.length() > 0) {
                    val movieId = results.getJSONObject(0).getInt("id")
                    val detailsUrl = "https://api.themoviedb.org/3/movie/$movieId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                    val detailsResponse = withContext(Dispatchers.IO) {
                        URL(detailsUrl).readText()
                    }
                    return@withContext JSONObject(detailsResponse)
                }
                null
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                null
            }
        }
    }

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

        val tmdbData = fetchTMDBData(loadData.title)
        
        val plot = buildString {
            if (tmdbData != null) {
                val overview = tmdbData.optString("overview", "")
                val releaseDate = tmdbData.optString("release_date", "").split("-").firstOrNull() ?: ""
                val rating = tmdbData.optDouble("vote_average", 0.0)
                val genres = tmdbData.getJSONArray("genres")
                val genreList = mutableListOf<String>()
                for (i in 0 until genres.length()) {
                    genreList.add(genres.getJSONObject(i).getString("name"))
                }
                
                val credits = tmdbData.getJSONObject("credits")
                val cast = credits.getJSONArray("cast")
                val castList = mutableListOf<String>()
                for (i in 0 until minOf(cast.length(), 5)) {
                    castList.add(cast.getJSONObject(i).getString("name"))
                }
                
                val crew = credits.getJSONArray("crew")
                var director = ""
                for (i in 0 until crew.length()) {
                    val member = crew.getJSONObject(i)
                    if (member.getString("job") == "Director") {
                        director = member.getString("name")
                        break
                    }
                }
                
                if (overview.isNotEmpty()) append("📝 Konu:\n${overview}\n\n")
                if (releaseDate.isNotEmpty()) append("📅 Yıl: $releaseDate\n\n")
                if (rating > 0) append("⭐ TMDB Puanı: $rating\n\n")
                if (director.isNotEmpty()) append("🎬 Yönetmen: $director\n\n")
                if (castList.isNotEmpty()) append("👥 Oyuncular: ${castList.joinToString(", ")}\n\n")
                if (genreList.isNotEmpty()) append("🎭 Türler: ${genreList.joinToString(", ")}\n\n")
                append("\n")
            }
            append("\nFilm Grubu: ${loadData.group}\n")
            append("Ülke: ${loadData.nation}")
        }

        val kanallar        = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val recommendations = mutableListOf<LiveSearchResponse>()

        for (kanal in kanallar.items) {
            if (kanal.attributes["group-title"].toString() == loadData.group) {
                val rcStreamUrl   = kanal.url.toString()
                val rcChannelName = kanal.title.toString()
                if (rcChannelName == loadData.title) continue

                val rcPosterUrl   = kanal.attributes["tvg-logo"].toString()
                val rcChGroup     = kanal.attributes["group-title"].toString()
                val rcNation      = kanal.attributes["tvg-country"].toString()

                val rcWatchKey = "watch_${rcStreamUrl.hashCode()}"
                val rcProgressKey = "progress_${rcStreamUrl.hashCode()}"
                val rcIsWatched = sharedPref?.getBoolean(rcWatchKey, false) ?: false
                val rcWatchProgress = sharedPref?.getLong(rcProgressKey, 0L) ?: 0L

                recommendations.add(newLiveSearchResponse(
                    rcChannelName,
                    LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcNation, rcIsWatched, rcWatchProgress).toJson(),
                    type = TvType.Movie
                ) {
                    this.posterUrl = rcPosterUrl
                    this.lang = rcNation
                })
            }
        }

        return newMovieLoadResponse(loadData.title, url, TvType.Movie, loadData.url) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOf(loadData.group, loadData.nation)
            this.recommendations = recommendations
            this.rating = (tmdbData?.optDouble("vote_average", 0.0)?.toFloat()?.times(2)?.toInt() ?: (if (isWatched) 5 else 0))
            this.duration = if (watchProgress > 0) (watchProgress / 1000).toInt() else tmdbData?.optInt("runtime", 0)
            this.comingSoon = false
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val loadData = fetchDataFromUrlOrJson(data)
            Log.d("IPTV", "loadData » $loadData")

            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false
            Log.d("IPTV", "kanal » $kanal")

            val watchKey = "watch_${data.hashCode()}"
            val progressKey = "progress_${data.hashCode()}"
            sharedPref?.edit()?.putBoolean(watchKey, true)?.apply()

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = loadData.title,
                    url = loadData.url,
                    headers = kanal.headers + mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    ),
                    referer = kanal.headers["referrer"] ?: "",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )

            return true
        } catch (e: Exception) {
            Log.e("IPTV", "Error in loadLinks: ${e.message}", e)
            return false
        }
    }

    data class LoadData(
    val url: String,
    val title: String,
    val poster: String,
    val group: String,
    val nation: String,
    val isWatched: Boolean = false,
    val watchProgress: Long = 0L
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
            val watchKey = "watch_${data.hashCode()}"
            val progressKey = "progress_${data.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            return LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress)
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
    val userAgent: String?              = null
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
        val commaIndex = lastIndexOf(",")
        return if (commaIndex >= 0) {
            substring(commaIndex + 1).trim().let { title ->
                val unquotedTitle = if (title.startsWith("\"") && title.endsWith("\"")) {
                    title.substring(1, title.length - 1)
                } else {
                    title
                }
                // Özel karakterleri ve Unicode karakterlerini koru
                unquotedTitle.trim().takeIf { it.isNotEmpty() }?.let { rawTitle ->
                    // HTML entity'lerini decode et
                    rawTitle.replace("&amp;", "&")
                           .replace("&lt;", "<")
                           .replace("&gt;", ">")
                           .replace("&quot;", "\"") 
                           .replace("&#39;", "'")
                } ?: unquotedTitle
            }
        } else {
            null
        }
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
        val attributesString = replace(extInfRegex, "").trim()
        
        val attributes = mutableMapOf<String, String>()
        var currentKey = ""
        var currentValue = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < attributesString.length) {
            val char = attributesString[i]
            when {
                char == '"' -> inQuotes = !inQuotes
                char == '=' && !inQuotes -> {
                    currentKey = currentValue.toString().trim()
                    currentValue.clear()
                }
                char == ' ' && !inQuotes && currentKey.isNotEmpty() && currentValue.isNotEmpty() -> {
                    val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
                    if (cleanValue.isNotEmpty()) {
                        attributes[currentKey] = cleanValue
                    }
                    currentKey = ""
                    currentValue.clear()
                }
                char == ',' && !inQuotes -> {
                    if (currentKey.isNotEmpty() && currentValue.isNotEmpty()) {
                        val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
                        if (cleanValue.isNotEmpty()) {
                            attributes[currentKey] = cleanValue
                        }
                    }
                    break
                }
                else -> currentValue.append(char)
            }
            i++
        }

        if (currentKey.isNotEmpty() && currentValue.isNotEmpty()) {
            val cleanValue = currentValue.toString().trim().removeSurrounding("\"").trim()
            if (cleanValue.isNotEmpty()) {
                attributes[currentKey] = cleanValue
            }
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
