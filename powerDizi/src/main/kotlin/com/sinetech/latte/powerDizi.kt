package com.sinetech.latte

// Gerekli importlar eklendi/kontrol edildi
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.DecimalRating // TMDB için eklendi
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.math.minOf // TMDB için eklendi

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi 🎬"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // --- Performans İyileştirmesi: Playlist'i önbelleğe al ---
    private var cachedPlaylist: Playlist? = null
    private val playlistMutex = Mutex()

    private suspend fun getPlaylist(): Playlist {
        cachedPlaylist?.let { return it }
        return playlistMutex.withLock {
            cachedPlaylist?.let { return it } // Double-check lock
            Log.d("powerDizi", "Playlist yükleniyor: $mainUrl")
            try {
                val m3uText = app.get(mainUrl).text
                IptvPlaylistParser().parseM3U(m3uText).also {
                     Log.d("powerDizi", "Playlist başarıyla yüklendi. Öğe sayısı: ${it.items.size}")
                     cachedPlaylist = it
                }
            } catch (e: Exception) {
                Log.e("powerDizi", "Playlist yükleme/parse hatası!", e)
                Playlist() // Hata durumunda boş liste
            }
        }
    }
    // --- Playlist önbellekleme sonu ---

    private val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = getPlaylist().items // Önbellekten veya yükleyerek al
        if (allItems.isEmpty()) {
             Log.w("powerDizi", "getMainPage: Playlist boş veya yüklenemedi.")
             return newHomePageResponse(emptyList(), false)
        }

        // Öğe işleme (Sezon/Bölüm ekleme) - Bu kısım aynı kalabilir
        val processedItems = allItems.map { item ->
            val title = item.title.toString() // Null kontrolü eklenebilir
            val match = episodeRegex.find(title)
            if (match != null) {
                try { // Sayıya çevirme hatasına karşı try-catch
                    val (_, seasonStr, episodeStr) = match.destructured
                    item.copy(
                        season = seasonStr.toInt(),
                        episode = episodeStr.toInt(),
                        attributes = item.attributes.toMutableMap().apply {
                            putIfAbsent("tvg-country", "TR/Altyazılı") // putIfAbsent daha güvenli
                            putIfAbsent("tvg-language", "TR;EN")
                        }
                    )
                } catch (e: NumberFormatException) {
                    Log.w("powerDizi", "Sezon/Bölüm parse hatası (getMainPage): $title")
                    item // Hata olursa orijinal öğeyi döndür
                }
            } else {
                item.copy( // Regex eşleşmese de varsayılan atribütleri ekleyebiliriz
                    attributes = item.attributes.toMutableMap().apply {
                        putIfAbsent("tvg-country", "TR")
                        putIfAbsent("tvg-language", "TR;EN")
                    }
                )
            }
        }

        // Gruplama - Bu kısım aynı kalabilir, ancak M3U'daki group-title'a bağlı
        val groupedShows = processedItems.groupBy {
             it.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
        }

        val homePageLists = mutableListOf<HomePageList>()

        groupedShows.forEach { (group, shows) ->
            // TvSeries için newTvSeriesSearchResponse kullanalım
            // Hata durumunda atlamak için mapNotNull kullanalım
            val searchResponses = shows.mapNotNull { kanal ->
                val streamurl = kanal.url // Null kontrolü eklendi
                if (streamurl.isNullOrBlank()) {
                    Log.w("powerDizi", "URL eksik veya boş: ${kanal.title}")
                    return@mapNotNull null // URL yoksa bu öğeyi atla
                }
                val channelname = kanal.title ?: "İsimsiz Bölüm" // Null fallback
                // Logo ve ülke için null veya boş kontrolü
                val posterurl = kanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: ""
                val nation = kanal.attributes["tvg-country"]?.takeIf { it.isNotBlank() } ?: "TR"

                val loadDataJson = try {
                    LoadData(
                        streamurl, channelname, posterurl, group, nation,
                        kanal.season, kanal.episode // Bunlar zaten Int? Kontrol edilebilir.
                    ).toJson()
                } catch (e: Exception) {
                     Log.e("powerDizi", "getMainPage - LoadData JSON hatası: ${kanal.title}", e)
                     null // JSON oluşturulamazsa atla
                }

                if (loadDataJson != null) {
                    newTvSeriesSearchResponse( // newLive yerine newTvSeries
                        channelname,
                        loadDataJson, // Tıklanınca load'a gidecek veri
                        TvType.TvSeries
                    ) {
                        this.posterUrl = posterurl
                    }
                } else {
                    null
                }
            }

            if (searchResponses.isNotEmpty()) {
                // Aynı URL'ye sahip olası tekrarları kaldır
                homePageLists.add(HomePageList(group, searchResponses.distinctBy { it.url }, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }
        override suspend fun search(query: String): List<SearchResponse> {
        val allItems = getPlaylist().items // Önbellekten al
        val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*") // Bu regex burada tekrar tanımlanmış, sınıf seviyesine alınabilir.

        // Filtreleme ve mapNotNull ile daha güvenli hale getirme
        return allItems.filter {
            (it.title ?: "").contains(query, ignoreCase = true) ||
            (it.attributes["group-title"] ?: "").contains(query, ignoreCase = true) // Grup adına göre de ara
        }.mapNotNull { kanal ->
            val streamurl = kanal.url // Null kontrolü
            if (streamurl.isNullOrBlank()) return@mapNotNull null
            val channelname = kanal.title ?: "İsimsiz Bölüm"
            val posterurl = kanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: ""
            val chGroup = kanal.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
            val nation = kanal.attributes["tvg-country"]?.takeIf { it.isNotBlank() } ?: "TR"

            // Sezon/Bölüm parse etme (try-catch ile)
            var seasonNum = 0 // Varsayılan 0 yapalım
            var episodeNum = 0
            episodeRegex.find(channelname)?.let { match ->
                try {
                    seasonNum = match.destructured.component2().toInt()
                    episodeNum = match.destructured.component3().toInt()
                } catch (e: NumberFormatException) { /* Hata loglanabilir */ }
            }

            val loadDataJson = try {
                LoadData(streamurl, channelname, posterurl, chGroup, nation, seasonNum, episodeNum).toJson()
            } catch (e: Exception) { null }

            if (loadDataJson != null) {
                newTvSeriesSearchResponse( // newLive yerine
                    channelname,
                    loadDataJson,
                    TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                }
            } else {
                null
            }
        }.distinctBy { it.url } // Tekrarları kaldır
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // fetchTMDBData fonksiyonu aynı kalabilir, importlar eklendi varsayılıyor
    private suspend fun fetchTMDBData(title: String): JSONObject? {
         return withContext(Dispatchers.IO) {
             try {
                 val apiKey = try { BuildConfig.TMDB_SECRET_API.trim('"') } catch (e: Exception) { "" }
                 if (apiKey.isEmpty()) { Log.e("TMDB", "API key is empty"); return@withContext null }
                 val cleanedTitle = title.replace(Regex("\\([^)]*\\)"), "").trim()
                 if (cleanedTitle.isEmpty()) return@withContext null
                 val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                 val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"
                 val response = URL(searchUrl).readText()
                 val jsonResponse = JSONObject(response)
                 val results = jsonResponse.optJSONArray("results")
                 if (results != null && results.length() > 0) {
                     val tvId = results.getJSONObject(0).optInt("id", -1)
                     if (tvId == -1) return@withContext null
                     val detailsUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                     val detailsResponse = URL(detailsUrl).readText()
                     return@withContext JSONObject(detailsResponse)
                 }
                 null
             } catch (e: Exception) { Log.e("TMDB", "Error fetching TMDB data: $title", e); null }
         }
     }

    override suspend fun load(url: String): LoadResponse {
        // İzlenme durumu SharedPreferences ile saklama mantığı kaldırıldı, CloudStream kendisi yönetmeli
        // val watchKey = ...
        // val progressKey = ...

        val loadData = try { // fetchDataFromUrlOrJson artık sadece JSON parse ediyor
            fetchDataFromUrlOrJson(url)
        } catch (e: Exception) {
             Log.e("powerDizi", "load - fetchDataFromUrlOrJson hatası: $url", e)
             // Hata durumunda kullanıcıya bilgi ver
             return newTvSeriesLoadResponse("Dizi Bilgisi Yüklenemedi", url, TvType.TvSeries, emptyList()) {
                 this.plot = "Tıklanan öğenin verisi okunamadı."
             }
        }

        val seriesTitleForTMDB = loadData.title.replace(episodeRegex, "$1").trim()
        val tmdbData = if (seriesTitleForTMDB.isNotEmpty()) fetchTMDBData(seriesTitleForTMDB) else null

        val plot = buildString {
             // TMDB verisi işleme kısmı aynı kalabilir (importlar eklendi varsayılıyor)
             if (tmdbData != null) {
                 val overview = tmdbData.optString("overview", "")
                 val firstAirDate = tmdbData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                 val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                 val rating = if (ratingValue >= 0) DecimalRating((ratingValue * 10).toInt(), 1000) else null // DecimalRating kullan
                 val ratingString = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                 val tagline = tmdbData.optString("tagline", "")
                 val originalName = tmdbData.optString("original_name", "")
                 val originalLanguage = tmdbData.optString("original_language", "")
                 val numberOfSeasons = tmdbData.optInt("number_of_seasons", 0)
                 val genresArray = tmdbData.optJSONArray("genres"); val genreList = mutableListOf<String>()
                 if (genresArray != null) for (i in 0 until genresArray.length()) genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                 val creditsObject = tmdbData.optJSONObject("credits"); val castList = mutableListOf<String>()
                 if (creditsObject != null) {
                     val castArray = creditsObject.optJSONArray("cast")
                     if (castArray != null) for (i in 0 until minOf(castArray.length(), 10)) castList.add(castArray.optJSONObject(i)?.optString("name") ?: "") // minOf kullan
                 }
                 if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>${tagline}<br><br>")
                 if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                 if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                 if (ratingString != null) append("⭐ <b>TMDB Puanı:</b> $ratingString / 10<br>")
                 if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                 if (originalLanguage.isNotEmpty()) { val langCode=originalLanguage.lowercase(); val turkishName=languageMap[langCode]?:originalLanguage; append("🌐 <b>Orijinal Dil:</b> $turkishName<br>") }
                 if (numberOfSeasons > 0) append("📅 <b>Toplam Sezon:</b> $numberOfSeasons<br>")
                 if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 if (castList.isNotEmpty()) append("👥 <b>Oyuncular:</b> ${castList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 append("<br>")
             } else { append("TMDB bilgisi bulunamadı.<br><br>") }
             val nationText = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) { "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️" } else { "» ${loadData.group} | ${loadData.nation} «" }
             append(nationText)
        }

        val allItems = getPlaylist().items // Bölümleri bulmak için tekrar al
        // Bölüm listesi oluşturma (newEpisode kullanarak)
        val groupEpisodes = allItems
            .filter { it.attributes["group-title"]?.toString()?.trim() == loadData.group }
            .mapNotNull { kanal ->
                val title = kanal.title ?: return@mapNotNull null
                val match = episodeRegex.find(title)
                if (match != null) {
                    try {
                        val (_, seasonStr, episodeStr) = match.destructured
                        val season = seasonStr.toInt()
                        val episode = episodeStr.toInt()
                        val epUrl = kanal.url ?: return@mapNotNull null
                        val epPoster = kanal.attributes["tvg-logo"]?.takeIf {it.isNotBlank()} ?: loadData.poster
                        val epGroup = kanal.attributes["group-title"]?.toString()?.trim() ?: loadData.group
                        val epNation = kanal.attributes["tvg-country"]?.takeIf {it.isNotBlank()} ?: loadData.nation

                         val episodeDataJson = LoadData(epUrl, title, epPoster, epGroup, epNation, season, episode).toJson()

                        // Episode yerine newEpisode kullan
                        newEpisode(episodeDataJson) { // data'yı constructor'a ver
                            this.name = title // Bölüm adı
                            this.season = season
                            this.episode = episode
                            this.posterUrl = epPoster
                            // this.rating = ... // Gerekirse TMDB'den bölüm rating'i eklenebilir
                            // this.description = ... // Gerekirse TMDB'den bölüm özeti eklenebilir
                        }
                    } catch (e: Exception) {
                         Log.e("powerDizi", "load - Bölüm parse/oluşturma hatası: $title", e)
                         null
                    }
                } else { null }
            }.sortedWith(compareBy({ it.season }, { it.episode })) // Sırala

        // Ana dizi başlığını temizle
        val seriesTitleClean = seriesTitleForTMDB.takeIf { it.isNotEmpty() } ?: loadData.group

        return newTvSeriesLoadResponse(
            seriesTitleClean, // Dizi Adı
            url, // Tıklanan öğenin orijinal JSON verisi
            TvType.TvSeries,
            groupEpisodes // Bölüm listesi
        ) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOfNotNull(loadData.group.takeIf { it != "Diğer" }, loadData.nation).distinct()
            // TMDB verilerini ekle
            tmdbData?.optString("first_air_date")?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()?.let { this.year = it }
            tmdbData?.optDouble("vote_average")?.takeIf { it >= 0 }?.let { this.rating = DecimalRating((it * 10).toInt(), 1000) }
            tmdbData?.optJSONObject("credits")?.optJSONArray("cast")?.let { castArray ->
                 val actors = mutableListOf<ActorData>()
                 for (i in 0 until minOf(castArray.length(), 15)) {
                     castArray.optJSONObject(i)?.let { actorJson ->
                         val name = actorJson.optString("name"); val character = actorJson.optString("character"); val profilePath = actorJson.optString("profile_path")
                         if (name.isNotEmpty()) { val imageUrl = profilePath.takeIf{it.isNotEmpty()}?.let{"https://image.tmdb.org/t/p/w185$it"}; actors.add(ActorData(Actor(name, imageUrl), roleString = character)) }
                     }
                 }
                 this.actors = actors // Direkt atama
            }
        }
    }
        // --- loadLinks DÜZELTİLDİ ---
    override suspend fun loadLinks(
        data: String, // JSON data from Episode.data
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit // Dönüş tipi Unit
    ): Boolean { // Fonksiyon Boolean döner
        val loadData = try {
            fetchDataFromUrlOrJson(data)
        } catch (e: Exception) {
            Log.e("powerDizi", "loadLinks JSON parse hatası: $data", e)
            return false // Hata varsa false dön
        }

        Log.d("powerDizi", "loadLinks için LoadData: $loadData")
        val url = loadData.url

        if (url.isBlank()) {
             Log.w("powerDizi", "loadLinks - Geçersiz URL: $url")
             return false
        }

        // Header bilgisine artık ihtiyacımız yok gibi görünüyor, çünkü ExtractorLink'e eklemiyoruz.
        // İleride gerekirse LoadData'ya eklenmeli.
        // val kanallar = getPlaylist().items // M3U'yu tekrar okumaya gerek yok!
        // val kanal = kanallar.firstOrNull { it.url == loadData.url }
        // val headers = kanal?.headers ?: emptyMap()
        // val referer = headers["Referer"] ?: "" // Referer header'dan alınabilir

        val isM3u8 = url.endsWith(".m3u8", ignoreCase = true)
        val isMkvOrOtherVideo = url.endsWith(".mkv", ignoreCase = true) ||
                                url.endsWith(".mp4", ignoreCase = true) ||
                                url.endsWith(".avi", ignoreCase = true) // Diğer formatlar eklenebilir

        if (isM3u8 || isMkvOrOtherVideo) {
            // Deprecated constructor'ı kullanıyoruz ama isM3u8 parametresi ile
            callback(
                ExtractorLink( // ExtractorLink constructor
                    source = this.name,
                    name = "${this.name} - ${ // Link adı
                        if (loadData.season > 0 && loadData.episode > 0) {
                            "S${loadData.season.toString().padStart(2, '0')}E${loadData.episode.toString().padStart(2, '0')}"
                        } else { loadData.title }
                    }",
                    url = url,
                    referer = "", // Şimdilik boş, gerekirse header'dan alınır
                    quality = Qualities.Unknown.value, // Kalite bilinmiyor
                    isM3u8 = isM3u8 // BU EN ÖNEMLİSİ! M3U8 ise true, değilse false
                    // headers = headers // Gerekirse header eklenebilir
                )
            )
            return true // Link gönderildi
        } else {
            Log.w("powerDizi", "Desteklenmeyen link formatı: $url")
            return false // Desteklenmeyen format
        }
    }
    // --- loadLinks Düzeltmesi Sonu ---

    // LoadData: İzlenme durumu kaldırıldı
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 0, // Varsayılan 0
        val episode: Int = 0 // Varsayılan 0
        // Gerekirse: val headers: Map<String, String> = emptyMap()
    )

    // fetchDataFromUrlOrJson: Sadece JSON parse edecek şekilde basitleştirildi
    private fun fetchDataFromUrlOrJson(data: String): LoadData {
         if (data.startsWith("{")) {
             return try {
                 parseJson<LoadData>(data)
             } catch (e: Exception) {
                  Log.e("powerDizi", "fetchData - JSON parse hatası: $data", e)
                  throw IllegalArgumentException("Geçersiz JSON verisi: $data", e) // Hata fırlatmak daha iyi olabilir
                  // Veya: return LoadData("", "HATA", "", "", "")
             }
         } else {
             // Bu path artık çağrılmamalı. Hata fırlat.
             throw IllegalArgumentException("fetchDataFromUrlOrJson JSON bekliyordu, ancak başka bir şey aldı: $data")
         }
    }

    // --- Playlist ve Parser Sınıfları ---
    // Parser kodu ve diğer yardımcılar önceki mesajdaki gibi kalabilir.
    // Sadece importların ve sabit referanslarının doğru olduğundan emin ol.
    data class Playlist(val items: List<PlaylistItem> = emptyList())
    data class PlaylistItem(
        val title: String?,
        val attributes: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val url: String?,
        val userAgent: String? = null,
        var season: Int = 0, // Sonradan atanacak
        var episode: Int = 0 // Sonradan atanacak
    ) {
        companion object {
            const val EXT_M3U = "#EXTM3U"
            const val EXT_INF = "#EXTINF"
            const val EXT_VLC_OPT = "#EXTVLCOPT"
        }
    }
    class IptvPlaylistParser {
        @Throws(PlaylistParserException::class)
        fun parseM3U(content: String): Playlist {
            val lines = content.lines()
            if (lines.firstOrNull()?.startsWith(PlaylistItem.EXT_M3U) != true) { throw PlaylistParserException.InvalidHeader() }
            val playlistItems = mutableListOf<PlaylistItem>()
            var currentTitle: String? = null; var currentAttributes = mutableMapOf<String, String>(); var currentHeaders = mutableMapOf<String, String>(); var currentUserAgent: String? = null
            for (line in lines) {
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith(PlaylistItem.EXT_INF) -> { currentTitle=null; currentAttributes=mutableMapOf(); /*...*/ try { val (attrs, title)=parseExtInf(trimmedLine); currentTitle=title; currentAttributes.putAll(attrs) } catch (e: Exception){/*...*/} }
                    trimmedLine.startsWith(PlaylistItem.EXT_VLC_OPT) -> { val (key, value)=parseVlcOpt(trimmedLine); if(key.equals("http-user-agent",true)){currentUserAgent=value; currentHeaders["User-Agent"]=value} else if(key.equals("http-referrer",true)){currentHeaders["Referer"]=value} }
                    !trimmedLine.startsWith("#") && trimmedLine.isNotEmpty() -> {
                        val url=trimmedLine; val finalTitle=currentTitle ?: url.substringAfterLast('/').substringBefore('?');
                        if(!currentAttributes.containsKey("group-title") || currentAttributes["group-title"].isNullOrBlank()){ val episodeRegexLocal=Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*"); val match=episodeRegexLocal.find(finalTitle); val groupFromTitle=match?.destructured?.component1()?.trim(); currentAttributes["group-title"]=groupFromTitle?.takeIf{it.isNotEmpty()}?:"Diğer" }
                        if (!currentAttributes.containsKey("tvg-country")) currentAttributes["tvg-country"] = "TR"; if (!currentAttributes.containsKey("tvg-language")) currentAttributes["tvg-language"] = "TR"
                        playlistItems.add(PlaylistItem(finalTitle, currentAttributes.toMap(), currentHeaders.toMap(), url, currentUserAgent)); currentTitle=null; /*...*/
                    }
                }
            }
            return Playlist(playlistItems)
        }
        private fun parseExtInf(line: String): Pair<Map<String, String>, String?> { val attributes=mutableMapOf<String,String>(); val dataPart=line.substringAfter(PlaylistItem.EXT_INF+":").trim(); val commaIndex=dataPart.indexOf(','); if(commaIndex==-1){return Pair(attributes, dataPart.takeIf{it.isNotEmpty()})}; val attributesPart=dataPart.substringBefore(',').trim(); val title=dataPart.substringAfter(',').trim().takeIf{it.isNotEmpty()}; val attrRegex=Regex("""([\w-]+)=("[^"]+"|[^"\s]+)"""); attrRegex.findAll(attributesPart).forEach { matchResult-> val key=matchResult.groupValues[1].trim(); var value=matchResult.groupValues[2].trim(); if(value.startsWith('"')&&value.endsWith('"')){value=value.substring(1,value.length-1)}; attributes[key]=value }; return Pair(attributes, title) }
        private fun parseVlcOpt(line: String): Pair<String, String> { val parts=line.substringAfter(PlaylistItem.EXT_VLC_OPT+":").split('=',limit=2); return if(parts.size==2){Pair(parts[0].trim(),parts[1].trim())}else{Pair(parts.getOrElse(0){""}.trim(),"")} }
    }
    sealed class PlaylistParserException(message: String): Exception(message){ class InvalidHeader: PlaylistParserException("Geçersiz M3U başlığı.") }
    val languageMap = mapOf( "en" to "İngilizce", /*...*/ "mul" to "Çok Dilli" ) // Aynı kalabilir
} // class powerDizi sonu