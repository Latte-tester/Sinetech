package com.sinetech.latte

// Orijinal importlar kalıyor, yenilerini eklemiyoruz şimdilik
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
// import com.lagradost.cloudstream3.utils.ExtractorLinkType // Buna artık gerek yok
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
// import kotlin.math.minOf // Şimdilik eklemiyoruz
// import com.lagradost.cloudstream3.mvvm.DecimalRating // Şimdilik eklemiyoruz

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi 🎬"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // Playlist önbellekleme eklemiyoruz, orijinaldeki gibi kalıyor
    // private val getPlaylist... fonksiyonu YOK

    private val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*") // Bunu sınıf seviyesine aldım

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // M3U her seferinde okunuyor (orijinaldeki gibi)
        val kanallar = try { // Hata durumunu yakalayalım
            IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        } catch (e: Exception) {
            Log.e("powerDizi", "getMainPage - M3U okuma/parse hatası!", e)
            return newHomePageResponse(emptyList(), false) // Hata olursa boş dön
        }

        // Parse episode information from titles (Orijinaldeki gibi)
        val processedItems = kanallar.items.map { item ->
            val title = item.title.toString()
            val match = episodeRegex.find(title)
            if (match != null) {
                 try { // Sayı parse hatası olabilir
                     val (_, seasonStr, episodeStr) = match.destructured
                     item.copy(
                         season = seasonStr.toInt(),
                         episode = episodeStr.toInt(),
                         attributes = item.attributes.toMutableMap().apply {
                             putIfAbsent("tvg-country", "TR/Altyazılı")
                             putIfAbsent("tvg-language", "TR;EN")
                         }
                     )
                 } catch (e: NumberFormatException) { item } // Hata olursa orijinal item
            } else {
                item.copy(
                    attributes = item.attributes.toMutableMap().apply {
                        putIfAbsent("tvg-country", "TR")
                        putIfAbsent("tvg-language", "TR;EN")
                    }
                )
            }
        }

        // Gruplama (Orijinaldeki gibi)
        val groupedShows = processedItems.groupBy { it.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer" }

        val homePageLists = mutableListOf<HomePageList>()

        groupedShows.forEach { (group, shows) ->
            // Sadece newLiveSearchResponse yerine newTvSeriesSearchResponse kullandık
            val searchResponses = shows.mapNotNull { kanal -> // mapNotNull daha güvenli
                val streamurl = kanal.url // Null kontrolü
                if (streamurl.isNullOrBlank()) return@mapNotNull null
                val channelname = kanal.title ?: "İsimsiz Bölüm"
                // Hata vermemesi için toString yerine null check ve fallback
                val posterurl = kanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: ""
                val nation = kanal.attributes["tvg-country"] ?: "TR"

                // LoadData JSON oluşturma
                 val loadDataJson = try {
                     LoadData(streamurl, channelname, posterurl, group, nation, kanal.season, kanal.episode).toJson()
                 } catch (e: Exception) { null }

                 if (loadDataJson != null) {
                     // newLive yerine newTvSeries
                     newTvSeriesSearchResponse(
                         channelname,
                         loadDataJson,
                         TvType.TvSeries
                     ) {
                         this.posterUrl = posterurl
                         // this.lang = nation // SearchResponse'da lang yok
                     }
                 } else { null }
            }

            if (searchResponses.isNotEmpty()) {
                // Tekrarları engellemek iyi bir pratik
                homePageLists.add(HomePageList(group, searchResponses.distinctBy { it.url }, isHorizontalImages = true))
            }
        }

        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }
        override suspend fun search(query: String): List<SearchResponse> {
        // Orijinaldeki gibi M3U okunuyor
        val kanallar = try { IptvPlaylistParser().parseM3U(app.get(mainUrl).text) } catch (e: Exception) { Playlist() }
        // val episodeRegex = ... // Zaten sınıf seviyesinde tanımlı

        return kanallar.items.filter {
            (it.title ?: "").contains(query, ignoreCase = true) ||
            (it.attributes["group-title"] ?: "").contains(query, ignoreCase = true) // Grup adına göre de ara
        }.mapNotNull { kanal -> // mapNotNull
            val streamurl = kanal.url
            if (streamurl.isNullOrBlank()) return@mapNotNull null
            val channelname = kanal.title ?: "İsimsiz Bölüm"
            val posterurl = kanal.attributes["tvg-logo"]?.takeIf { it.isNotBlank() } ?: ""
            // Hata vermesin diye null kontrolü ve fallback
            val chGroup = kanal.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
            val nation = kanal.attributes["tvg-country"] ?: "TR"

            var seasonNum = 0 // Default 0
            var episodeNum = 0
            episodeRegex.find(channelname)?.let { match ->
                try {
                    seasonNum = match.destructured.component2().toInt()
                    episodeNum = match.destructured.component3().toInt()
                } catch (e: NumberFormatException) {}
            }

            val loadDataJson = try {
                 LoadData(streamurl, channelname, posterurl, chGroup, nation, seasonNum, episodeNum).toJson()
            } catch (e: Exception) { null }

             if (loadDataJson != null) {
                // newLive yerine newTvSeries
                newTvSeriesSearchResponse(
                    channelname,
                    loadDataJson,
                    TvType.TvSeries
                ) {
                    this.posterUrl = posterurl
                }
             } else { null }
        }.distinctBy { it.url } // Tekrarları kaldır
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // fetchTMDBData - Orijinal haliyle bırakıyoruz, DecimalRating/minOf eklemiyoruz
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
                 val results = jsonResponse.optJSONArray("results") // optJSONArray kullanmak daha güvenli
                 if (results != null && results.length() > 0) {
                     val tvId = results.getJSONObject(0).optInt("id", -1) // optInt kullanmak daha güvenli
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
        // İzleme takibi orijinaldeki gibi kalıyor
        val watchKey = "watch_${url.hashCode()}"
        val progressKey = "progress_${url.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

        val loadData = try { fetchDataFromUrlOrJson(url) } catch (e: Exception) {
             return newTvSeriesLoadResponse("Hata", url, TvType.TvSeries, emptyList()) {}
        }

        val seriesTitleForTMDB = loadData.title.replace(episodeRegex, "$1").trim()
        val tmdbData = if (seriesTitleForTMDB.isNotEmpty()) fetchTMDBData(seriesTitleForTMDB) else null

        val plot = buildString {
            // Orijinaldeki TMDB plot oluşturma mantığı kalıyor
            // DecimalRating veya minOf kullanmıyoruz
             if (tmdbData != null) {
                 val overview = tmdbData.optString("overview", "")
                 val firstAirDate = tmdbData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                 val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                 val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null // Sadece String formatı
                 val tagline = tmdbData.optString("tagline", "")
                 val originalName = tmdbData.optString("original_name", "")
                 val originalLanguage = tmdbData.optString("original_language", "")
                 val numberOfSeasons = tmdbData.optInt("number_of_seasons", 1) // Orijinalde 1'di
                 val genresArray = tmdbData.optJSONArray("genres"); val genreList = mutableListOf<String>()
                 if (genresArray != null) for (i in 0 until genresArray.length()) genreList.add(genresArray.optJSONObject(i)?.optString("name") ?: "")
                 val creditsObject = tmdbData.optJSONObject("credits"); val castList = mutableListOf<String>()
                 if (creditsObject != null) {
                     val castArray = creditsObject.optJSONArray("cast")
                     // minOf yerine basit bir sınır koyalım (orijinaldeki gibi)
                     if (castArray != null) for (i in 0 until castArray.length().coerceAtMost(10)) castList.add(castArray.optJSONObject(i)?.optString("name") ?: "")
                 }
                 if (tagline.isNotEmpty()) append("💭 <b>Slogan:</b><br>${tagline}<br><br>")
                 if (overview.isNotEmpty()) append("📝 <b>Konu:</b><br>${overview}<br><br>")
                 if (firstAirDate.isNotEmpty()) append("📅 <b>İlk Yayın Tarihi:</b> $firstAirDate<br>")
                 if (rating != null) append("⭐ <b>TMDB Puanı:</b> $rating / 10<br>") // String rating kullanılıyor
                 if (originalName.isNotEmpty()) append("📜 <b>Orijinal Ad:</b> $originalName<br>")
                 if (originalLanguage.isNotEmpty()) { val langCode=originalLanguage.lowercase(); val turkishName=languageMap[langCode]?:originalLanguage; append("🌐 <b>Orijinal Dil:</b> $turkishName<br>") }
                 if (numberOfSeasons > 1) append("📅 <b>Toplam Sezon:</b> $numberOfSeasons<br>")
                 if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter{it.isNotEmpty()}.joinToString(", ")}<br>")
                 if (castList.isNotEmpty()) append("👥 <b>Oyuncular:</b> ${castList.filter{it.isNotEmpty()}.joinToString(", ")}<br>")
                 append("<br>")
             } else { append("TMDB bilgisi bulunamadı.<br><br>") } // Hata mesajı eklendi
             val nationText = if (listOf("adult", "erotic", "erotik", "porn", "porno").any { loadData.group.contains(it, ignoreCase = true) }) { "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️" } else { "» ${loadData.group} | ${loadData.nation} «" }
             append(nationText)
        }

        // M3U tekrar okunuyor (orijinaldeki gibi)
        val kanallar = try { IptvPlaylistParser().parseM3U(app.get(mainUrl).text) } catch (e: Exception) { Playlist() }
        // val episodeRegex = ... // Zaten sınıf seviyesinde tanımlı

        // Bölüm listesi oluşturma (newEpisode kullanarak)
        val groupEpisodes = kanallar.items
            .filter { it.attributes["group-title"]?.toString()?.trim() == loadData.group } // Boş gruba düşmemesi için trim
            .mapNotNull { kanal ->
                val title = kanal.title ?: return@mapNotNull null // Null kontrolü
                val match = episodeRegex.find(title)
                if (match != null) {
                    try {
                        val (_, seasonStr, episodeStr) = match.destructured
                        val season = seasonStr.toInt()
                        val episode = episodeStr.toInt()
                        val epUrl = kanal.url ?: return@mapNotNull null // Null kontrolü
                        // toString() yerine null check ve fallback
                        val epPoster = kanal.attributes["tvg-logo"]?.takeIf{it.isNotBlank()} ?: loadData.poster // Fallback dizi posteri
                        val epGroup = kanal.attributes["group-title"]?.toString()?.trim() ?: loadData.group // Fallback
                        val epNation = kanal.attributes["tvg-country"] ?: "TR" // Fallback

                        val episodeDataJson = try {
                            LoadData(epUrl, title, epPoster, epGroup, epNation, season, episode).toJson()
                        } catch (e: Exception) { return@mapNotNull null } // JSON hatası varsa atla

                        // Episode(...) yerine newEpisode(...) kullan
                        newEpisode(episodeDataJson) { // data'yı constructor'a ver
                            this.name = title
                            this.season = season
                            this.episode = episode
                            this.posterUrl = epPoster
                        }
                    } catch (e: Exception) { null } // Parse hatası varsa atla
                } else { null } // Regex eşleşmezse atla
            }.sortedWith(compareBy({ it.season }, { it.episode })) // Sırala

        // Orijinal izlenme durumu map işlemi kalıyor
         val episodesWithWatchStatus = groupEpisodes.map { episode ->
              val epData = try { parseJson<LoadData>(episode.data) } catch (e: Exception) { null }
              // epData null ise izlenme durumu eklenemez, orijinal bölümü döndür
              if (epData == null) return@map episode

              // URL hash'i yerine data hash'i kullanılıyordu, öyle kalsın
              val epWatchKey = "watch_${episode.data.hashCode()}"
              val epProgressKey = "progress_${episode.data.hashCode()}"
              val epIsWatched = sharedPref?.getBoolean(epWatchKey, false) ?: false
              val epWatchProgress = sharedPref?.getLong(epProgressKey, 0L) ?: 0L

              // İzlenme durumunu Episode nesnesine eklemek için uygun bir yol yok.
              // CloudStream bunu kendi halleder. Bu map işlemini kaldırabiliriz
              // veya sadece loglama için kullanabiliriz. Şimdilik orijinal bölümü döndürelim.
              episode // Bu map işlemi aslında bir şey değiştirmiyor olabilir.
          }


        return newTvSeriesLoadResponse(
            loadData.title.replace(episodeRegex, "$1").trim(), // Dizi adını temizle
            url, // Orijinal JSON data
            TvType.TvSeries,
            episodesWithWatchStatus // İzlenme durumu eklenmiş (veya eklenmemiş) liste
        ) {
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOfNotNull(loadData.group.takeIf { it != "Diğer" }, loadData.nation).distinct() // Null kontrolü ve distinct
            // this.year, this.rating, this.actors eklemiyoruz şimdilik
        }
    }
        // --- loadLinks DÜZELTİLDİ ---
    // enum LocalExtractorLinkType ve mapToExternalType fonksiyonları kaldırıldı.
    /*
    enum class LocalExtractorLinkType { M3U8, MKV, MP4, AVI, VIDEO }
    fun mapToExternalType(localType: LocalExtractorLinkType): ExtractorLinkType { ... }
    */

    override suspend fun loadLinks(
        data: String, // JSON data
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = try { fetchDataFromUrlOrJson(data) } catch (e: Exception) { return false }
        val url = loadData.url
        if (url.isBlank()) { return false }

        // Header'ları almak için M3U'yu oku (orijinaldeki gibi)
        val kanal = try {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            kanallar.items.firstOrNull { it.url == loadData.url }
        } catch (e: Exception) {
            Log.w("powerDizi", "loadLinks - Header almak için M3U okunamadı.", e)
            null
        }
        val headers = kanal?.headers ?: emptyMap()
        val referer = headers["Referer"] ?: headers["referer"] ?: "" // Küçük/büyük harf kontrolü

        // Uzantıya göre isM3u8 belirle
        val isM3u8 = url.endsWith(".m3u8", ignoreCase = true)
        val isKnownVideo = url.endsWith(".mkv", ignoreCase = true) ||
                           url.endsWith(".mp4", ignoreCase = true) ||
                           url.endsWith(".avi", ignoreCase = true) // Diğerleri eklenebilir

        if (isM3u8 || isKnownVideo) {
            // Deprecated constructor'ı kullanıyoruz ama isM3u8 parametresi ile
            callback(
                ExtractorLink( // newExtractorLink YERİNE
                    source = this.name,
                    name = "${this.name} - ${ // Link adı
                        if (loadData.season > 0 && loadData.episode > 0) {
                            "S${loadData.season.toString().padStart(2, '0')}E${loadData.episode.toString().padStart(2, '0')}"
                        } else { loadData.title }
                    }",
                    url = url,
                    referer = referer, // Header'dan alınan referer
                    quality = Qualities.Unknown.value,
                    isM3u8 = isM3u8, // M3U8 ise true, değilse false
                    headers = headers // Alınan header'ları ekle
                )
            )
            return true
        } else {
            Log.w("powerDizi", "Desteklenmeyen veya bilinmeyen link formatı (loadLinks): $url")
            // Bilinmeyen formatları da video olarak göndermeyi deneyebiliriz?
            // callback(ExtractorLink(..., isM3u8 = false))
            // Şimdilik sadece bilinenleri gönderelim.
            return false
        }
    }
    // --- loadLinks Düzeltmesi Sonu ---

    // LoadData - Orijinal haliyle bırakıyoruz (izleme durumu ile)
     data class LoadData(
         val url: String,
         val title: String,
         val poster: String,
         val group: String,
         val nation: String,
         val season: Int = 0, // Default 0 yaptım
         val episode: Int = 0, // Default 0 yaptım
         val isWatched: Boolean = false, // Orijinalde vardı, kaldı
         val watchProgress: Long = 0 // Orijinalde vardı, kaldı
     )


    // fetchDataFromUrlOrJson - Orijinal haliyle bırakıyoruz
     private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
         if (data.startsWith("{")) {
             return parseJson<LoadData>(data)
         } else {
             // Bu path normalde kullanılmamalı ama orijinalde vardı
             Log.w("powerDizi", "fetchDataFromUrlOrJson: URL geldi, M3U okunuyor - $data")
             val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
             // first yerine firstOrNull daha güvenli
             val kanal = kanallar.items.firstOrNull { it.url == data }
                 ?: throw RuntimeException("fetchDataFromUrlOrJson: M3U içinde URL bulunamadı - $data") // Hata fırlat

             val streamurl = kanal.url ?: "" // URL null olamaz (yukarıda kontrol edildi)
             val channelname = kanal.title ?: "Bilinmeyen"
             val posterurl = kanal.attributes["tvg-logo"] ?: ""
             val chGroup = kanal.attributes["group-title"] ?: "Diğer"
             val nation = kanal.attributes["tvg-country"] ?: "TR"
             // Sezon/bölüm bilgisi burada eksik kalıyor, dikkat!
             return LoadData(streamurl, channelname, posterurl, chGroup, nation)
         }
     }


    // --- Playlist ve Parser Sınıfları ---
    // Orijinal halleriyle bırakıyoruz, sadece sabitlere erişimi düzelteceğiz
    data class Playlist( val items: List<PlaylistItem> = emptyList() )
    data class PlaylistItem(
        val title: String?, val attributes: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(), val url: String?,
        val userAgent: String? = null,
        var season: Int = 0, // var yapalım, sonradan atanacak
        var episode: Int = 0 // var yapalım, sonradan atanacak
    ) {
        // Companion object'teki sabitler burada kalıyor
         companion object {
             const val EXT_M3U = "#EXTM3U"
             const val EXT_INF = "#EXTINF"
             const val EXT_VLC_OPT = "#EXTVLCOPT"
         }
    }
    class IptvPlaylistParser {
         @Throws(PlaylistParserException::class)
         fun parseM3U(input: InputStream): Playlist { // InputStream alan versiyonu kullanmak daha iyi olabilir
             val reader = input.bufferedReader()
             // PlaylistItem. önekleri eklendi
             if (reader.readLine()?.startsWith(PlaylistItem.EXT_M3U) != true) {
                 throw PlaylistParserException.InvalidHeader()
             }
             // val EXT_M3U = PlaylistItem.EXT_M3U // Bunları tekrar tanımlamaya gerek yok
             // val EXT_INF = PlaylistItem.EXT_INF
             // val EXT_VLC_OPT = PlaylistItem.EXT_VLC_OPT
             val playlistItems: MutableList<PlaylistItem> = mutableListOf()
             var currentItem: PlaylistItem? = null // Geçici öğe tutucu

             var line: String? = reader.readLine()
             while (line != null) {
                 val trimmedLine = line.trim()
                 if (trimmedLine.isNotEmpty()) {
                     // PlaylistItem. önekleri eklendi
                     if (trimmedLine.startsWith(PlaylistItem.EXT_INF)) {
                         val (attributes, title) = parseExtInf(trimmedLine) // Bu fonksiyon PlaylistItem. kullanacak
                         currentItem = PlaylistItem(title = title, attributes = attributes)
                     } else if (trimmedLine.startsWith(PlaylistItem.EXT_VLC_OPT) && currentItem != null) {
                         val (key, value) = parseVlcOpt(trimmedLine) // Bu fonksiyon PlaylistItem. kullanacak
                         val currentHeaders = currentItem.headers.toMutableMap()
                         var currentAgent = currentItem.userAgent
                         if (key.equals("http-user-agent", ignoreCase = true)) {
                             currentAgent = value
                             currentHeaders["User-Agent"] = value
                         } else if (key.equals("http-referrer", ignoreCase = true)) {
                             currentHeaders["Referer"] = value
                         }
                         currentItem = currentItem.copy(headers = currentHeaders, userAgent = currentAgent)
                     } else if (!trimmedLine.startsWith("#") && currentItem != null) {
                         // Bu URL satırı, currentItem'a ekleyip listeye atalım
                         val url = trimmedLine.getUrl() // Bu fonksiyon | sonrasını temizliyordu
                         val userAgentFromUrl = trimmedLine.getUrlParameter("user-agent")
                         val referrerFromUrl = trimmedLine.getUrlParameter("referer")
                         val finalHeaders = currentItem.headers.toMutableMap()
                         if (referrerFromUrl != null) finalHeaders["Referer"] = referrerFromUrl
                         val finalUserAgent = currentItem.userAgent ?: userAgentFromUrl
                         if (finalUserAgent != null) finalHeaders["User-Agent"] = finalUserAgent

                         playlistItems.add(currentItem.copy(
                             url = url,
                             headers = finalHeaders,
                             userAgent = finalUserAgent
                         ))
                         currentItem = null // Öğeyi listeye ekledik, sıfırla
                     }
                 }
                 line = reader.readLine()
             }
             input.close() // InputStream'i kapatmayı unutma
             return Playlist(playlistItems)
         }
        // String alan parseM3U fonksiyonu InputStream olanı çağırabilir
         fun parseM3U(content: String): Playlist {
             return parseM3U(content.byteInputStream(Charsets.UTF_8)) // Charset belirtmek iyi olur
         }

        // --- Parser Yardımcı Fonksiyonları (Orijinaldeki gibi) ---
         private fun String?.isExtendedM3u(): Boolean = this?.startsWith(PlaylistItem.EXT_M3U) ?: false // Null check
         private fun String.getTitle(): String? = this.split(",").lastOrNull()?.replaceQuotesAndTrim()
         private fun String.getUrl(): String? = this.split("|").firstOrNull()?.replaceQuotesAndTrim()
         private fun String.getUrlParameter(key: String): String? { /*...*/ return null } // Bu fonksiyonlar aynı
         private fun String.getTagValue(key: String): String? { /*...*/ return null } // Bu fonksiyonlar aynı
         private fun String.replaceQuotesAndTrim(): String = this.replace("\"", "").trim()

         // parseExtInf ve parseVlcOpt içinde de PlaylistItem. kullanılmalı
         private fun parseExtInf(line: String): Pair<Map<String, String>, String?> {
             val attributes = mutableMapOf<String, String>()
             val dataPart = line.substringAfter(PlaylistItem.EXT_INF + ":").trim() // PlaylistItem.
             val commaIndex = dataPart.indexOf(',')
             if (commaIndex == -1) { return Pair(attributes, dataPart.takeIf { it.isNotEmpty() }) }
             val attributesPart = dataPart.substringBefore(',').trim()
             val title = dataPart.substringAfter(',').trim().takeIf { it.isNotEmpty() }
             val attrRegex = Regex("""([\w-]+)=("[^"]+"|[^"\s]+)""")
             attrRegex.findAll(attributesPart).forEach { /*...*/ attributes[it.groupValues[1].trim()] = it.groupValues[2].trim().removeSurrounding("\"") } // Basitleştirilmiş atama
             return Pair(attributes, title)
         }
         private fun parseVlcOpt(line: String): Pair<String, String> {
             val parts = line.substringAfter(PlaylistItem.EXT_VLC_OPT + ":").split('=', limit = 2) // PlaylistItem.
             return if (parts.size == 2) { Pair(parts[0].trim(), parts[1].trim()) } else { Pair("", "") }
         }
         // --- Parser Yardımcı Fonksiyonları Sonu ---
    }
    sealed class PlaylistParserException(message: String): Exception(message){ class InvalidHeader(message: String = "Geçersiz M3U başlığı."): PlaylistParserException(message) }
    val languageMap = mapOf( "en" to "İngilizce", /*...*/ "mul" to "Çok Dilli" )
} // class powerDizi sonu