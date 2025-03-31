package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import java.util.regex.Pattern

class powerDizi(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/refs/heads/site/lists/power-yabanci-dizi.m3u"
    override var name                 = "powerDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String,
        val season: Int?,
        val episode: Int?
    )

    data class PlaylistItem(
        val title: String?                  = null,
        val attributes: Map<String, String> = emptyMap(),
        val headers: Map<String, String>    = emptyMap(),
        val url: String?                    = null,
        val userAgent: String?              = null
    ) {
        companion object {
            const val EXT_M3U = "#EXTM3U"
            const val EXT_INF = "#EXTINF"
            const val EXT_VLC_OPT = "#EXTVLCOPT"
        }
    }

    data class Playlist(
        val items: List<PlaylistItem> = emptyList()
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = try {
             IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        } catch (e: Exception) {
            Log.e("powerDizi", "M3U dosyası alınamadı veya parse edilemedi: $mainUrl", e)
            return newHomePageResponse(listOf(HomePageList("Hata", emptyList())), hasNext = false)
        }

        val episodeRegex = Regex("(.*?)-?(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*", RegexOption.IGNORE_CASE)

        // Dizileri grupla, her diziden sadece bir örnek al (poster/bilgi için)
        val uniqueShows = kanallar.items
            .mapNotNull { item ->
                val group = item.attributes["group-title"]?.toString()?.trim()
                val poster = item.attributes["tvg-logo"]?.toString()
                val url = item.url?.toString()
                val title = item.title?.toString()?.trim() ?: ""
                val nation = item.attributes["tvg-country"]?.toString() ?: "TR"

                if (group.isNullOrBlank() || poster.isNullOrBlank() || url.isNullOrBlank()) {
                    null
                } else {
                    // Dizi adını gruptan al, başlıkta sezon/bölüm varsa temizlemeye *çalışma*
                    // Ana sayfada dizi adı + poster + grup + nation yeterli
                    val displayTitle = group // Ana sayfada grup adını (dizi adı) göster
                    val loadData = LoadData(
                        url = url, // İlk bölümün URL'si gibi davranabilir, load'da düzeltilecek
                        title = displayTitle,
                        poster = poster,
                        group = group,
                        nation = nation,
                        season = null, // Ana sayfada sez/bölüm gereksiz
                        episode = null
                    ).toJson()

                    Triple(group, displayTitle, newTvSeriesSearchResponse(displayTitle, loadData, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.lang = nation
                    })
                }
            }
            .distinctBy { it.first } // Aynı gruba (diziye) ait ilk örneği al

        // Group-title'a göre ana sayfa listeleri oluştur
        val homePageLists = uniqueShows
             .groupBy { it.first } // Dizi adına göre grupla (gerçi distinctBy sonrası tek eleman olmalı her grup)
             .map { (groupName, showTriples) ->
                HomePageList(
                    groupName ?: "Diğer", // Grup adı yoksa Diğer
                    showTriples.map { it.third }, // newTvSeriesSearchResponse listesi
                    isHorizontalImages = true
                )
             }

        return newHomePageResponse(homePageLists, hasNext = false)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = try {
             IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        } catch (e: Exception) {
             Log.e("powerDizi", "Arama için M3U alınamadı: $mainUrl", e)
             return emptyList()
        }
        val episodeRegex = Regex("(.*?)-?(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*", RegexOption.IGNORE_CASE)

        // Arama sorgusuyla eşleşen *dizileri* bul (grup adına göre)
        val matchingShows = kanallar.items
            .mapNotNull { item ->
                val group = item.attributes["group-title"]?.toString()?.trim()
                val poster = item.attributes["tvg-logo"]?.toString()
                val url = item.url?.toString() // İlk bölümün URL'si
                val nation = item.attributes["tvg-country"]?.toString() ?: "TR"

                if (group.isNullOrBlank() || url.isNullOrBlank() || poster.isNullOrBlank()) return@mapNotNull null

                // Dizi adı (group) sorguyla eşleşiyorsa
                if (group.contains(query, ignoreCase = true)) {
                     val loadData = LoadData(
                         url = url, // İlk bölümün URL'si
                         title = group, // Arama sonucu başlığı dizi adı olsun
                         poster = poster,
                         group = group,
                         nation = nation,
                         season = null,
                         episode = null
                     ).toJson()

                     newTvSeriesSearchResponse(group, loadData, TvType.TvSeries) {
                         this.posterUrl = poster
                         this.lang = nation
                     }
                } else {
                    null
                }
            }
            .distinctBy { it.name } // Aynı dizi adını tekrar listeleme

        return matchingShows
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


    override suspend fun load(url: String): LoadResponse {
        val loadData = try {
            fetchDataFromUrlOrJson(url)
        } catch (e: Exception) {
            Log.e("powerDizi", "fetchDataFromUrlOrJson hatası, data: $url", e)
            return newTvSeriesLoadResponse("Hata", url, TvType.TvSeries, emptyList()) {
                this.plot = "Dizi bilgileri yüklenirken hata oluştu."
            }
        }

        val kanallar = try {
            IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        } catch (e: Exception) {
            Log.e("powerDizi", "M3U dosyası alınamadı veya parse edilemedi: $mainUrl", e)
            return newTvSeriesLoadResponse(loadData.group, url, TvType.TvSeries, emptyList()) {
                this.posterUrl = loadData.poster
                this.plot = "Bölüm listesi yüklenirken hata oluştu."
            }
        }

        val episodeRegex = Regex("(.*?)-?(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*", RegexOption.IGNORE_CASE)

        val groupEpisodes = kanallar.items
            .filter { it.attributes["group-title"]?.toString()?.trim().equals(loadData.group.trim(), ignoreCase = true) }
            .mapNotNull { kanal ->
                val title = kanal.title?.toString()?.trim()
                val streamUrl = kanal.url?.toString()
                val poster = kanal.attributes["tvg-logo"]?.toString() ?: loadData.poster
                val groupTitle = kanal.attributes["group-title"]?.toString() ?: loadData.group
                val country = kanal.attributes["tvg-country"]?.toString() ?: loadData.nation

                if (title.isNullOrBlank() || streamUrl.isNullOrBlank()) {
                    return@mapNotNull null
                }

                val match = episodeRegex.find(title)
                if (match != null) {
                    val (_, seasonStr, episodeStr) = match.destructured
                    val season = seasonStr.toIntOrNull()
                    val episode = episodeStr.toIntOrNull()

                    if (season == null || episode == null) {
                        Log.w("powerDizi", "Sezon/Bölüm numarası alınamadı: '$title'")
                        return@mapNotNull null
                    }

                    val episodeSpecificDataJson = LoadData(
                        url = streamUrl,
                        title = title,
                        poster = poster,
                        group = groupTitle,
                        nation = country,
                        season = season,
                        episode = episode
                    ).toJson()

                    newEpisode(episodeSpecificDataJson) {
                        this.name = title
                        this.season = season
                        this.episode = episode
                        this.posterUrl = poster
                        this.runTime = null // Süre bilgisi M3U'da yoksa null veya 0L
                        this.description = null
                        this.rating = null
                        this.date = null
                    }
                } else {
                    Log.w("powerDizi", "Başlık regex ile eşleşmedi: '$title'")
                    null
                }
            }
            .sortedWith(compareBy({ it.season }, { it.episode }))

        return newTvSeriesLoadResponse(
            loadData.group,
            url,
            TvType.TvSeries,
            groupEpisodes
        ) {
            this.posterUrl = loadData.poster
            this.plot = "Grup: ${loadData.group}\nDil/Ülke: ${loadData.nation}"
            this.tags = listOfNotNull(loadData.group.takeIf { it.isNotEmpty() }, loadData.nation.takeIf { it.isNotEmpty() })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
       return try {
            val loadData = fetchDataFromUrlOrJson(data) // Bu data artık JSON olmalı
            Log.d("powerDizi", "loadLinks için LoadData: $loadData")

            // M3U'yu tekrar parse etmeye gerek yok, URL loadData içinde zaten var.
            // Ancak header bilgisi gerekiyorsa parse etmek gerekebilir.
            // Şimdilik header olmadığını varsayalım. Eğer gerekirse aşağıdaki blok açılabilir:
             /*
             val kanallar = try {
                 IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
             } catch (e: Exception) {
                 Log.e("powerDizi", "loadLinks içinde M3U parse edilemedi", e)
                 return false
             }
             val kanal = kanallar.items.firstOrNull { it.url == loadData.url }
             val headers = kanal?.headers ?: emptyMap()
             val referer = headers["referer"] ?: ""
             */

            // Eğer sezon/bölüm null ise veya 0 ise hata verelim (LoadData düzgün gelmemiş olabilir)
            if (loadData.season == null || loadData.episode == null || loadData.episode == 0) {
                 Log.e("powerDizi", "loadLinks geçersiz sezon/bölüm bilgisi aldı: $loadData")
                 return false
            }

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = "${loadData.group} S${loadData.season.toString().padStart(2, '0')}E${loadData.episode.toString().padStart(2, '0')}", // Daha standart link adı
                    url = loadData.url, // Bölümün stream URL'si
                    referer = "", // Şimdilik boş, gerekirse yukarıdaki bloktan alınmalı
                    quality = Qualities.Unknown.value,
                    isM3u8 = true, // URL'nin M3U8 olduğunu varsayıyoruz, değilse false yapın
                    headers = emptyMap() // Şimdilik boş, gerekirse yukarıdaki bloktan alınmalı
                )
            )
            true
       } catch (e: Exception) {
            Log.e("powerDizi", "loadLinks sırasında hata: Data: $data", e)
            false
       }
    }

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        return try {
            if (data.startsWith("{")) {
                AppUtils.parseJson<LoadData>(data)
            } else {
                Log.d("powerDizi", "fetchDataFromUrlOrJson: JSON olmayan data alındı, M3U'dan aranacak: $data")
                val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
                val kanal = kanallar.items.firstOrNull { it.url == data }
                if (kanal != null) {
                    val title = kanal.title ?: "Başlık Yok"
                    val poster = kanal.attributes["tvg-logo"] ?: ""
                    val group = kanal.attributes["group-title"] ?: "Bilinmiyor"
                    val nation = kanal.attributes["tvg-country"] ?: "TR"

                    val episodeRegex = Regex("(.*?)-?(\\d+)\\.\\s*Sezon\\s*(\\d+)\\.\\s*Bölüm.*", RegexOption.IGNORE_CASE)
                    val match = episodeRegex.find(title)
                    val season = match?.groupValues?.getOrNull(2)?.toIntOrNull()
                    val episode = match?.groupValues?.getOrNull(3)?.toIntOrNull()

                    LoadData(
                        url = data,
                        title = title,
                        poster = poster,
                        group = group,
                        nation = nation,
                        season = season,
                        episode = episode
                    )
                } else {
                    Log.e("powerDizi", "fetchDataFromUrlOrJson: M3U'da URL bulunamadı: $data")
                    LoadData(url = data, title = "Bulunamadı", poster = "", group = "Bilinmiyor", nation = "", season = null, episode = null)
                }
            }
        } catch (e: Exception) {
            Log.e("powerDizi", "fetchDataFromUrlOrJson sırasında hata: Data: $data", e)
            LoadData(url = data, title = "Hata", poster = "", group = "Hata", nation = "", season = null, episode = null)
        }
    }

    class IptvPlaylistParser {
        @Throws(PlaylistParserException::class)
        fun parseM3U(input: InputStream): Playlist {
            val reader = input.bufferedReader()

            if (!reader.readLine().isExtendedM3u()) {
                throw PlaylistParserException.InvalidHeader()
            }

            val EXT_INF = PlaylistItem.EXT_INF
            val EXT_VLC_OPT = PlaylistItem.EXT_VLC_OPT

            val playlistItems: MutableList<PlaylistItem> = mutableListOf()
            var currentItemBuilder: PlaylistItem? = null
            var currentHeaders = mutableMapOf<String, String>()
            var currentUserAgent: String? = null

            reader.forEachLine { line ->
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith(EXT_INF) -> {
                        currentItemBuilder?.let { builder ->
                             // Önceki item'ı bitir (URL'si bir sonraki satırda gelecek)
                             playlistItems.add(builder.copy(headers = currentHeaders, userAgent = currentUserAgent))
                        }
                        // Yeni item için başlangıç yap
                        val title = trimmedLine.getTitle()
                        val attributes = trimmedLine.getAttributes()
                        currentItemBuilder = PlaylistItem(title = title, attributes = attributes)
                        // Reset headers and user agent for the new item
                        currentHeaders = mutableMapOf()
                        currentUserAgent = null
                    }
                    trimmedLine.startsWith(EXT_VLC_OPT) -> {
                        val userAgent = trimmedLine.getTagValue("http-user-agent")
                        val referrer = trimmedLine.getTagValue("http-referrer")
                        if (userAgent != null) currentUserAgent = userAgent
                        if (referrer != null) currentHeaders["referer"] = referrer // Use referer spelling
                    }
                    trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
                        // Bu satır URL olmalı
                        currentItemBuilder?.let { builder ->
                            val url = trimmedLine.getUrl()
                            val headerUserAgent = trimmedLine.getUrlParameter("user-agent")
                            val headerReferrer = trimmedLine.getUrlParameter("referer")

                            if (headerUserAgent != null) currentUserAgent = headerUserAgent
                            if (headerReferrer != null) currentHeaders["referer"] = headerReferrer

                            playlistItems.add(builder.copy(
                                url = url,
                                headers = currentHeaders,
                                userAgent = currentUserAgent
                            ))
                            currentItemBuilder = null // Item tamamlandı
                        }
                    }
                }
            }
             // Dosya sonundaki son item'ı ekle (eğer varsa)
             currentItemBuilder?.let { builder ->
                  // URL'si olmayan item olmamalı ama yine de kontrol edelim
                  if (builder.url != null) {
                       playlistItems.add(builder.copy(headers = currentHeaders, userAgent = currentUserAgent))
                  }
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
             // Basic URL extraction, assumes URL is the whole line if not EXT_INF etc.
             return this.trim()
         }

        private fun String.getUrlParameter(key: String): String? {
            // This basic parser assumes URL parameters are NOT appended with |
            // If they are, the logic from the original parser is needed here.
            return null // Simplified: Assume no URL params appended with |
        }


        private fun String.getTagValue(key: String): String? {
             // Example: "#EXTVLCOPT:http-user-agent=MyAgent"
             val prefix = "#EXTVLCOPT:$key="
             return if (this.startsWith(prefix)) {
                 this.substring(prefix.length).replaceQuotesAndTrim()
             } else {
                 null
             }
         }

        private fun String.getAttributes(): Map<String, String> {
            // Example: #EXTINF:-1 tvg-id="id" tvg-name="name" tvg-logo="logo", Title
            val extInfRegex = Regex("""^#EXTINF:\s*(-?\d+)\s*,(.*)""")
            val matchResult = extInfRegex.find(this)
            if (matchResult == null || matchResult.groupValues.size < 3) return emptyMap()

            val attributesString = matchResult.groupValues[1] // Attributes are *before* the last comma now
            val titlePart = matchResult.groupValues[2] // Title is after the last comma (already handled by getTitle)

            val attributes = mutableMapOf<String, String>()
            val attrRegex = Regex("""([\w-]+)=["']?([^"']*)["']?""") // Improved regex for attributes

            attrRegex.findAll(attributesString).forEach { attrMatch ->
                 if (attrMatch.groupValues.size >= 3) {
                     val key = attrMatch.groupValues[1]
                     val value = attrMatch.groupValues[2]
                     attributes[key] = value // Keep original quoting/spacing for now if needed later
                 }
            }
            return attributes
        }
    }

    sealed class PlaylistParserException(message: String) : Exception(message) {
        class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
    }
}