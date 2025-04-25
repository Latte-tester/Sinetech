package com.sinetech.latte

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors // addActors aslında ActorData bekler
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
// kotlinx.coroutines importları
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
// Diğer importlar
import org.jsoup.nodes.Document

class WebDramaTurkey : MainAPI() {
    override var mainUrl              = "https://webdramaturkey.org"
    override var name                 = "WebDramaTurkey「🍿🎥」"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Podcast, TvType.AsianDrama, TvType.Anime)

    // --- mainPage (Değişiklik Yok) ---
    override val mainPage = mainPageOf(
    "${mainUrl}/programlar"                                                   to "Programlar",
    "${mainUrl}/animeler"                                                     to "Animeler",
    "${mainUrl}/filmler?filter={\"category\":\"36\",\"sorting\":\"newest\"}"  to "Aile Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"36\",\"sorting\":\"newest\"}"  to "Aile Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"17\",\"sorting\":\"newest\"}"  to "Aksiyon Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"17\",\"sorting\":\"newest\"}"  to "Aksiyon Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"34\",\"sorting\":\"newest\"}"  to "Belgesel Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"34\",\"sorting\":\"newest\"}"  to "Belgesel Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"19\",\"sorting\":\"newest\"}"  to "Bilim Kurgu Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"19\",\"sorting\":\"newest\"}"  to "Bilim Kurgu Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"13\",\"sorting\":\"newest\"}"  to "BL Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"13\",\"sorting\":\"newest\"}"  to "BL Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"37\",\"sorting\":\"newest\"}"  to "Bromance Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"37\",\"sorting\":\"newest\"}"  to "Bromance Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"6\",\"sorting\":\"newest\"}"   to "Dram Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"6\",\"sorting\":\"newest\"}"   to "Dram Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"22\",\"sorting\":\"newest\"}"  to "Eğlence Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"22\",\"sorting\":\"newest\"}"  to "Eğlence Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"5\",\"sorting\":\"newest\"}"   to "Fantastik Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"5\",\"sorting\":\"newest\"}"   to "Fantastik Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"14\",\"sorting\":\"newest\"}"  to "Gençlik Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"14\",\"sorting\":\"newest\"}"  to "Gençlik Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"8\",\"sorting\":\"newest\"}"   to "Gerilim Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"8\",\"sorting\":\"newest\"}"   to "Gerilim Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"25\",\"sorting\":\"newest\"}"  to "Gezi Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"25\",\"sorting\":\"newest\"}"  to "Gezi Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"11\",\"sorting\":\"newest\"}"  to "Gizem Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"11\",\"sorting\":\"newest\"}"  to "Gizem Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"35\",\"sorting\":\"newest\"}"  to "GL Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"35\",\"sorting\":\"newest\"}"  to "GL Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"24\",\"sorting\":\"newest\"}"  to "İzdivaç Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"24\",\"sorting\":\"newest\"}"  to "İzdivaç Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"9\",\"sorting\":\"newest\"}"   to "Komedi Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"9\",\"sorting\":\"newest\"}"   to "Komedi Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"7\",\"sorting\":\"newest\"}"   to "Korku Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"7\",\"sorting\":\"newest\"}"   to "Korku Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"1\",\"sorting\":\"newest\"}"   to "Lise Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"1\",\"sorting\":\"newest\"}"   to "Lise Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"10\",\"sorting\":\"newest\"}"  to "Müzik Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"10\",\"sorting\":\"newest\"}"  to "Müzik Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"3\",\"sorting\":\"newest\"}"   to "Ofis Aşkı Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"3\",\"sorting\":\"newest\"}"   to "Ofis Aşkı Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"28\",\"sorting\":\"newest\"}"  to "Reality Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"28\",\"sorting\":\"newest\"}"  to "Reality Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"4\",\"sorting\":\"newest\"}"   to "Romantik Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"4\",\"sorting\":\"newest\"}"   to "Romantik Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"18\",\"sorting\":\"newest\"}"  to "Tarihi Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"18\",\"sorting\":\"newest\"}"  to "Tarihi Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"26\",\"sorting\":\"newest\"}"  to "Tartışma Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"26\",\"sorting\":\"newest\"}"  to "Tartışma Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"2\",\"sorting\":\"newest\"}"   to "Üniversite Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"2\",\"sorting\":\"newest\"}"   to "Üniversite Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"27\",\"sorting\":\"newest\"}"  to "Varyete Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"27\",\"sorting\":\"newest\"}"  to "Varyete Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"15\",\"sorting\":\"newest\"}"  to "Web Drama Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"15\",\"sorting\":\"newest\"}"  to "Web Drama Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"32\",\"sorting\":\"newest\"}"  to "Wuxia Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"32\",\"sorting\":\"newest\"}"  to "Wuxia Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"33\",\"sorting\":\"newest\"}"  to "Xianxia Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"33\",\"sorting\":\"newest\"}"  to "Xianxia Dizileri",
    "${mainUrl}/filmler?filter={\"category\":\"23\",\"sorting\":\"newest\"}"  to "Yarışma Filmleri",
    "${mainUrl}/diziler?filter={\"category\":\"23\",\"sorting\":\"newest\"}"  to "Yarışma Dizileri"
    )

    private fun getHeaders(referer: String? = null): Map<String, String> {
        val headers = mutableMapOf<String, String>(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.8,en-US;q=0.5,en;q=0.3",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "document", "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none", "Sec-Fetch-User" to "?1",
            "DNT" to "1", "Upgrade-Insecure-Requests" to "1",
            "Pragma" to "no-cache", "Cache-Control" to "no-cache"
        )
        if (!referer.isNullOrEmpty()) { headers["Referer"] = referer }
        headers["Origin"] = mainUrl.removeSuffix("/")
        return headers
    }

    private fun extractUrlFromStyle(style: String?): String? {
        if (style == null) return null
        return Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)
    }

    // getMainPage (Boş Kategori Kontrolü Ekledim. Eğer kategori içerisinde içerik olmazsa görsel ve metin göstermek için)
     override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val placeholderImageUrl = "https://raw.githubusercontent.com/GitLatte/Sinetech/refs/heads/main/WebDramaTurkey/src/main/resources/icerik-yok.jpeg/?text=İçerik+Yok" // İçerik olmayan kategoriye görsel ve metin eklemek için

        val url = if (page > 1) {
            if (request.data.contains("?")) { "${request.data}&page=$page" }
            else { "${request.data}?page=$page" }
        } else { request.data }
        Log.d("WDrama:", "İçerik sayfasını iste: URL='$url', Page='$page'")

        val document = try {
             app.get(url, headers = getHeaders(mainUrl)).document
        } catch (e: Exception) {
            Log.e("WDrama:", "İçerik URL`si alınamadı '$url': ${e.message}", e)
            return newHomePageResponse(request.name, emptyList(), false)
        }

        val resultsList = mutableListOf<SearchResponse>()
        try {
             document.select("div.row > div.col > div.list-movie, .list-movie").mapNotNull { element ->
                val titleElement = element.selectFirst("a.list-title, .list-caption > a.list-title")
                val title = titleElement?.text()?.trim() ?: return@mapNotNull null
                val linkElement = element.selectFirst("a.list-media, .list-caption > a.list-title")
                val href = fixUrlNull(linkElement?.attr("href")) ?: return@mapNotNull null
                val posterElement = element.selectFirst("div.media.media-cover, a.list-media > div.media")
                val posterStyle = posterElement?.attr("style")
                var poster = fixUrlNull(extractUrlFromStyle(posterStyle))
                if (poster == null) { poster = fixUrlNull(posterElement?.attr("data-src") ?: posterElement?.selectFirst("img")?.attr("src")) }

                val type = when {
                    href.contains("/dizi/") -> TvType.AsianDrama; href.contains("/anime/") -> TvType.Anime
                    href.contains("/program/") -> TvType.Podcast; href.contains("/film/") -> TvType.Movie
                    request.data.contains("/diziler") -> TvType.AsianDrama; request.data.contains("/animeler") -> TvType.Anime
                    request.data.contains("/programlar") -> TvType.Podcast; request.data.contains("/filmler") -> TvType.Movie
                    else -> TvType.Movie
                }
                val response = if (type == TvType.Movie || type == TvType.Podcast) {
                    newMovieSearchResponse(title, href, type) { this.posterUrl = poster }
                } else {
                    newTvSeriesSearchResponse(title, href, type) { this.posterUrl = poster }
                }
                 resultsList.add(response)
            }

        } catch (e: Exception) {

            return newHomePageResponse(request.name, emptyList(), false)
        }

        if (page == 1 && resultsList.isEmpty()) {
            Log.w("WDrama", "Kategori '${request.name}' ($url) boş, boş kategori için olan görsel gösteriliyor.")
            resultsList.add(
                newMovieSearchResponse(
                    name = "Bu kategoride içerik yok.",
                    url = request.data, type = TvType.Others
                ) { this.posterUrl = placeholderImageUrl }
            )
            return newHomePageResponse(request.name, resultsList, false) // hasNextPage = false
        }

        val hasNextPage = if (resultsList.isNotEmpty()) {
             document.select(".pagination a").any {
                 it.text().contains("Sonraki", ignoreCase = true) || it.text().contains("Next", ignoreCase = true)
             }
        } else { false }

        Log.d("WDrama:", "Finished Page: URL='$url'. Items: ${resultsList.size}, HasNext: $hasNextPage")
        return newHomePageResponse(request.name, resultsList, hasNextPage)
    }

    // --- toMainPageResult (Artık kullanılmıyor gibi, kaldırılabilir veya bırakılabilir) ---
    /*
    private fun Element.toMainPageResult(): SearchResponse? { ... }
    */

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("div.result-item article").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.title a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.media.media-cover")?.attr("data-src"))
        val contentType = when {
            href.contains("/dizi/") -> TvType.AsianDrama; href.contains("/anime/") -> TvType.Anime
            href.contains("/program/") -> TvType.Podcast; href.contains("/film/") -> TvType.Movie
            else -> TvType.Movie
        }

        return if (contentType == TvType.Movie) {
            newMovieSearchResponse(title, href, contentType) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, contentType) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)


     private fun Element.toRecommendationResult(): SearchResponse? {
        // Bu elementin 'div.list-movie' olduğunu varsayalım
        val titleElement = this.selectFirst(".list-caption > a.list-title, a.list-title")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrlNull(titleElement?.attr("href")) ?: return null // url adresini başlık elementinden alalım

        val posterElement = this.selectFirst("a.list-media > div.media, div.media.media-cover")
        val posterStyle = posterElement?.attr("style")
        var posterUrl = fixUrlNull(extractUrlFromStyle(posterStyle))
        if (posterUrl == null) {
            posterUrl = fixUrlNull(posterElement?.attr("data-src") ?: posterElement?.selectFirst("img")?.attr("src"))
        }

        val type = when {
            href.contains("/dizi/") -> TvType.AsianDrama
            href.contains("/anime/") -> TvType.Anime
            href.contains("/program/") -> TvType.Podcast
            href.contains("/film/") -> TvType.Movie
            else -> TvType.Movie
        }

        return if (type == TvType.Movie || type == TvType.Podcast) {
            newMovieSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, type) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, headers = getHeaders(mainUrl)).document
        } catch (e: Exception) {
             Log.e("WDrama", "İçerik sayfası yüklenirken hata $url: ${e.message}", e)
            return null
        }

        val contentType = when {
            url.contains("/dizi/") -> TvType.AsianDrama; url.contains("/anime/") -> TvType.Anime
            url.contains("/program/") -> TvType.Podcast; url.contains("/film/") -> TvType.Movie
            else -> TvType.Movie
        }
        Log.d("WDrama:", "Loading Content Type: $contentType for $url")

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.detail-attr div.text div.text-content")?.text()?.trim()
             ?: document.select("div.video-attr").firstOrNull { it.selectFirst("div.attr")?.text()?.trim() == "Genel Bakış" }
                ?.selectFirst("div.text")?.text()?.trim()
        val year = document.select("div.video-attr").firstOrNull { it.selectFirst("div.attr")?.text()?.trim() == "Yayın yılı" }
            ?.selectFirst("div.text")?.text()?.trim()?.toIntOrNull()
             ?: document.selectFirst("div.featured-box div.featured-attr div.text")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.tags div, .video-attr:contains(Tür) .text a").mapNotNull { it.text()?.trim()?.ifBlank { null } }
        val duration = document.selectFirst("span.runtime, span.duration")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.app-section div.row div.col:has(div.list-movie)").mapNotNull { colElement ->
             colElement.selectFirst("div.list-movie")?.toRecommendationResult() // Düzeltilmiş çağrı
        }
        val actorsData: List<ActorData>? = document.select("span.valor a, div.cast a").mapNotNull { el ->
            val actorName = el.text()?.trim()?.ifBlank { null }
            if(actorName != null) { ActorData(Actor(name = actorName, image = null), role = ActorRole.Main) }
            else null
        }.ifEmpty { 
            listOf(ActorData(Actor("Web Drama Turkey", fixUrl("/public/static/logo.webp"))))
        }
        val trailer = Regex("""youtube\.com/embed/([^?"']+)""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/watch?v=$it" }

        return if (contentType == TvType.Movie) {
            newMovieLoadResponse(title, url, contentType, url) {
                this.posterUrl = poster; this.plot = description; this.year = year
                this.tags = tags; this.duration = duration; this.recommendations = recommendations
                this.actors = actorsData
                addTrailer(trailer)
            }
        } else { // Dizi, Anime, Podcast
            val episodes = ArrayList<Episode>()
            val seasonElements = document.select(".season-list .seasons .nav .nav-item a, .season-list .nav .nav-link")
            if (seasonElements.isNotEmpty()) {
                 seasonElements.forEachIndexed { seasonIndex, seasonElement ->
                    val seasonNumber = seasonIndex + 1
                    val seasonId = seasonElement.attr("href").substringAfter("#", "")
                     val seasonPaneSelector = if (seasonId.isNotEmpty()) ".episodes .tab-pane#$seasonId" else ".episodes .tab-pane.active"
                     val seasonEpisodes = document.select("$seasonPaneSelector a")

                    seasonEpisodes.forEach { episodeElement ->
                        val episodeTitle = episodeElement.selectFirst(".episode, .name")?.text()?.trim() ?: episodeElement.text().trim()
                        val episodeHref = fixUrlNull(episodeElement.attr("href")) ?: return@forEach
                        val episodeNum = Regex("(\\d+)").find(episodeTitle)?.groupValues?.get(1)?.toIntOrNull()
                            ?: episodeElement.selectFirst("span.number")?.text()?.trim()?.toIntOrNull()
                            ?: (episodes.count { it.season == seasonNumber } + 1)

                        // newEpisode düzeltmesi <====
                        episodes.add(newEpisode(episodeHref) {
                            this.name = episodeTitle
                            this.season = seasonNumber
                            this.episode = episodeNum
                            this.posterUrl = poster // Ana posteri kullan
                            // this.runtime = bölümSüresi // Bilgi yoksa eklenmez
                            // this.description = bölümAçıklaması // Bilgi yoksa eklenmez
                        })
                    }
                }
            } else { // Tek Sezon
                val singleSeasonEpisodes = document.select(".episodes a, .season-list .episodes .tab-pane a, .episodios li a")
                singleSeasonEpisodes.forEach { episodeElement ->
                    val episodeTitle = episodeElement.selectFirst(".episode, .name")?.text()?.trim() ?: episodeElement.text().trim()
                    val episodeHref = fixUrlNull(episodeElement.attr("href")) ?: return@forEach
                    val episodeNum = Regex("(\\d+)").find(episodeTitle)?.groupValues?.get(1)?.toIntOrNull()
                        ?: episodeElement.selectFirst("span.number")?.text()?.trim()?.toIntOrNull()
                        ?: (episodes.size + 1)

                    // newEpisode düzeltmesi <====
                     episodes.add(newEpisode(episodeHref) {
                         this.name = episodeTitle
                         this.season = 1 // Tek sezon
                         this.episode = episodeNum
                         this.posterUrl = poster // Ana posteri kullan
                     })
                }
            }

             if (episodes.isEmpty() && contentType != TvType.Movie) {
                 Log.w("WDrama", "No episodes found for $title ($url), treating as movie to show info.")
                  return newMovieLoadResponse(title, url, contentType, url) {
                     this.posterUrl = poster; this.plot = description; this.year = year; this.tags = tags
                     this.duration = duration; this.recommendations = recommendations;
                     this.actors = actorsData
                     addTrailer(trailer)
                  }
             }

            newTvSeriesLoadResponse(title, url, contentType, episodes) {
                this.posterUrl = poster; this.plot = description; this.year = year
                this.tags = tags; this.duration = duration; this.recommendations = recommendations
                this.actors = actorsData
                addTrailer(trailer)
            }
        }
    } // load fonksiyonu sonu


    // --- loadLinks (coroutineScope kullandım çünkü linkler direkt alınamıyordu) ---
    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = try {
            app.get(data, headers = getHeaders(referer = mainUrl)).document
        } catch (e: Exception) {
            Log.e("WDrama", "Sayfa alınma hatası $data: ${e.message}")
            return false
        }

        var linksLoaded = false // Sonucun başarılı olup olmadığını takip etmek için

        // 1. Kaynak ID'lerini ve isimlerini bul
        val sourceButtons = document.select(".nav-player-select .dropdown-menu button.dropdown-source")
        val embedPairs = if (sourceButtons.isNotEmpty()) {
            sourceButtons.mapNotNull { btn ->
                val id = btn.attr("data-embed").ifBlank { null }
                val name = btn.selectFirst("span.name")?.text()?.trim()
                    ?: btn.text().replace("Türkçe Altyazı", "").trim().ifBlank { "Kaynak" }
                if (id != null) Triple(id, name, data) else null
            }
        } else {
            document.selectFirst(".embed-play .play-btn")?.attr("data-embed")?.let { id ->
                if (id.isNotBlank()) listOf(Triple(id, "Varsayılan", data)) else emptyList()
            } ?: emptyList()
        }

        if (embedPairs.isEmpty()) {
            Log.w("WDrama", "Sayfa üzerinde embed ID bulamadık: $data. Direkt olarak iframe deneniyor.")
            coroutineScope {
                 val tasks = document.select("iframe[src]").map { iframe ->
                    async { // Her iframe için asenkron görev başlat
                        val src = fixUrl(iframe.attr("src"))
                        var loaded = false
                        if (src.isNotBlank()) {
                            Log.d("WDrama", "Trying direct iframe src: $src")
                            loaded = loadExtractor(src, data, subtitleCallback, callback)
                        }
                        loaded // Görevin sonucunu döndür (true/false)
                    }
                }
                // Tüm görevlerin bitmesini bekle ve en az biri true ise linksLoaded'ı true yap
                linksLoaded = tasks.awaitAll().any { it }
            }
            return linksLoaded
        }

        Log.d("WDrama", "Found ${embedPairs.size} sources: ${embedPairs.joinToString { it.second + "("+it.first+")" }}")

         coroutineScope {
             val tasks = embedPairs.map { (embedId, sourceName, pageUrl) ->
                 async { // Her kaynak için asenkron görev başlat
                     var currentLinksLoaded = false // Bu görev için başarı durumu
                     try {
                         val ajaxUrl = fixUrl("/ajax/embed")
                         val postData = mapOf("id" to embedId)
                         val ajaxHeaders = getHeaders(referer = pageUrl) + mapOf(
                             "X-Requested-With" to "XMLHttpRequest", "Accept" to "*/*",
                             "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                         )
                         val ajaxResponse = app.post(ajaxUrl, headers = ajaxHeaders, data = postData)

                         val embedPhpIframeSrc = ajaxResponse.document.selectFirst("iframe[src]")?.attr("src")
                         if (embedPhpIframeSrc.isNullOrBlank()) {
                             Log.w("WDrama", "[$sourceName] Step 1: Failed to extract embed.php iframe src.")
                             return@async false // Bu async görev başarısız
                         }

                         val absoluteEmbedPhpSrc = fixUrl(embedPhpIframeSrc)
                         // Log.d("WDrama", "[$sourceName] Step 1: Extracted embed.php src: $absoluteEmbedPhpSrc")
                         // Log.d("WDrama", "[$sourceName] Step 2: Fetching embed.php page: $absoluteEmbedPhpSrc")

                         val embedPhpPageResponse = try {
                              app.get(absoluteEmbedPhpSrc, headers = getHeaders(referer = pageUrl))
                         } catch (e: Exception) {
                              Log.e("WDrama", "[$sourceName] Step 2: Failed to fetch embed.php: ${e.message}")
                              return@async false // Bu async görev başarısız
                         }
                         // Log.d("WDrama", "[$sourceName] Step 2: Status: ${embedPhpPageResponse.code}")

                         var finalUrlToLoad: String? = null
                         val embedPhpDocument: Document = embedPhpPageResponse.document
                         val finalIframeSrc = embedPhpDocument.selectFirst("iframe[src]")?.attr("src")

                         if (!finalIframeSrc.isNullOrBlank()) {
                             finalUrlToLoad = fixUrl(finalIframeSrc)
                             Log.d("WDrama", "[$sourceName] Step 2: Found final iframe: $finalUrlToLoad")
                         } else {
                             val scripts = embedPhpDocument.select("script")
                             for (script in scripts) {
                                 val scriptData = script.data()
                                 val videoUrlRegex = Regex("""["'](https?://(?:www\.)?(?:vk\.com|ok\.ru|moly\.cloud|vudeo\.io|[^"']*\.(?:m3u8|mp4))[^"']*)["']""")
                                 val match = videoUrlRegex.find(scriptData)
                                 if (match != null) {
                                     finalUrlToLoad = fixUrl(match.groupValues[1])
                                     Log.d("WDrama", "[$sourceName] Step 2: Found URL in script: $finalUrlToLoad")
                                     break
                                 }
                             }
                             if(finalUrlToLoad == null) {
                                 Log.w("WDrama", "[$sourceName] Step 2: Could not find final iframe or script URL.")
                                 return@async false // Bu async görev başarısız
                             }
                         }

                         if (!finalUrlToLoad.isNullOrBlank()) {
                             // Log.d("WDrama", "[$sourceName] Step 3: Calling loadExtractor: $finalUrlToLoad")
                             if (loadExtractor(finalUrlToLoad, pageUrl, subtitleCallback, callback)) {
                                 Log.d("WDrama", "[$sourceName] Step 3: Extractor SUCCESS for $finalUrlToLoad")
                                 currentLinksLoaded = true // Bu async görev başarılı
                             } else {
                                 Log.w("WDrama", "[$sourceName] Step 3: Extractor FAILED for $finalUrlToLoad")
                             }
                         } else {
                             Log.e("WDrama", "[$sourceName] Logic Error: finalUrlToLoad is blank.")
                         }

                     } catch (e: Exception) {
                         Log.e("WDrama", "[$sourceName] Error processing embedId $embedId: ${e.message}", e)
                     }
                     currentLinksLoaded // Async görevin sonucunu döndür
                 } // async biter
             } // map biter

             // Tüm async görevlerinin bitmesini bekle ve en az biri başarılıysa sonuçları true yap
             linksLoaded = tasks.awaitAll().any { it }
         } // coroutineScope biter


        if (!linksLoaded) {
             Log.e("WDrama", "Failed to load ANY links after trying all sources for page: $data")
        }

        return linksLoaded
    } // loadLinks fonksiyonu biter

} // Sınıf biter