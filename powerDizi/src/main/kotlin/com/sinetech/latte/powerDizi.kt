package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
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

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi 🎬"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        // Parse episode information from titles
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")
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

        val groupedShows = processedItems.groupBy { it.attributes["group-title"]?.toString()?.trim() ?: "Diğer" }

        val homePageLists = mutableListOf<HomePageList>()

        groupedShows.forEach { (group, shows) ->
            val searchResponses = shows.map { kanal ->
                val streamurl = kanal.url.toString()
                val channelname = kanal.title.toString()
                val posterurl = kanal.attributes["tvg-logo"].toString()
                val nation = kanal.attributes["tvg-country"].toString()

                newLiveSearchResponse(
                    channelname,
                    LoadData(streamurl, channelname, posterurl, group, nation, kanal.season, kanal.episode).toJson(),
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }
            }
            
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList(group, searchResponses, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")

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

    private suspend fun fetchTMDBData(title: String, season: Int, episode: Int): Pair<JSONObject?, JSONObject?> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.TMDB_SECRET_API.trim('"')
                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "API key is empty")
                    return@withContext Pair(null, null)
                }

                // Dizi adını temizle ve hazırla
                val cleanedTitle = title
                    .replace(Regex("\\([^)]*\\)"), "") // Parantez içindeki metinleri kaldır
                    .trim()
                
                Log.d("TMDB", "Searching for TV show: $cleanedTitle")
                val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = withContext(Dispatchers.IO) {
                    URL(searchUrl).readText()
                }
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")
                
                Log.d("TMDB", "Search results count: ${results.length()}")
                
                if (results.length() > 0) {
                    // İlk sonucu al
                    val tvId = results.getJSONObject(0).getInt("id")
                    val foundTitle = results.getJSONObject(0).optString("name", "")
                    Log.d("TMDB", "Found TV show: $foundTitle with ID: $tvId")
                    
                    // Dizi detaylarını getir
                    val seriesUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits,images,videos&language=tr-TR"
                    val seriesResponse = withContext(Dispatchers.IO) {
                        URL(seriesUrl).readText()
                    }
                    val seriesData = JSONObject(seriesResponse)
                    
                    // Bölüm detaylarını getir
                    try {
                        val episodeUrl = "https://api.themoviedb.org/3/tv/$tvId/season/$season/episode/$episode?api_key=$apiKey&append_to_response=credits,images,videos&language=tr-TR"
                        val episodeResponse = withContext(Dispatchers.IO) {
                            URL(episodeUrl).readText()
                        }
                        val episodeData = JSONObject(episodeResponse)
                        
                        return@withContext Pair(seriesData, episodeData)
                    } catch (e: Exception) {
                        Log.e("TMDB", "Error fetching episode data: ${e.message}")
                        // Bölüm bilgisi alınamazsa sadece dizi bilgisini döndür
                        return@withContext Pair(seriesData, null)
                    }
                } else {
                    Log.d("TMDB", "No results found for: $cleanedTitle")
                }
                Pair(null, null)
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching TMDB data: ${e.message}")
                Pair(null, null)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L
        val loadData = fetchDataFromUrlOrJson(url)
        
        // Dizi adını temizle - hem "Dizi-1.Sezon" hem de "Dizi 1. Sezon" formatlarını destekler
        val cleanTitle = loadData.title.replace(Regex("""[-\s]*\d+\.?\s*Sezon\s*\d+\.?\s*Bölüm.*"""), "").trim()
        val (seriesData, episodeData) = fetchTMDBData(cleanTitle, loadData.season, loadData.episode)
        
        val plot = buildString {
            // Her zaman önce dizi bilgilerini göster
            if (seriesData != null) {
                append("<b>📺 DİZİ BİLGİLERİ</b><br><br>")
                
                val overview = seriesData.optString("overview", "")
                val firstAirDate = seriesData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                val ratingValue = seriesData.optDouble("vote_average", -1.0)
                val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                val tagline = seriesData.optString("tagline", "")
                val originalName = seriesData.optString("original_name", "")
                val originalLanguage = seriesData.optString("original_language", "")
                val numberOfSeasons = seriesData.optInt("number_of_seasons", 1)

                val genresArray = seriesData.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                    }
                }
                
                if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>${tagline}<br><br>")
                if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>")
                if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                if (originalLanguage.isNotEmpty()) {
                    val langCode = originalLanguage.lowercase()
                    val turkishName = languageMap[langCode] ?: originalLanguage
                    append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                }
                if (numberOfSeasons > 1) append("📅 <b>Toplam Sezon:</b> $numberOfSeasons<br>")
                if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                
                // Dizi oyuncuları fotoğraflarıyla
                val creditsObject = seriesData.optJSONObject("credits")
                if (creditsObject != null) {
                    val castArray = creditsObject.optJSONArray("cast")
                    if (castArray != null && castArray.length() > 0) {
                        append("<br>👥 <b>Oyuncular:</b><br>")
                        append("<div style='display:grid; grid-template-columns:repeat(auto-fill, minmax(120px, 1fr)); gap:16px; justify-content:center; padding:8px;'>")
                        for (i in 0 until minOf(castArray.length(), 8)) {
                            val actor = castArray.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            val profilePath = actor?.optString("profile_path", "") ?: ""
                            
                            if (actorName.isNotEmpty()) {
                                append("<div style='text-align:center;'>")
                                if (profilePath.isNotEmpty()) {
                                    val imageUrl = "https://image.tmdb.org/t/p/w300$profilePath"
                                    append("<div style='aspect-ratio:2/3; margin-bottom:8px; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1);'>")
                                    append("<img src='$imageUrl' style='width:100%; height:100%; object-fit:cover;'>")
                                    append("</div>")
                                }
                                append("<div style='padding:4px;'>")
                                append("<b style='font-size:14px; display:block; margin-bottom:4px;'>$actorName</b>")
                                if (character.isNotEmpty()) append("<span style='font-size:12px; color:#666;'>$character</span>")
                                append("</div>")
                                append("</div>")
                            }
                        }
                        append("</div><br>")
                    } else {
                        val castList = mutableListOf<String>()
                        if (castArray != null) {
                            for (i in 0 until minOf(castArray.length(), 10)) {
                                castList.add(castArray.optJSONObject(i)?.optString("name") ?: "")
                            }
                        }
                        if (castList.isNotEmpty()) {
                            append("👥 <b>Oyuncular:</b> ${castList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                        }
                    }
                }
                
                // Dizi fragmanı
                val videos = seriesData.optJSONObject("videos")
                if (videos != null) {
                    val results = videos.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        var foundTrailer = false
                        for (i in 0 until results.length()) {
                            val video = results.optJSONObject(i)
                            val videoType = video?.optString("type", "") ?: ""
                            val videoKey = video?.optString("key", "") ?: ""
                            val videoSite = video?.optString("site", "") ?: ""
                            val videoName = video?.optString("name", "") ?: ""
                            
                            if ((videoType == "Trailer" || videoType == "Teaser") && videoSite == "YouTube" && videoKey.isNotEmpty()) {
                                append("<br>🎬 <b>Dizi Fragmanı:</b> $videoName<br>")
                                append("<div class='video-container' style='position:relative; padding-bottom:56.25%; height:0; overflow:hidden; margin:15px 0; border-radius:12px; box-shadow:0 2px 8px rgba(0,0,0,0.1);'>")
                                append("<iframe style='position:absolute; top:0; left:0; width:100%; height:100%; border:none;' src='https://www.youtube.com/embed/$videoKey' allowfullscreen></iframe>")
                                append("</div><br>")
                                foundTrailer = true
                                break
                            }
                        }
                        if (!foundTrailer) {
                            Log.d("TMDB", "No trailer found in series videos")
                        }
                    }
                }
                
                append("<hr>")
            }
            
            // Bölüm bilgileri
            if (episodeData != null) {
                append("<b>🎬 BÖLÜM BİLGİLERİ</b><br><br>")
                
                val episodeTitle = episodeData.optString("name", "")
                val episodeOverview = episodeData.optString("overview", "")
                val episodeAirDate = episodeData.optString("air_date", "").split("-").firstOrNull() ?: ""
                val episodeRating = episodeData.optDouble("vote_average", -1.0)
                
                if (episodeTitle.isNotEmpty()) append("🎬 <b>Bölüm Adı:</b> ${episodeTitle}<br>")
                if (episodeOverview.isNotEmpty()) append("📝 <b>Bölüm Konusu:</b><br>${episodeOverview}<br><br>")
                if (episodeAirDate.isNotEmpty()) append("📅 <b>Yayın Tarihi:</b> $episodeAirDate<br>")
                if (episodeRating >= 0) append("⭐ <b>Bölüm Puanı:</b> ${String.format("%.1f", episodeRating)} / 10<br>")
                
                // Bölüm oyuncuları
                val episodeCredits = episodeData.optJSONObject("credits")
                if (episodeCredits != null) {
                    val episodeCast = episodeCredits.optJSONArray("cast")
                    if (episodeCast != null && episodeCast.length() > 0) {
                        append("<br>👥 <b>Bu Bölümdeki Oyuncular:</b><br>")
                        append("<div style='display:flex; flex-wrap:wrap; gap:10px; justify-content:flex-start;'>")
                        for (i in 0 until minOf(episodeCast.length(), 5)) {
                            val actor = episodeCast.optJSONObject(i)
                            val actorName = actor?.optString("name", "") ?: ""
                            val character = actor?.optString("character", "") ?: ""
                            val profilePath = actor?.optString("profile_path", "") ?: ""
                            
                            if (actorName.isNotEmpty()) {
                                append("<div style='flex:0 0 auto; text-align:center; width:80px;'>")
                                if (profilePath.isNotEmpty()) {
                                    val imageUrl = "https://image.tmdb.org/t/p/w200$profilePath"
                                    append("<img src='$imageUrl' width='80' height='120' style='border-radius:8px; margin-bottom:4px;'><br>")
                                }
                                append("<b style='font-size:12px;'>$actorName</b>")
                                if (character.isNotEmpty()) append("<br><small style='font-size:10px;'>$character</small>")
                                append("</div>")
                            }
                        }
                        append("</div><br>")
                    }
                }
                
                // Bölüm fragmanı ve diğer videoları
                val videos = episodeData.optJSONObject("videos")
                if (videos != null) {
                    val results = videos.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        var foundVideo = false
                        for (i in 0 until results.length()) {
                            val video = results.optJSONObject(i)
                            val videoType = video?.optString("type", "") ?: ""
                            val videoKey = video?.optString("key", "") ?: ""
                            val videoSite = video?.optString("site", "") ?: ""
                            val videoName = video?.optString("name", "") ?: ""
                            
                            if (videoSite == "YouTube" && videoKey.isNotEmpty()) {
                                val videoTypeText = when (videoType) {
                                    "Trailer" -> "Fragman"
                                    "Teaser" -> "Tanıtım"
                                    "Clip" -> "Klip"
                                    "Featurette" -> "Özel Video"
                                    "Opening Credits" -> "Jenerik"
                                    "Behind the Scenes" -> "Kamera Arkası"
                                    else -> videoType
                                }
                                append("<br>🎬 <b>Bölüm $videoTypeText:</b> $videoName<br>")
                                append("<div class='video-container' style='position:relative; padding-bottom:56.25%; height:0; overflow:hidden; margin:15px 0; border-radius:12px; box-shadow:0 2px 8px rgba(0,0,0,0.1);'>")
                                append("<iframe style='position:absolute; top:0; left:0; width:100%; height:100%; border:none;' src='https://www.youtube.com/embed/$videoKey' allowfullscreen></iframe>")
                                append("</div><br>")
                                foundVideo = true
                            }
                        }
                        if (!foundVideo) {
                            Log.d("TMDB", "No videos found for episode")
                        }
                    }
                }
                
                append("<hr>")
            }
            
            // Eğer hiçbir TMDB verisi yoksa, en azından temel bilgileri göster
            if (seriesData == null && episodeData == null) {
                append("<b>📺 DİZİ BİLGİLERİ</b><br><br>")
                append("📝 <b>TMDB'den bilgi alınamadı.</b><br><br>")
            }
            
            val nation = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) {
                "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
            } else {
                "» ${loadData.group} | ${loadData.nation} «"
            }
            append(nation)
        }

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")
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
                episode
            }
        ) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOf(loadData.group, loadData.nation)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val loadData = fetchDataFromUrlOrJson(data)
        Log.d("IPTV", "loadData » $loadData")

        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        val kanal    = kanallar.items.firstOrNull { it.url == loadData.url } ?: return false
        Log.d("IPTV", "kanal » $kanal")

        val videoUrl = loadData.url
        val videoType = when {

            videoUrl.endsWith(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
            else -> ExtractorLinkType.M3U8
            
            }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "${loadData.title} (S${loadData.season}:E${loadData.episode})",
                url = videoUrl,
                type = videoType
            ) {
                headers = kanal.headers
                referer = kanal.headers["referrer"] ?: ""
                quality = Qualities.Unknown.value
            }
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
) {
    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

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

        val EXT_M3U = PlaylistItem.EXT_M3U
        val EXT_INF = PlaylistItem.EXT_INF
        val EXT_VLC_OPT = PlaylistItem.EXT_VLC_OPT

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
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")?.toString()
                    val referrer  = line.getTagValue("http-referrer")?.toString()

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

    private fun String.isExtendedM3u(): Boolean = startsWith(PlaylistItem.EXT_M3U)

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

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim()
        val titleAndAttributes = attributesString.split(",", limit = 2)
        
        val attributes = mutableMapOf<String, String>()
        if (titleAndAttributes.size > 1) {
            val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"|([\\w-]+)=([^\"]+)")
            
            attrRegex.findAll(titleAndAttributes[0]).forEach { matchResult ->
                val (quotedKey, quotedValue, unquotedKey, unquotedValue) = matchResult.destructured
                val key = quotedKey.takeIf { it.isNotEmpty() } ?: unquotedKey
                val value = quotedValue.takeIf { it.isNotEmpty() } ?: unquotedValue
                attributes[key] = value.replaceQuotesAndTrim()
            }
        }

        if (!attributes.containsKey("tvg-country")) {
            attributes["tvg-country"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("tvg-language")) {
            attributes["tvg-language"] = "TR/Altyazılı"
        }
        if (!attributes.containsKey("group-title")) {
            val episodeRegex = Regex("""(.*?)[^\w\d]+(\d+)\.\s*Sezon\s*(\d+)\.\s*Bölüm.*""")
            val match = episodeRegex.find(titleAndAttributes.last())
            if (match != null) {
                val (showName, _, _) = match.destructured
                attributes["group-title"] = showName.trim()
            } else {
                attributes["group-title"] = "Diğer"
            }
        }

        return attributes
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {

    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

val languageMap = mapOf(
    // Temel Diller
    "en" to "İngilizce",
    "tr" to "Türkçe",
    "ja" to "Japonca", // jp yerine ja daha standart ISO 639-1 kodudur
    "de" to "Almanca",
    "fr" to "Fransızca",
    "es" to "İspanyolca",
    "it" to "İtalyanca",
    "ru" to "Rusça",
    "pt" to "Portekizce",
    "ko" to "Korece",
    "zh" to "Çince", // Genellikle Mandarin için kullanılır
    "hi" to "Hintçe",
    "ar" to "Arapça",

    // Avrupa Dilleri
    "nl" to "Felemenkçe", // veya "Hollandaca"
    "sv" to "İsveççe",
    "no" to "Norveççe",
    "da" to "Danca",
    "fi" to "Fince",
    "pl" to "Lehçe", // veya "Polonyaca"
    "cs" to "Çekçe",
    "hu" to "Macarca",
    "ro" to "Rumence",
    "el" to "Yunanca", // Greek
    "uk" to "Ukraynaca",
    "bg" to "Bulgarca",
    "sr" to "Sırpça",
    "hr" to "Hırvatça",
    "sk" to "Slovakça",
    "sl" to "Slovence",

    // Asya Dilleri
    "th" to "Tayca",
    "vi" to "Vietnamca",
    "id" to "Endonezce",
    "ms" to "Malayca",
    "tl" to "Tagalogca", // Filipince
    "fa" to "Farsça", // İran
    "he" to "İbranice", // veya "iw"

    // Diğer
    "la" to "Latince",
    "xx" to "Belirsiz",
    "mul" to "Çok Dilli" 

)

fun getTurkishLanguageName(code: String?): String? {
    return languageMap[code?.lowercase()]
}
