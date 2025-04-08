package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import com.sinetech.latte.BuildConfig // TMDB API anahtarı için
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.math.minOf // Oyuncu listesi için

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerboard Dizi 🎬"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true // İndirme desteği ekledik varsayalım
    override val supportedTypes       = setOf(TvType.TvSeries)

    // M3U içeriğini ve parse edilmiş listeyi lazy olarak yükle
    private val parsedPlaylist: Playlist by lazy {
        Log.d("powerDizi", "Lazy: M3U okunuyor ve parse ediliyor...")
        try {
            val m3uText = app.get(mainUrl).text
            IptvPlaylistParser().parseM3U(m3uText).also {
                 Log.d("powerDizi", "Lazy: Parse tamamlandı. Öğe sayısı: ${it.items.size}")
            }
        } catch (e: Exception) {
            Log.e("powerDizi", "Lazy: M3U okuma/parse hatası!", e)
            Playlist() // Hata durumunda boş liste dön
        }
    }

    // Regex'i sınıf seviyesinde tanımlayalım, tekrar tekrar oluşturmaya gerek yok
    private val episodeRegex = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val allItems = parsedPlaylist.items
        if (allItems.isEmpty()) {
             Log.w("powerDizi", "getMainPage: Parse edilmiş öğe yok veya M3U yüklenemedi.")
             return newHomePageResponse(emptyList(), false)
        }

        Log.d("powerDizi", "getMainPage işleniyor. Toplam öğe: ${allItems.size}")

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
                         episode = episodeStr.toInt(),
                         // Gerekirse varsayılan atribütleri burada da ayarlayabilirsin,
                         // ama parser'ın bunu yapması daha mantıklı.
                         // attributes = item.attributes.toMutableMap().apply { ... }
                     )
                 } catch (e: NumberFormatException) {
                     Log.w("powerDizi", "getMainPage: Sezon/Bölüm parse hatası: $title")
                     item // Parse edilemese de öğeyi koru, belki filmdir? Veya null dönüp atla.
                 }
             } else {
                 item // Regex eşleşmezse orijinal öğeyi kullan
             }
         }

        val groupedShows = processedItems.groupBy {
             // group-title'ı null veya boş ise "Diğer" grubuna ata
             it.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
        }

        val homePageLists = mutableListOf<HomePageList>()

        groupedShows.forEach { (group, shows) ->
            // Her gruptan sadece ilk bölümü (veya bir temsilciyi) alıp SearchResponse oluşturmak
            // genellikle daha doğrudur. Burada tüm bölümleri listeliyoruz, bu da CloudStream'de
            // ana sayfada her bölümün ayrı bir kart olarak görünmesine neden olabilir.
            // Eğer dizi bazlı gruplama isteniyorsa, mantığı değiştirmek gerekir.
            // Şimdilik mevcut mantıkla devam edelim:
            val searchResponses = shows.mapNotNull { kanal ->
                val streamurl = kanal.url ?: return@mapNotNull null // URL yoksa atla
                val channelname = kanal.title ?: "İsimsiz Bölüm"
                val posterurl = kanal.attributes["tvg-logo"] ?: ""
                val nation = kanal.attributes["tvg-country"] ?: "TR"

                val loadDataJson = try {
                    LoadData(
                        streamurl,
                        channelname,
                        posterurl,
                        group,
                        nation,
                        kanal.season,
                        kanal.episode
                    ).toJson()
                } catch (e: Exception) {
                     Log.e("powerDizi", "getMainPage - LoadData JSON hatası: ${kanal.title}", e)
                     null
                }

                if (loadDataJson != null) {
                    newTvSeriesSearchResponse(
                        channelname, // Ana sayfada görünecek isim
                        loadDataJson, // Tıklanınca load'a gönderilecek veri
                        TvType.TvSeries
                    ) {
                        this.posterUrl = posterurl
                        // SearchResponse'da ek bilgi (lang vb.) ayarlamaya gerek yok
                    }
                } else {
                    null
                }
            }

            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList(group, searchResponses.distinctBy { it.url }, isHorizontalImages = true)) // Tekrarları engelle
            }
        }

        Log.d("powerDizi", "getMainPage tamamlandı. Grup sayısı: ${homePageLists.size}")
        return newHomePageResponse(
            homePageLists,
            hasNext = false
        )
    }
        override suspend fun search(query: String): List<SearchResponse> {
        val allItems = parsedPlaylist.items
        Log.d("powerDizi", "Arama yapılıyor: '$query'. Toplam öğe: ${allItems.size}")

        return allItems.filter {
            (it.title ?: "").contains(query, ignoreCase = true) ||
            (it.attributes["group-title"] ?: "").contains(query, ignoreCase = true)
        }.mapNotNull { kanal ->
             val streamurl = kanal.url ?: return@mapNotNull null
             val channelname = kanal.title ?: "İsimsiz Bölüm"
             val posterurl = kanal.attributes["tvg-logo"] ?: ""
             val chGroup = kanal.attributes["group-title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer"
             val nation = kanal.attributes["tvg-country"] ?: "TR"

             var seasonNum = 1
             var episodeNum = 0
             episodeRegex.find(channelname)?.let { match ->
                 try {
                     seasonNum = match.destructured.component2().toInt()
                     episodeNum = match.destructured.component3().toInt()
                 } catch (e: NumberFormatException) { /* Hata loglandı */ }
             }

             val loadDataJson = try {
                 LoadData(streamurl, channelname, posterurl, chGroup, nation, seasonNum, episodeNum).toJson()
             } catch (e: Exception) {
                  Log.e("powerDizi", "Arama - LoadData JSON hatası: ${kanal.title}", e)
                  null
             }

             if (loadDataJson != null) {
                 newTvSeriesSearchResponse(
                     channelname,
                     loadDataJson,
                     TvType.TvSeries
                 ) {
                     this.posterUrl = posterurl
                 }
             } else {
                 null
             }
        }.distinctBy { it.url } // URL bazında tekrarları kaldır
         .also {
             Log.d("powerDizi", "Arama sonucu: ${it.size} öğe bulundu.")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private suspend fun fetchTMDBData(title: String): JSONObject? {
        // Bu fonksiyon önceki haliyle iyi görünüyor, aynen bırakabiliriz.
        // Sadece API anahtarının BuildConfig içinde doğru tanımlandığından emin ol.
        return withContext(Dispatchers.IO) {
            try {
                // BuildConfig.TMDB_SECRET_API doğru şekilde ayarlandıysa çalışır.
                // Gizli API anahtarını doğrudan koda yazmaktan kaçının.
                val apiKey = try {
                     BuildConfig.TMDB_SECRET_API.trim('"')
                 } catch (e: Exception) {
                      Log.e("TMDB", "BuildConfig.TMDB_SECRET_API bulunamadı veya okunamadı.", e)
                      "" // Hata durumunda boş anahtar
                 }

                if (apiKey.isEmpty()) {
                    Log.e("TMDB", "TMDB API anahtarı boş veya bulunamadı.")
                    return@withContext null
                }

                // Başlıktaki parantez içi ifadeleri temizle
                val cleanedTitle = title.replace(Regex("\\([^)]*\\)"), "").trim()
                if (cleanedTitle.isEmpty()) return@withContext null // Temizlenmiş başlık boşsa arama yapma

                val encodedTitle = URLEncoder.encode(cleanedTitle, "UTF-8")
                val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedTitle&language=tr-TR"

                val response = URL(searchUrl).readText()
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.optJSONArray("results") // optJSONArray null dönebilir

                if (results != null && results.length() > 0) {
                    val tvId = results.getJSONObject(0).optInt("id", -1) // optInt kullan
                    if (tvId == -1) return@withContext null // ID alınamadıysa devam etme

                    val detailsUrl = "https://api.themoviedb.org/3/tv/$tvId?api_key=$apiKey&append_to_response=credits&language=tr-TR"
                    val detailsResponse = URL(detailsUrl).readText()
                    return@withContext JSONObject(detailsResponse)
                }
                null
            } catch (e: Exception) {
                Log.e("TMDB", "TMDB veri çekme hatası: $title", e) // Hangi başlıkta hata olduğunu logla
                null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // url burada tıklanan öğenin LoadData JSON'u olmalı
        Log.d("powerDizi", "load çağrıldı: $url")
        val allItems = parsedPlaylist.items

        val loadData = try {
             fetchDataFromUrlOrJson(url) // Bu artık sadece JSON parse etmeli
        } catch (e: Exception) {
             Log.e("powerDizi", "load - fetchDataFromUrlOrJson hatası: $url", e)
             return newTvSeriesLoadResponse("Dizi Yüklenemedi", url, TvType.TvSeries, emptyList()) {}
        }

        // Dizi adını TMDB araması için temizle
        val seriesTitleForTMDB = loadData.title.replace(episodeRegex, "$1").trim()

        val tmdbData = if (seriesTitleForTMDB.isNotEmpty()) fetchTMDBData(seriesTitleForTMDB) else null

        val plot = buildString {
             if (tmdbData != null) {
                 // TMDB verilerini ayrıştır ve plot'a ekle (önceki kod gibi)
                 val overview = tmdbData.optString("overview", "")
                 val firstAirDate = tmdbData.optString("first_air_date", "").split("-").firstOrNull() ?: ""
                 val ratingValue = tmdbData.optDouble("vote_average", -1.0)
                 val rating = if (ratingValue >= 0) String.format("%.1f", ratingValue) else null
                 val tagline = tmdbData.optString("tagline", "")
                 val originalName = tmdbData.optString("original_name", "")
                 val originalLanguage = tmdbData.optString("original_language", "")
                 val numberOfSeasons = tmdbData.optInt("number_of_seasons", 0) // 0 daha mantıklı default olabilir

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
                         for (i in 0 until minOf(castArray.length(), 10)) { // minOf ekli
                             castList.add(castArray.optJSONObject(i)?.optString("name") ?: "")
                         }
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
                 if (numberOfSeasons > 0) append("📅 <b>Toplam Sezon:</b> $numberOfSeasons<br>") // > 0 kontrolü
                 if (genreList.isNotEmpty()) append("🎭 <b>Dizi Türü:</b> ${genreList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 if (castList.isNotEmpty()) append("👥 <b>Oyuncular:</b> ${castList.filter { it.isNotEmpty() }.joinToString(", ")}<br>")
                 append("<br>") // Ayraç
             } else {
                 append("TMDB bilgisi bulunamadı.<br><br>")
             }

             // Grup ve ülke bilgisini ekle
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
            .mapNotNull { kanal ->
                val title = kanal.title ?: return@mapNotNull null
                val match = episodeRegex.find(title)
                if (match != null) {
                    try {
                        val (_, seasonStr, episodeStr) = match.destructured
                        val season = seasonStr.toInt()
                        val episode = episodeStr.toInt()
                        val epUrl = kanal.url ?: return@mapNotNull null
                        val epPoster = kanal.attributes["tvg-logo"] ?: loadData.poster // Bölüm posteri yoksa dizi posterini kullan
                        val epGroup = kanal.attributes["group-title"]?.toString()?.trim() ?: loadData.group
                        val epNation = kanal.attributes["tvg-country"] ?: loadData.nation

                         val episodeDataJson = LoadData(
                             epUrl,
                             title, // Bölümün tam başlığı
                             epPoster,
                             epGroup,
                             epNation,
                             season,
                             episode
                         ).toJson()

                        Episode( // CloudStream'in Episode sınıfı
                            data = episodeDataJson,
                            name = title, // Bölüm adı (Örn: Game of Thrones - 1. Sezon 1. Bölüm)
                            season = season,
                            episode = episode,
                            posterUrl = epPoster
                            // description = ... // TMDB'den bölüm özeti alınabilir (daha karmaşık)
                        )
                    } catch (e: Exception) {
                         Log.e("powerDizi", "load - Bölüm parse/oluşturma hatası: $title", e)
                         null
                    }
                } else {
                    // Regex eşleşmiyorsa, bunu belki film gibi kabul edebiliriz?
                    // Şimdilik sadece dizi bölümlerini alıyoruz.
                    // Log.w("powerDizi", "load - Bölüm başlığı regex ile eşleşmedi: $title")
                    null
                }
            }.sortedWith(compareBy({ it.season }, { it.episode })) // Sırala

        Log.d("powerDizi", "load - Grup için ${groupEpisodes.size} bölüm bulundu: ${loadData.group}")

        return newTvSeriesLoadResponse(
            seriesTitleForTMDB.takeIf { it.isNotEmpty() } ?: loadData.group, // Dizi Adı
            url, // Orijinal JSON verisi (veya dizi için benzersiz bir ID)
            TvType.TvSeries,
            groupEpisodes // Bulunan ve sıralanan bölümler
        ) {
            // Diziye ait genel bilgiler
            this.posterUrl = loadData.poster
            this.plot = plot
            this.tags = listOfNotNull(loadData.group.takeIf { it != "Diğer" }, loadData.nation).distinct()
            // TMDB'den ek bilgiler eklenebilir
            tmdbData?.optString("first_air_date")?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()?.let {
                this.year = it
            }
            tmdbData?.optDouble("vote_average")?.let {
                this.rating = DecimalRating( (it * 10).toInt() , 1000) // Örn: 7.8 -> 78
            }
            tmdbData?.optJSONObject("credits")?.optJSONArray("cast")?.let { castArray ->
                 val actors = mutableListOf<ActorData>()
                 for (i in 0 until minOf(castArray.length(), 15)) { // İlk 15 oyuncu
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
                 this.actors = actors // addActors yerine doğrudan atama
            }
            // Öneriler eklenebilir (Benzer diziler)
            // this.recommendations = ...
        }
    }
        override suspend fun loadLinks(
        data: String, // JSON data from Episode.data
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit // Callback returns Unit
    ): Boolean { // Function returns Boolean
        val loadData = try {
            fetchDataFromUrlOrJson(data)
        } catch (e: Exception) {
            Log.e("powerDizi", "loadLinks JSON parse hatası: $data", e)
            return false // Error loading data
        }

        Log.d("powerDizi", "loadLinks için LoadData: $loadData")
        val url = loadData.url

        if (url.isBlank()) {
             Log.w("powerDizi", "loadLinks - Geçersiz URL: $url")
             return false // Invalid URL
        }

        val isM3u8 = url.endsWith(".m3u8", ignoreCase = true)
        val isMkv = url.endsWith(".mkv", ignoreCase = true)
        val isMp4 = url.endsWith(".mp4", ignoreCase = true)
        val isAvi = url.endsWith(".avi", ignoreCase = true)
        // Diğer video formatlarını da buraya ekleyebilirsin (örn: .ts, .mov)

        if (isM3u8 || isMkv || isMp4 || isAvi) {
            callback(
                newExtractorLink( // Use newExtractorLink
                    source = this.name, // Eklenti adı daha iyi
                    name = "${this.name} - ${ // Daha açıklayıcı link adı
                        if (loadData.season > 0 && loadData.episode > 0) {
                            "S${loadData.season.toString().padStart(2, '0')}E${loadData.episode.toString().padStart(2, '0')}"
                        } else {
                            loadData.title // Sezon/bölüm yoksa başlık
                        }
                    }",
                    url = url,
                    referer = "", // Referer gerekiyorsa LoadData'ya eklenmeli
                    quality = Qualities.Unknown.value, // Varsa M3U'dan kalite bilgisi alınabilir
                    isM3u8 = isM3u8 // M3U8 ise true, diğerleri (MKV, MP4, AVI) için false
                    // headers = headers // Gerekirse LoadData'dan header bilgisi alınmalı
                )
            )
            return true // Link başarıyla gönderildi
        } else {
            Log.w("powerDizi", "Desteklenmeyen link formatı: $url")
            return false // Bu format için link oluşturulamadı
        }
    }

    // Veri taşıma sınıfı (önceki haliyle iyi)
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int = 0, // 0 varsayılan olabilir, 1 yerine
        val episode: Int = 0,
        // İzlenme durumu LoadData içinde tutulmamalı
        // val isWatched: Boolean = false,
        // val watchProgress: Long = 0
        // Gerekirse header eklenebilir: val headers: Map<String, String> = emptyMap()
    )

    // Sadece JSON parse eden fonksiyon
    private fun fetchDataFromUrlOrJson(data: String): LoadData {
         if (data.startsWith("{")) {
             return try {
                 parseJson<LoadData>(data)
             } catch (e: Exception) {
                  Log.e("powerDizi", "fetchData - JSON parse hatası: $data", e)
                  // Hata durumunda boş LoadData döndür, üst katman kontrol etsin
                  LoadData("", "HATA", "", "", "")
             }
         } else {
             // Bu path artık çağrılmamalı. Güvenlik için logla.
             Log.e("powerDizi", "fetchData - JSON bekleniyordu ama URL veya başka bir şey geldi: $data")
             // Hata durumunda boş LoadData döndür
             return LoadData("", "HATA", "", "", "")
         }
    }


    // --- Playlist ve Parser Sınıfları ---
    // Bu kısımlar genellikle ayrı bir dosyada tutulur ama burada kalabilir.
    // Parser'ı daha sağlam hale getirmek için iyileştirmeler yapılabilir.

    data class Playlist(
        val items: List<PlaylistItem> = emptyList()
    )

    data class PlaylistItem(
        val title: String?,
        val attributes: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(), // Header bilgisi eklendi
        val url: String?,
        val userAgent: String? = null, // User-Agent ayrı tutulabilir
        var season: Int = 0, // var olarak değiştirildi, sonradan atanacak
        var episode: Int = 0 // var olarak değiştirildi, sonradan atanacak
    ) {
        companion object {
            const val EXT_M3U = "#EXTM3U"
            const val EXT_INF = "#EXTINF"
            const val EXT_VLC_OPT = "#EXTVLCOPT"
            const val EXT_KEY = "#EXT-X-KEY" // Şifreli yayınlar için (opsiyonel)
            const val USER_AGENT = "#EXTM3U url-tvg=\"\" x-tvg-url=\"\" tvg-shift=0 group-title=\"\"" // User-Agent için M3U başlığı (varsayım)
        }
    }

    class IptvPlaylistParser {
        // Basit ve hızlı M3U parser, daha kapsamlı kütüphaneler de kullanılabilir.
        @Throws(PlaylistParserException::class)
        fun parseM3U(content: String): Playlist {
            val lines = content.lines()
            if (lines.isEmpty() || !lines[0].startsWith(PlaylistItem.EXT_M3U)) {
                throw PlaylistParserException.InvalidHeader()
            }

            val playlistItems = mutableListOf<PlaylistItem>()
            var currentTitle: String? = null
            var currentAttributes = mutableMapOf<String, String>()
            var currentHeaders = mutableMapOf<String, String>()
            var currentUserAgent: String? = null

            // Global User-Agent'ı başlık satırından almaya çalış (opsiyonel)
            // val globalUserAgentLine = lines.firstOrNull { it.contains("user-agent", ignoreCase = true) }
            // val globalUserAgent = globalUserAgentLine?.let { extractUserAgentFromHeader(it) }

            for (line in lines) {
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith(PlaylistItem.EXT_INF) -> {
                        // Önceki öğe için bilgileri sıfırla (URL'den önce gelmeli)
                        currentTitle = null
                        currentAttributes = mutableMapOf()
                        currentHeaders = mutableMapOf()
                        currentUserAgent = null // Her öğe için sıfırla

                        try {
                            val (attrs, title) = parseExtInf(trimmedLine)
                            currentTitle = title
                            currentAttributes.putAll(attrs)
                        } catch (e: Exception) {
                            Log.e("IptvPlaylistParser", "EXTINF parse hatası: $trimmedLine", e)
                        }
                    }
                    trimmedLine.startsWith(PlaylistItem.EXT_VLC_OPT) -> {
                        // VLC seçeneklerini parse et (örn: http-user-agent, http-referrer)
                        val (key, value) = parseVlcOpt(trimmedLine)
                        if (key.equals("http-user-agent", ignoreCase = true)) {
                            currentUserAgent = value
                            currentHeaders["User-Agent"] = value // Header'a da ekle
                        } else if (key.equals("http-referrer", ignoreCase = true)) {
                            currentHeaders["Referer"] = value
                        }
                        // Diğer VLC opsiyonları eklenebilir
                    }
                    // Başka #EXT ile başlayan etiketler eklenebilir (örn: #EXTGRP)

                    !trimmedLine.startsWith("#") && trimmedLine.isNotEmpty() -> {
                        // Bu satır URL olmalı
                        val url = trimmedLine

                        // URL'den User-Agent veya Referer parse etme (artık EXT_VLC_OPT'den gelmeli)
                        // val urlUserAgent = extractUrlParameter(url, "user-agent")
                        // val urlReferrer = extractUrlParameter(url, "referer")
                        // val finalUserAgent = currentUserAgent ?: urlUserAgent ?: globalUserAgent
                        // if (urlReferrer != null) currentHeaders["Referer"] = urlReferrer
                        // if (finalUserAgent != null) currentHeaders["User-Agent"] = finalUserAgent

                         // Başlık yoksa URL'yi başlık yap veya "İsimsiz" de
                         val finalTitle = currentTitle ?: url.substringAfterLast('/').substringBefore('?')

                        // group-title yoksa, başlıktan türetmeye çalış (son çare)
                        if (!currentAttributes.containsKey("group-title") || currentAttributes["group-title"].isNullOrBlank()) {
                            val episodeRegexLocal = Regex("(.*?)-(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*") // Yerel Regex
                            val match = episodeRegexLocal.find(finalTitle)
                            val groupFromTitle = match?.destructured?.component1()?.trim()
                            if (!groupFromTitle.isNullOrBlank()) {
                                currentAttributes["group-title"] = groupFromTitle
                            } else {
                                // Başlıktan da grup çıkarılamıyorsa "Diğer" ata
                                currentAttributes["group-title"] = "Diğer"
                            }
                        }

                         // tvg-country/language yoksa varsayılan ata
                         if (!currentAttributes.containsKey("tvg-country")) currentAttributes["tvg-country"] = "TR"
                         if (!currentAttributes.containsKey("tvg-language")) currentAttributes["tvg-language"] = "TR"


                        playlistItems.add(
                            PlaylistItem(
                                title = finalTitle,
                                attributes = currentAttributes.toMap(), // Kopyasını al
                                headers = currentHeaders.toMap(), // Kopyasını al
                                url = url,
                                userAgent = currentUserAgent
                            )
                        )
                        // Sonraki öğe için bilgileri sıfırla
                        currentTitle = null
                        currentAttributes = mutableMapOf()
                        currentHeaders = mutableMapOf()
                        currentUserAgent = null
                    }
                }
            }
            return Playlist(playlistItems)
        }

        private fun parseExtInf(line: String): Pair<Map<String, String>, String?> {
            val attributes = mutableMapOf<String, String>()
            // #EXTINF: den sonraki kısmı al, ilk virgül ayırıcıdır
            val dataPart = line.substringAfter(PlaylistItem.EXT_INF + ":").trim()
            val commaIndex = dataPart.indexOf(',')

            if (commaIndex == -1) { // Virgül yoksa, sadece başlık vardır (veya süre)
                return Pair(attributes, dataPart.takeIf { it.isNotEmpty() })
            }

            val attributesPart = dataPart.substringBefore(',').trim()
            val title = dataPart.substringAfter(',').trim().takeIf { it.isNotEmpty() }

            // Süreyi atla veya istersen `duration` olarak sakla
            // val duration = attributesPart.substringBefore(' ').toDoubleOrNull() ?: -1.0

            // Etiketleri parse et (key="value" veya key=value formatı)
            val attrRegex = Regex("""([\w-]+)=("[^"]+"|[^"\s]+)""")
            attrRegex.findAll(attributesPart).forEach { matchResult ->
                val key = matchResult.groupValues[1].trim()
                var value = matchResult.groupValues[2].trim()
                // Tırnakları temizle
                if (value.startsWith('"') && value.endsWith('"')) {
                    value = value.substring(1, value.length - 1)
                }
                attributes[key] = value
            }
            return Pair(attributes, title)
        }

        private fun parseVlcOpt(line: String): Pair<String, String> {
            // #EXTVLCOPT:key=value formatı
            val parts = line.substringAfter(PlaylistItem.EXT_VLC_OPT + ":").split('=', limit = 2)
            return if (parts.size == 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                Pair(parts.getOrElse(0) { "" }.trim(), "") // Hatalı format
            }
        }
    }

    sealed class PlaylistParserException(message: String) : Exception(message) {
        class InvalidHeader : PlaylistParserException("Geçersiz M3U başlığı. #EXTM3U ile başlamıyor.")
    }

    // Dil haritası (önceki haliyle iyi)
    val languageMap = mapOf(
        "en" to "İngilizce", "tr" to "Türkçe", "ja" to "Japonca", "de" to "Almanca",
        "fr" to "Fransızca", "es" to "İspanyolca", "it" to "İtalyanca", "ru" to "Rusça",
        "pt" to "Portekizce", "ko" to "Korece", "zh" to "Çince", "hi" to "Hintçe",
        "ar" to "Arapça", "nl" to "Felemenkçe", "sv" to "İsveççe", "no" to "Norveççe",
        "da" to "Danca", "fi" to "Fince", "pl" to "Lehçe", "cs" to "Çekçe",
        "hu" to "Macarca", "ro" to "Rumence", "el" to "Yunanca", "uk" to "Ukraynaca",
        "bg" to "Bulgarca", "sr" to "Sırpça", "hr" to "Hırvatça", "sk" to "Slovakça",
        "sl" to "Slovence", "th" to "Tayca", "vi" to "Vietnamca", "id" to "Endonezce",
        "ms" to "Malayca", "tl" to "Tagalogca", "fa" to "Farsça", "he" to "İbranice",
        "la" to "Latince", "xx" to "Belirsiz", "mul" to "Çok Dilli"
    )

} // class powerDizi sonu
