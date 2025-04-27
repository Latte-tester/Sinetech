package com.sinetech.latte

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Log
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        return HomePageResponse(item)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf(
                "x-requested-with" to "XMLHttpRequest"
            )
        )
        return tryParseJson<List<AnimeSearch>>(json.text)?.filter {
            !it.link.contains("episode-") && it.link.contains(
                "/stream"
            )
        }?.map {
            newAnimeSearchResponse(
                it.title?.replace(Regex("</?em>"), "") ?: "",
                fixUrl(it.link),
                TvType.Anime
            ) {
            }
        } ?: throw ErrorLoadingException()

    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actor =
            document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = mutableListOf<Episode>()
        document.select("div#stream > ul:first-child li").map { ele ->
            val page = ele.selectFirst("a")
            val epsDocument = app.get(fixUrl(page?.attr("href") ?: return@map)).document
            epsDocument.select("div#stream > ul:nth-child(4) li").mapNotNull { eps ->
                episodes.add(
                    Episode(
                        fixUrl(eps.selectFirst("a")?.attr("href") ?: return@mapNotNull null),
                        episode = eps.selectFirst("a")?.text()?.toIntOrNull(),
                        season = page.text().toIntOrNull()
                    )
                )
            }
        }

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(
                DubStatus.Subbed,
                episodes
            )
            addActors(actor)
            plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val document = try { app.get(data).document } catch (e: Exception) {
             Log.e(name, "loadLinks - Sayfa alınamadı: $data - Hata: ${e.message}")
            return@coroutineScope false
        }
        var foundLinks = false

        val hosterLinks = document.select("div.hosterSiteVideo ul li").mapNotNull {
             val langKey = it.attr("data-lang-key"); val target = it.attr("data-link-target"); val hosterName = it.select("h4").text()
             if (target.isBlank() || hosterName.equals("Vidoza", true)) null else Triple(langKey, target, hosterName)
        }

        Log.d(name, "Bulunan Hosterlar (${hosterLinks.size}): ${hosterLinks.map { it.third }}")

        val deferredResults = hosterLinks.map { (langKey, linkTarget, hosterName) ->
            async {
                val lang = langKey.getLanguage(document) ?: langKey
                val sourceName = "$hosterName [$lang]"
                Log.d(name, "İşleniyor: $sourceName - $linkTarget")
                val foundInThisTask = mutableListOf<Boolean>()

                try {
                    val initialUrl = fixUrl(linkTarget)
                    val redirectUrl = fixUrl(app.get(initialUrl, referer = data).url)
                    Log.d(name, "Redirect URL: $redirectUrl for $sourceName")

                    if (hosterName.equals("VOE", ignoreCase = true)) {
                        val voeResults = mutableListOf<ExtractorLink>()
                        try {
                             // Voe().getUrl callback'i suspend değil, dikkat!
                             Voe().getUrl(redirectUrl, data) { fetchedVoeLink -> // Callback parametresine farklı isim verelim
                                 // Callback içinde newExtractorLink OLUŞTURULMAZ. Sadece veri toplanır.
                                 voeResults.add(fetchedVoeLink)
                                 Log.d(name, "Voe linki callback ile alındı: ${fetchedVoeLink.url}")
                             }
                         } catch (e: Exception){
                              Log.e(name, "Voe().getUrl çağrılırken hata: ${e.message}")
                         }

                        // Toplanan Voe linklerini işle (async bloğu içinde ama callback dışında)
                        if(voeResults.isNotEmpty()){
                             voeResults.forEach { link -> // Her bir bulunan voeLink için
                                try {
                                    callback(
                                        newExtractorLink( // newExtractorLink çağrısı
                                            source = sourceName,
                                            name = link.name, // link nesnesinden al
                                            url = link.url,   // link nesnesinden al
                                            type = link.type ?: ExtractorLinkType.VIDEO // link nesnesinden al
                                        ){
                                            // Lambda içinde this ile ayarla, değerleri link nesnesinden al
                                            this.referer = link.referer
                                            this.quality = link.quality
                                            this.isM3u8 = link.type == ExtractorLinkType.M3U8 // Tipe göre ayarla
                                            this.headers = link.headers
                                            this.extractorData = link.extractorData
                                        }
                                    )
                                    foundInThisTask.add(true)
                                } catch (e: Exception) {
                                     Log.e(name, "Voe callback işlerken hata: ${e.message}")
                                }
                            }
                        } else { Log.w(name, "Voe linki bulunamadı: $redirectUrl") }

                    } else {
                        // Diğerleri için loadExtractor
                        // loadExtractor callback'i suspend olabilir, o yüzden suspendSafeApiCall mantıklı
                        suspendSafeApiCall {
                             var extractorCallbackCalled = false
                             loadExtractor(redirectUrl, data, subtitleCallback) { link -> // link parametresi
                                try {
                                    callback(
                                        newExtractorLink(
                                            source = sourceName,
                                            name = link.name, // link nesnesinden al
                                            url = link.url,   // link nesnesinden al
                                            type = link.type ?: ExtractorLinkType.VIDEO // link nesnesinden al
                                        ) {
                                            this.referer = link.referer
                                            this.quality = link.quality
                                            this.isM3u8 = link.isM3u8 // Doğrudan linkten al
                                            this.headers = link.headers
                                            this.extractorData = link.extractorData
                                        }
                                    )
                                    extractorCallbackCalled = true
                                 } catch (e: Exception) {
                                      Log.e(name, "loadExtractor callback işlerken hata: ${e.message}")
                                 }
                            }
                             if(extractorCallbackCalled) foundInThisTask.add(true)
                             else Log.w(name, "loadExtractor link döndürmedi: $sourceName - $redirectUrl")
                        } ?: Log.w(name, "suspendSafeApiCall (loadExtractor) başarısız oldu: $sourceName")
                    }
                } catch (e: Exception) {
                    Log.e(name, "Link işlenirken hata: $sourceName - $linkTarget - Hata: ${e.message}")
                }
                foundInThisTask.any { it } // Bu görev başarılı mı?
            }
        }.awaitAll()

        foundLinks = deferredResults.any { it }

        Log.d(name, "loadLinks tamamlandı. Link bulundu mu: $foundLinks")
        return@coroutineScope foundLinks
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun String.getLanguage(document: Document): String? {
        return document.selectFirst("div.changeLanguageBox img[data-lang-key=$this]")?.attr("title")
            ?.removePrefix("mit")?.trim()
    }

    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://urochsunloath.com"
}