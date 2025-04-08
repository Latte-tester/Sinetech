package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.DecimalRating // DecimalRating için eklendi
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig
import kotlinx.coroutines.* // CoroutineScope için eklendi
import kotlinx.coroutines.sync.Mutex // Mutex için eklendi
import kotlinx.coroutines.sync.withLock // Mutex için eklendi
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.math.minOf // minOf için eklendi (emin olmak için)

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi 🎬"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // Playlist'i güvenli bir şekilde yüklemek için mekanizma
    private var cachedPlaylist: Playlist? = null
    private val playlistMutex = Mutex() // Eş zamanlı erişimi engellemek için

    // Playlist'i getiren veya önbellekten döndüren suspend fonksiyon
    private suspend fun getPlaylist(): Playlist {
        cachedPlaylist?.let { return it } // Önbellekte varsa döndür

        return playlistMutex.withLock { // Aynı anda sadece bir coroutine'in yüklemesini sağla
            // Mutex kilidi alındıktan sonra tekrar kontrol et, belki başka biri yüklemiştir
            cachedPlaylist?.let { return it }

            Log.d("powerDizi", "Playlist yükleniyor: $mainUrl")
            try {
                val m3uText = app.get(mainUrl).text // app.get() suspend olduğu için sorun yok
                IptvPlaylistParser().parseM3U(m3uText).also {
                     Log.d("powerDizi", "Playlist başarıyla yüklendi ve parse edildi. Öğe sayısı: ${it.items.size}")
                     cachedPlaylist = it // Önbelleğe al
                }
            } catch (e: Exception) {
                Log.e("powerDizi", "Playlist yükleme/parse hatası!", e)
                Playlist() // Hata durumunda boş liste dön (ve önbelleğe alma)
            }
        }
    }


    private val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = getPlaylist().items // Playlist'i güvenli şekilde al
        if (allItems.isEmpty()) {
             Log.w("powerDizi", "getMainPage: Parse edilmiş öğe yok veya M3U yüklenemedi.")
             return newHomePageResponse(emptyList(), false)
        }

        Log.d("powerDizi", "getMainPage işleniyor. Toplam öğe: ${allItems.size}")

        // -> processedItems, groupedShows, homePageLists oluşturma kısmı önceki gibi...
        // (Bu kısımda değişiklik yok, önceki mesajdaki gibi kalabilir)
         val processedItems = allItems.mapNotNull { item ->
              val title = item.title
              if (title == null) {
                  Log.w("powerDizi", "getMainPage: Başlıksız öğe atlandı. URL: ${item.url}")
                  return@mapNotNull null
              }
              val match = episodeRegex.find(title)
              if (match != null) {
                  try {
                      val (_, seasonStr, episodeStr) = match.destructured
                      item.copy(
                          season = seasonStr.toInt(),
                          episode = episodeStr.toInt()
                      )
                  } catch (e: NumberFormatException) {
                      Log.w("powerDizi", "getMainPage: Sezon/Bölüm parse hatası: $title")
                      item // Parse edilemese de öğeyi koru
                  }
              } else {
                  item // Regex eşleşmezse orijinal öğeyi kullan
              }
          }

         val groupedShows = processedItems.groupBy {
              it.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
         }

         val homePageLists = mutableListOf<HomePageList>()

         groupedShows.forEach { (group, shows) ->
             val searchResponses = shows.mapNotNull { kanal ->
                 val streamurl = kanal.url ?: return@mapNotNull null
                 val channelname = kanal.title ?: "İsimsiz Bölüm"
                 val posterurl = kanal.attributes["tvg-logo"] ?: ""
                 val nation = kanal.attributes["tvg-country"] ?: "TR"

                 val loadDataJson = try {
                     LoadData(
                         streamurl, channelname, posterurl, group, nation,
                         kanal.season, kanal.episode
                     ).toJson()
                 } catch (e: Exception) {
                      Log.e("powerDizi", "getMainPage - LoadData JSON hatası: ${kanal.title}", e)
                      null
                 }

                 if (loadDataJson != null) {
                     newTvSeriesSearchResponse(
                         channelname, loadDataJson, TvType.TvSeries
                     ) { this.posterUrl = posterurl }
                 } else { null }
             }

             if (searchResponses.isNotEmpty()) {
                 // distinctBy ile URL bazında tekrarları kaldır
                 homePageLists.add(HomePageList(group, searchResponses.distinctBy { it.url }, isHorizontalImages = true))
             }
         }


        Log.d("powerDizi", "getMainPage tamamlandı. Grup sayısı: ${homePageLists.size}")
        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }
        override suspend fun search(query: String): List<SearchResponse> {
        val allItems = getPlaylist().items // Playlist'i güvenli şekilde al
        Log.d("powerDizi", "Arama yapılıyor: '$query'. Toplam öğe: ${allItems.size}")

        return allItems.filter {
            (it.title ?: "").contains(query, ignoreCase = true) ||
            (it.attributes["group-title"] ?: "").contains(query, ignoreCase = true)
        }.mapNotNull { kanal ->
             // -> mapNotNull içeriği önceki gibi...
             // (Bu kısımda değişiklik yok, önceki mesajdaki gibi kalabilir)
              val streamurl = kanal.url ?: return@mapNotNull null
              val channelname = kanal.title ?: "İsimsiz Bölüm"
              val posterurl = kanal.attributes["tvg-logo"] ?: ""
              val chGroup = kanal.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
              val nation = kanal.attributes["tvg-country"] ?: "TR"

              var seasonNum = 0 // Default 0 yapalım
              var episodeNum = 0
              episodeRegex.find(channelname)?.let { match ->
                  try {
                      seasonNum = match.destructured.component2().toInt()
                      episodeNum = match.destructured.component3().toInt()
                  } catch (e: NumberFormatException) { /* Hata */ }
              }

              val loadDataJson = try {
                  LoadData(streamurl, channelname, posterurl, chGroup, nation, seasonNum, episodeNum).toJson()
              } catch (e: Exception) {
                   Log.e("powerDizi", "Arama - LoadData JSON hatası: ${kanal.title}", e)
                   null
              }

              if (loadDataJson != null) {
                  newTvSeriesSearchResponse(
                      channelname, loadDataJson, TvType.TvSeries
                  ) { this.posterUrl = posterurl }
              } else { null }
        }.distinctBy { it.url }
         .also {
             Log.d("powerDizi", "Arama sonucu: ${it.size} öğe bulundu.")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // fetchTMDBData fonksiyonu önceki haliyle kalabilir (içinde minOf vardı)
    private suspend fun fetchTMDBData(title: String): JSONObject? {
         return withContext(Dispatchers.IO) {
             try {
                 val apiKey = try { BuildConfig.TMDB_SECRET_API.trim('"') } catch (e: Exception) { "" }
                 if (apiKey.isEmpty()) {
                     Log.e("TMDB", "TMDB API anahtarı boş veya bulunamadı.")
                     return@withContext null
                 }
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
             } catch (e: Exception) {
                 Log.e("TMDB", "TMDB veri çekme hatası: $title", e)
                 null
             }
         }
     }


    override suspend fun load(url: String): LoadResponse {
        Log.d("powerDizi", "load çağrıldı: $url")
        val allItems = getPlaylist().items // Playlist'i güvenli şekilde al

        val loadData = try {
             fetchDataFromUrlOrJson(url)
        } catch (e: Exception) {
             Log.e("powerDizi", "load - fetchDataFromUrlOrJson hatası: $url", e)
             return newTvSeriesLoadResponse("Dizi Yüklenemedi", url, TvType.TvSeries, emptyList()) {}
        }

        val seriesTitleForTMDB = loadData.title.replace(episodeRegex, "$1").trim()
        val tmdbData = if (seriesTitleForTMDB.isNotEmpty()) fetchTMDBData(seriesTitleForTMDB) else null

        val plot = buildString { /* ... TMDB verisi ile plot oluşturma (önceki gibi)... */
            if (tmdbData != null) {
                 val overview = tmdbData.optString("overview", "")
                 val firstAirDate = tmdbData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                 val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                 // DecimalRating kullanımı düzeltildi
                 val rating = if (ratingValue >= 0) DecimalRating((ratingValue * 10).toInt(), 1000) else null // Örn: 7.8 -> 78
                 val ratingString = if (rating != null) String.format("%.1f", ratingValue) else null // Gösterim için
                 val tagline = tmdbData.optString("tagline", "")
                 val originalName = tmdbData.optString("original_name", "")
                 val originalLanguage = tmdbData.optString("original_language", "")
                 val numberOfSeasons = tmdbData.optInt("number_of_seasons", 0)

                 val genresArray = tmdbData.optJSONArray("genres")
                 val genreList = mutableListOf<String>()
                 if (genresArray != null) {
                     for (i in 0 until genresArray.length()) {
                         genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                     }
                 }

                 val creditsObject = tmdbData.optJSONObject("credits")
                 val castList = mutableListOf<String>()
                 if (creditsObject != null) {
                     val castArray = creditsObject.optJSONArray("cast")
                     if (castArray != null) {
                         for (i in 0 until minOf(castArray.length(), 10)) { // minOf burada kullanılıyor
                             castList.add(castArray.optJSONObject(i)?.optString("name") ?: "")
                         }
                     }
                 }

                 if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>${tagline}<br><br>")
                 if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                 if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                 if (ratingString != null) append("⭐ <b>TMDB Puanı:</b> $ratingString / 10<br>") // ratingString kullanıldı
                 if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                 if (originalLanguage.isNotEmpty()) {
                     val langCode = originalLanguage.lowercase()
                     val turkishName = languageMap[langCode] ?: originalLanguage
                     append("🌐 <b>Orijinal Dil:</b> $turkishName<br>")
                 }
                 if (numberOfSeasons > 0) append("📅 <b>Toplam Sezon:</b> $numberOfSeasons<br>")
                 if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 if (castList.isNotEmpty()) append("👥 <b>Oyuncular:</b> ${castList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 append("<br>")
             } else {
                 append("TMDB bilgisi bulunamadı.<br><br>")
             }

             val nation = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) {
                 "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
             } else {
                 "» ${loadData.group} | ${loadData.nation} «"
             }
             append(nation)
        }


        // İlgili grubun tüm bölümlerini bul
        val groupEpisodes = allItems
            .filter { it.attributes["group-title"]?.toString()?.trim() == loadData.group }
            .mapNotNull { kanal -> /* ... Bölüm listesi oluşturma (önceki gibi) ... */
                val title = kanal.title ?: return@mapNotNull null
                val match = episodeRegex.find(title)
                if (match != null) {
                    try {
                        val (_, seasonStr, episodeStr) = match.destructured
                        val season = seasonStr.toInt()
                        val episode = episodeStr.toInt()
                        val epUrl = kanal.url ?: return@mapNotNull null
                        val epPoster = kanal.attributes["tvg-logo"] ?: loadData.poster
                        val epGroup = kanal.attributes["group-title"]?.toString()?.trim() ?: loadData.group
                        val epNation = kanal.attributes["tvg-country"] ?: loadData.nation
                        val episodeDataJson = LoadData(epUrl, title, epPoster, epGroup, epNation, season, episode).toJson()
                        Episode(data = episodeDataJson, name = title, season = season, episode = episode, posterUrl = epPoster)
                    } catch (e: Exception) { null }
                } else { null }
            }.sortedWith(compareBy({ it.season }, { it.episode }))

        Log.d("powerDizi", "load - Grup için ${groupEpisodes.size} bölüm bulundu: ${loadData.group}")

        return newTvSeriesLoadResponse(
            seriesTitleForTMDB.takeIf { it.isNotEmpty() } ?: loadData.group,
            url, TvType.TvSeries, groupEpisodes
        ) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOfNotNull(loadData.group.takeIf { it != "Diğer" }, loadData.nation).distinct()
            // TMDB'den ek bilgiler (DecimalRating kullanımı düzeltildi)
             tmdbData?.optString("first_air_date")?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()?.let {
                 this.year = it
             }
             tmdbData?.optDouble("vote_average")?.takeIf { it >= 0 }?.let { // >= 0 kontrolü
                 this.rating = DecimalRating((it * 10).toInt(), 1000) // DecimalRating burada
             }
             tmdbData?.optJSONObject("credits")?.optJSONArray("cast")?.let { castArray ->
                  val actors = mutableListOf<ActorData>()
                  for (i in 0 until minOf(castArray.length(), 15)) { // minOf burada
                      castArray.optJSONObject(i)?.let { actorJson ->
                          val name = actorJson.optString("name")
                          val character = actorJson.optString("character")
                          val profilePath = actorJson.optString("profile_path")
                          if (name.isNotEmpty()) {
                               val imageUrl = if (profilePath.isNotEmpty()) "https://image.tmdb.org/t/p/w185$profilePath" else null
                               actors.add(ActorData(Actor(name, imageUrl), roleString = character))
                          }
                      }
                  }
                  this.actors = actors // actors ataması
             }
        }
    }
        override suspend fun loadLinks(
        data: String, // JSON data from Episode.data
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = try {
            fetchDataFromUrlOrJson(data)
        } catch (e: Exception) {
            Log.e("powerDizi", "loadLinks JSON parse hatası: $data", e)
            return false
        }

        Log.d("powerDizi", "loadLinks için LoadData: $loadData")
        val url = loadData.url

        if (url.isBlank()) {
             Log.w("powerDizi", "loadLinks - Geçersiz URL: $url")
             return false
        }

        val isM3u8 = url.endsWith(".m3u8", ignoreCase = true)
        val isMkv = url.endsWith(".mkv", ignoreCase = true)
        val isMp4 = url.endsWith(".mp4", ignoreCase = true)
        val isAvi = url.endsWith(".avi", ignoreCase = true)

        if (isM3u8 || isMkv || isMp4 || isAvi) {
            // newExtractorLink yerine doğrudan ExtractorLink constructor'ını kullanıyoruz
            callback(
                ExtractorLink( // ExtractorLink constructor
                    source = this.name, // Eklenti adı
                    name = "${this.name} - ${ // Link adı
                        if (loadData.season > 0 && loadData.episode > 0) {
                            "S${loadData.season.toString().padStart(2, '0')}E${loadData.episode.toString().padStart(2, '0')}"
                        } else { loadData.title }
                    }",
                    url = url,
                    referer = "", // Referer parametresi var
                    quality = Qualities.Unknown.value, // Quality parametresi var
                    isM3u8 = isM3u8, // isM3u8 parametresi var
                    // headers = loadData.headers // headers parametresi var (eğer LoadData'ya eklersek)
                )
            )
            return true
        } else {
            Log.w("powerDizi", "Desteklenmeyen link formatı: $url")
            return false
        }
    }

    // Veri taşıma sınıfı (önceki gibi)
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 0,
        val episode: Int = 0
        // Gerekirse: val headers: Map<String, String> = emptyMap()
    )

    // Sadece JSON parse eden fonksiyon (önceki gibi)
    private fun fetchDataFromUrlOrJson(data: String): LoadData {
         if (data.startsWith("{")) {
             return try { parseJson<LoadData>(data) }
             catch (e: Exception) {
                 Log.e("powerDizi", "fetchData - JSON parse hatası: $data", e)
                 LoadData("", "HATA", "", "", "") // Hata durumu
             }
         } else {
             Log.e("powerDizi", "fetchData - JSON bekleniyordu ama URL veya başka bir şey geldi: $data")
             return LoadData("", "HATA", "", "", "") // Hata durumu
         }
    }

    // --- Playlist ve Parser Sınıfları ---
    // (Bu kısımda değişiklik yok, önceki mesajdaki gibi kalabilir)
    data class Playlist(
        val items: List<PlaylistItem> = emptyList()
    )

    data class PlaylistItem(
        val title: String?,
        val attributes: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val url: String?,
        val userAgent: String? = null,
        var season: Int = 0,
        var episode: Int = 0
    ) { /* companion object */ }

    class IptvPlaylistParser {
         @Throws(PlaylistParserException::class)
         fun parseM3U(content: String): Playlist { /* ... parser kodu (önceki gibi) ... */
             val lines = content.lines()
             if (lines.isEmpty() || !lines[0].startsWith(PlaylistItem.EXT_M3U)) {
                 throw PlaylistParserException.InvalidHeader()
             }
             val playlistItems = mutableListOf<PlaylistItem>()
             var currentTitle: String? = null
             var currentAttributes = mutableMapOf<String, String>()
             var currentHeaders = mutableMapOf<String, String>()
             var currentUserAgent: String? = null
             for (line in lines) {
                 val trimmedLine = line.trim()
                 when {
                     trimmedLine.startsWith(PlaylistItem.EXT_INF) -> {
                         currentTitle = null; currentAttributes = mutableMapOf(); currentHeaders = mutableMapOf(); currentUserAgent = null
                         try {
                             val (attrs, title) = parseExtInf(trimmedLine)
                             currentTitle = title
                             currentAttributes.putAll(attrs)
                         } catch (e: Exception) { Log.e("IptvPlaylistParser", "EXTINF parse hatası: $trimmedLine", e) }
                     }
                     trimmedLine.startsWith(PlaylistItem.EXT_VLC_OPT) -> {
                         val (key, value) = parseVlcOpt(trimmedLine)
                         if (key.equals("http-user-agent", ignoreCase = true)) {
                             currentUserAgent = value; currentHeaders["User-Agent"] = value
                         } else if (key.equals("http-referrer", ignoreCase = true)) {
                             currentHeaders["Referer"] = value
                         }
                     }
                     !trimmedLine.startsWith("#") && trimmedLine.isNotEmpty() -> {
                         val url = trimmedLine
                         val finalTitle = currentTitle ?: url.substringAfterLast('/').substringBefore('?')
                         if (!currentAttributes.containsKey("group-title") || currentAttributes["group-title"].isNullOrBlank()) {
                             val episodeRegexLocal = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")
                             val match = episodeRegexLocal.find(finalTitle)
                             val groupFromTitle = match?.destructured?.component1()?.trim()
                             currentAttributes["group-title"] = groupFromTitle?.takeIf { it.isNotEmpty() } ?: "Diğer"
                         }
                         if (!currentAttributes.containsKey("tvg-country")) currentAttributes["tvg-country"] = "TR"
                         if (!currentAttributes.containsKey("tvg-language")) currentAttributes["tvg-language"] = "TR"
                         playlistItems.add(PlaylistItem(finalTitle, currentAttributes.toMap(), currentHeaders.toMap(), url, currentUserAgent))
                         currentTitle = null; currentAttributes = mutableMapOf(); currentHeaders = mutableMapOf(); currentUserAgent = null
                     }
                 }
             }
             return Playlist(playlistItems)
         }
         private fun parseExtInf(line: String): Pair<Map<String, String>, String?> { /* ... önceki gibi ... */
             val attributes = mutableMapOf<String, String>()
             val dataPart = line.substringAfter(PlaylistItem.EXT_INF + ":").trim()
             val commaIndex = dataPart.indexOf(',')
             if (commaIndex == -1) { return Pair(attributes, dataPart.takeIf { it.isNotEmpty() }) }
             val attributesPart = dataPart.substringBefore(',').trim()
             val title = dataPart.substringAfter(',').trim().takeIf { it.isNotEmpty() }
             val attrRegex = Regex("""([\w-]+)=("[^"]+"|[^"\s]+)""")
             attrRegex.findAll(attributesPart).forEach { matchResult ->
                 val key = matchResult.groupValues[1].trim()
                 var value = matchResult.groupValues[2].trim()
                 if (value.startsWith('"') && value.endsWith('"')) { value = value.substring(1, value.length - 1) }
                 attributes[key] = value
             }
             return Pair(attributes, title)
         }
         private fun parseVlcOpt(line: String): Pair<String, String> { /* ... önceki gibi ... */
             val parts = line.substringAfter(PlaylistItem.EXT_VLC_OPT + ":").split('=', limit = 2)
             return if (parts.size == 2) { Pair(parts[0].trim(), parts[1].trim()) } else { Pair(parts.getOrElse(0) { "" }.trim(), "") }
         }
    }

    sealed class PlaylistParserException(message: String) : Exception(message) {
        class InvalidHeader : PlaylistParserException("Geçersiz M3U başlığı. #EXTM3U ile başlamıyor.")
    }

    // Dil haritası (önceki gibi)
    val languageMap = mapOf(
        "en" to "İngilizce", "tr" to "Türkçe", "ja" to "Japonca", "de" to "Almanca", "fr" to "Fransızca",
        "es" to "İspanyolca", "it" to "İtalyanca", "ru" to "Rusça", "pt" to "Portekizce", "ko" to "Korece",
        "zh" to "Çince", "hi" to "Hintçe", "ar" to "Arapça", "nl" to "Felemenkçe", "sv" to "İsveççe",
        "no" to "Norveççe", "da" to "Danca", "fi" to "Fince", "pl" to "Lehçe", "cs" to "Çekçe",
        "hu" to "Macarca", "ro" to "Rumence", "el" to "Yunanca", "uk" to "Ukraynaca", "bg" to "Bulgarca",
        "sr" to "Sırpça", "hr" to "Hırvatça", "sk" to "Slovakça", "sl" to "Slovence", "th" to "Tayca",
        "vi" to "Vietnamca", "id" to "Endonezce", "ms" to "Malayca", "tl" to "Tagalogca", "fa" to "Farsça",
        "he" to "İbranice", "la" to "Latince", "xx" to "Belirsiz", "mul" to "Çok Dilli"
    )

} // class powerDizi sonu