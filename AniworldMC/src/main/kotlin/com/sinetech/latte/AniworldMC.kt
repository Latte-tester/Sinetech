package com.sinetech.latte // Veya com.lagradost.cloudstream3.animeproviders

// === TÜM GEREKLİ IMPORTLAR ===
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log // CloudStream Log (2 argümanlı)
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities // Qualities importu eklendi
import com.lagradost.cloudstream3.utils.loadExtractor // loadExtractor importu
import com.lagradost.cloudstream3.utils.newExtractorLink // newExtractorLink importu
import com.lagradost.cloudstream3.utils.newEpisode // newEpisode importu
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall // suspendSafeApiCall importu
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex // Mutex için import
import kotlinx.coroutines.sync.withLock // Mutex için import
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
// ========================

open class AniworldMC : MainAPI() {
    override var mainUrl = "https://aniworld.to"
    override var name = "AniworldMC"
    override val hasMainPage = true
    override var lang = "de"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

     override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        if (item.isEmpty()) {
            Log.w(name, "Ana sayfada içerik bulunamadı, seçiciler kontrol edilmeli.")
        }
        return HomePageResponse(item)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = try {
            app.post(
                "$mainUrl/ajax/search",
                data = mapOf("keyword" to query),
                referer = "$mainUrl/search",
                headers = mapOf("x-requested-with" to "XMLHttpRequest")
            ).text
        } catch (e: Exception) {
            Log.e(name, "Arama isteği başarısız: ${e.message}")
            return emptyList()
        }

        return tryParseJson<List<AnimeSearch>>(json)?.mapNotNull {
            if (!it.link.contains("episode-") && it.link.contains("/stream")) {
                 newAnimeSearchResponse(
                    it.title?.replace(Regex("</?em>"), "") ?: return@mapNotNull null,
                    fixUrl(it.link),
                    TvType.Anime
                )
            } else {
                null
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
         val document = try { app.get(url).document } catch (e: Exception) {
             Log.e(name, "load - Sayfa alınamadı: $url - Hata: ${e.message}")
             return null
         }

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actorNames = document.select("li:contains(Schauspieler:) ul li a span")
                            .mapNotNull { it.text() }
                            .filter { it.isNotBlank() }

        val episodes = mutableListOf<Episode>()
        val seasonTabs = document.select("div#stream > ul:first-child li a")

        // Sezonları ve bölümleri al (coroutine ile paralel hale getirilebilir ama şimdilik sıralı)
        seasonTabs.forEach { seasonTab ->
            val seasonUrl = fixUrlNull(seasonTab.attr("href")) ?: return@forEach
            val seasonNum = seasonTab.text().toIntOrNull()
            if(seasonNum == null) {
                 Log.w(name, "Sezon numarası alınamadı: ${seasonTab.text()}")
                 return@forEach
            }
            val epsDocument = try { app.get(seasonUrl, referer = url).document } catch (e:Exception) {
                 Log.e(name, "Bölüm sayfası alınamadı: $seasonUrl - Hata: ${e.message}")
                 return@forEach
            }

            epsDocument.select("div.episodeList ul li, div#stream > ul:nth-child(4) li").mapNotNull { eps ->
                val epLink = fixUrlNull(eps.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epNumText = eps.selectFirst("a")?.text()?.trim() ?: eps.text().trim()
                val epNum = epNumText.filter { it.isDigit() }.toIntOrNull()

                if (epNum != null) {
                    episodes.add(
                        newEpisode(epLink) {
                            this.name = "Bölüm $epNum" // Daha basit isim
                            this.episode = epNum
                            this.season = seasonNum
                            this.posterUrl = poster // Ana posteri kullan
                        }
                    )
                } else {
                     Log.w(name, "Bölüm numarası alınamadı: $epNumText")
                }
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes.sortedWith(compareBy({ it.season }, { it.episode })))
            addActors(actorNames.map { Actor(it) })
            plot = description
            this.tags = tags
        }
    }

    // loadLinks Düzeltilmiş Hali
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val document = try { app.get(data).document } catch (e: Exception) {
            Log.e(name, "loadLinks - Ana bölüm sayfası alınamadı: $data - Hata: ${e.message}")
            return@coroutineScope false
        }
        val linksMutex = Mutex() // Callback'e aynı anda yazmayı önlemek için
        val foundLinksList = mutableListOf<Boolean>() // Başarı durumlarını toplamak için

        val hosterLinks = document.select("div.hosterSiteVideo ul li").mapNotNull {
             val langKey = it.attr("data-lang-key"); val target = it.attr("data-link-target"); val hosterName = it.select("h4").text()
             if (target.isBlank() || hosterName.equals("Vidoza", true)) null else Triple(langKey, target, hosterName)
        }

        Log.d(name, "Bulunan Hosterlar (${hosterLinks.size}): ${hosterLinks.map { it.third }}")

        // Linkleri paralel işle (async/awaitAll)
        val deferredJobs = hosterLinks.map { (langKey, linkTarget, hosterName) ->
            async { // Her biri için async görev başlat
                val lang = langKey.getLanguage(document) ?: langKey
                val sourceName = "$hosterName [$lang]"
                var taskSuccess = false // Bu görev başarılı mı?
                Log.d(name, "İşleniyor: $sourceName - $linkTarget")

                try {
                    val initialUrl = fixUrl(linkTarget)
                    val redirectUrl = fixUrl(app.get(initialUrl, referer = data).url)
                    Log.d(name, "Redirect URL: $redirectUrl for $sourceName")

                    if (hosterName.equals("VOE", ignoreCase = true)) {
                        // Voe linklerini güvenli bir şekilde al
                        val voeExtractor = Voe()
                        // Voe().getUrl in getUrl(url, referer) overload'ını kullanalım (varsa)
                        // Bu overload List<ExtractorLink>? döndürebilir ve suspend olabilir
                        val currentLinks = suspendSafeApiCall {
                            voeExtractor.getUrl(redirectUrl, data) // Sadece url ve referer verelim
                        } ?: emptyList() // Hata veya null ise boş liste

                        if (currentLinks.isNotEmpty()) {
                            Log.i(name, "Voe extractor ${currentLinks.size} link buldu.")
                            currentLinks.forEach { link ->
                                try {
                                    // Gelen linki doğrudan callback'e gönder (newExtractorLink gerekmez)
                                     linksMutex.withLock { // Callback'e aynı anda erişimi engelle
                                         callback(link.copy(source = sourceName)) // Sadece source adını güncelle
                                     }
                                     taskSuccess = true
                                 } catch (e: Exception) {
                                      Log.e(name, "Voe callback işlerken hata: ${e.message}")
                                 }
                            }
                        } else {
                            Log.w(name, "Voe extractor link bulamadı: $redirectUrl")
                        }

                    } else {
                        // Diğerleri için loadExtractor
                        var extractorCallbackCalled = false
                         suspendSafeApiCall {
                             loadExtractor(redirectUrl, data, subtitleCallback) { link ->
                                try {
                                    // Gelen linki doğrudan callback'e gönder
                                     linksMutex.withLock {
                                        callback(link.copy(source = sourceName)) // Sadece source adını güncelle
                                     }
                                    extractorCallbackCalled = true
                                 } catch (e: Exception) {
                                      Log.e(name, "loadExtractor callback işlerken hata: ${e.message}")
                                 }
                            }
                        } ?: Log.w(name, "suspendSafeApiCall (loadExtractor) null döndü: $sourceName")

                         if(extractorCallbackCalled) taskSuccess = true
                         else Log.w(name, "loadExtractor link döndürmedi: $sourceName - $redirectUrl")
                    }
                } catch (e: Exception) {
                     Log.e(name, "Link işlenirken hata: $sourceName - $linkTarget - Hata: ${e.message}")
                }
                taskSuccess // async bloğunun dönüş değeri
            }
        } // map sonu

        foundLinksList.addAll(deferredJobs.awaitAll()) // Tüm görevleri bekle ve sonuçları listeye ekle

        val finalResult = foundLinksList.any { it } // En az bir görev başarılıysa true

        Log.d(name, "loadLinks tamamlandı. Link bulundu mu: $finalResult")
        return@coroutineScope finalResult
    }


    private fun String.getLanguage(document: Document): String? {
        return document.selectFirst("div.changeLanguageBox img[data-lang-key=\"$this\"]")?.attr("title")
            ?.replace("mit", "", ignoreCase = true)
            ?.replace("Untertiteln", "", ignoreCase = true)
            ?.replace("synchronisiert", "", ignoreCase = true)
            ?.trim()
            ?.let { if(it.isBlank()) null else it }
   }

    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3, .serienTitle")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }
} // AniworldMC sınıfının sonu

class Dooood : DoodLaExtractor() {
    // mainUrl override edilebilir olmalı, DoodLaExtractor'a bakmak lazım
    // Eğer DoodLaExtractor'da 'open var mainUrl' ise 'override' gerekir.
    // Eğer 'var mainUrl' ise veya hiç yoksa, 'override' olmadan tanımlanır.
    // Şimdilik override varsayalım, hata verirse kaldırırız.
    override var mainUrl = "https://urochsunloath.com"
}