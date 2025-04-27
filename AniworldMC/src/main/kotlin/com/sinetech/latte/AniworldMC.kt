package com.sinetech.latte

// Gerekli importlar (Öncekiler + Logcat için Android Log)
import com.lagradost.api.Log // CloudStream Log (2 argümanlı)
// import android.util.Log as AndroidLog // Alternatif Android Log (Exception için)
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.Qualities // Qualities importu
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    // ... (getMainPage, search, load fonksiyonları önceki gibi kalabilir, Episode düzeltmesi yapılmıştı) ...
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
                val epNum = eps.selectFirst("a")?.text()?.trim()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: eps.text().trim().filter{ it.isDigit() }.toIntOrNull()

                if (epNum != null) {
                    episodes.add(
                        newEpisode(epLink) {
                            this.name = "Bölüm $epNum"
                            this.episode = epNum
                            this.season = seasonNum
                            // Ana posteri kullanmak daha mantıklı
                            this.posterUrl = poster
                        }
                    )
                } else {
                     Log.w(name, "Bölüm numarası alınamadı: ${eps.text()}")
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


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope { // CoroutineScope kullan
        val document = try { app.get(data).document } catch (e: Exception) {
            Log.e(name, "Ana sayfa alınamadı: $data - Hata: ${e.message}")
            return@coroutineScope false
        }
        var foundLinks = false // Genel başarı durumu

        val hosterLinks = document.select("div.hosterSiteVideo ul li").mapNotNull {
            // ... (hoster bilgilerini al, Vidoza'yı filtrele) ...
             val langKey = it.attr("data-lang-key"); val target = it.attr("data-link-target"); val hosterName = it.select("h4").text()
             if (target.isBlank() || hosterName.equals("Vidoza", true)) null else Triple(langKey, target, hosterName)
        }

        Log.d(name, "Bulunan Hosterlar (${hosterLinks.size}): ${hosterLinks.map { it.third }}")

        // Linkleri paralel olarak işle ve sonuçları topla
        val extractedLinksDeferred = hosterLinks.map { (langKey, linkTarget, hosterName) ->
            async { // Her biri için async görev başlat
                val lang = langKey.getLanguage(document) ?: langKey
                val sourceName = "$hosterName [$lang]"
                Log.d(name, "İşleniyor: $sourceName - $linkTarget")
                val linksFromThisTask = mutableListOf<ExtractorLink>() // Bu görevden gelen linkler

                try {
                    val initialUrl = fixUrl(linkTarget)
                    val redirectUrl = fixUrl(app.get(initialUrl, referer = data).url)
                    Log.d(name, "Redirect URL: $redirectUrl for $sourceName")

                    if (hosterName.equals("VOE", ignoreCase = true)) {
                        // Voe extractor'ını çağır ve sonuçları topla
                        // suspendSafeApiCall burada deneyebiliriz, belki Voe içindeki ağ isteği için?
                        suspendSafeApiCall {
                            // Voe().getUrl'in doğru imzasını varsayıyoruz (callback ile)
                             Voe().getUrl(redirectUrl, data) { voeLink -> // subtitleCallback'i Voe'ya verme
                                // Gelen linki doğru formatta listeye ekle
                                linksFromThisTask.add(
                                    newExtractorLink(
                                        source = sourceName,
                                        name = voeLink.name,
                                        url = voeLink.url,
                                        type = voeLink.type ?: ExtractorLinkType.VIDEO
                                    ).apply {
                                        this.referer = voeLink.referer
                                        this.quality = voeLink.quality
                                        // isM3u8'e gerek yok, type yeterli
                                        this.headers = voeLink.headers
                                        this.extractorData = voeLink.extractorData
                                    }
                                )
                            }
                        } ?: Log.e(name, "Voe().getUrl (suspendSafeApiCall) başarısız oldu.")


                    } else {
                        // Diğerleri için loadExtractor
                        suspendSafeApiCall {
                            loadExtractor(redirectUrl, data, subtitleCallback) { link ->
                                linksFromThisTask.add(
                                    newExtractorLink(
                                        source = sourceName,
                                        name = link.name,
                                        url = link.url,
                                        type = link.type ?: ExtractorLinkType.VIDEO
                                    ).apply {
                                        this.referer = link.referer
                                        this.quality = link.quality
                                        this.headers = link.headers
                                        this.extractorData = link.extractorData
                                    }
                                )
                            }
                        } ?: Log.w(name, "loadExtractor (suspendSafeApiCall) link bulamadı: $sourceName - $redirectUrl")
                    }
                } catch (e: Exception) {
                     Log.e(name, "Link işlenirken hata: $sourceName - $linkTarget - Hata: ${e.message}")
                }
                linksFromThisTask // async bloğunun dönüş değeri bu liste olacak
            }
        } // map sonu

        // Tüm async görevlerinin bitmesini bekle ve sonuç listelerini birleştir
        val allExtractedLinks = extractedLinksDeferred.awaitAll().flatten()

        // Toplanan tüm linkleri ana callback'e gönder
        if (allExtractedLinks.isNotEmpty()) {
            foundLinks = true
            allExtractedLinks.forEach { callback(it) }
            Log.i(name, "${allExtractedLinks.size} adet link bulundu ve gönderildi.")
        } else {
            Log.w(name, "Hiçbir hoster'dan link bulunamadı.")
        }

        return@coroutineScope foundLinks
    }


    // Dil alma fonksiyonu
     private fun String.getLanguage(document: Document): String? {
         return document.selectFirst("div.changeLanguageBox img[data-lang-key=\"$this\"]")?.attr("title")
             ?.replace("mit", "", ignoreCase = true)
             ?.replace("Untertiteln", "", ignoreCase = true)
             ?.replace("synchronisiert", "", ignoreCase = true)
             ?.trim()
             ?.let { if(it.isBlank()) null else it } // Boş string ise null yap
    }

    // Anime arama sonucu için data class
    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

    // Ana sayfa ve arama sonuçları için yardımcı fonksiyon
    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3, .serienTitle")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))
        // Ana sayfada tip her zaman Anime olacak gibi duruyor
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }
} // AniworldMC sınıfının sonu

// Özel Dood Extractor
class Dooood : DoodLaExtractor() {
    // === override kaldırıldı, var olarak tanımlandı ===
    override var mainUrl = "https://urochsunloath.com"
    // ==============================================
}