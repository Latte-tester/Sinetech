package com.sinetech.latte // Veya com.lagradost.cloudstream3.animeproviders

// === GEREKLİ IMPORTLAR ===
import com.lagradost.api.Log // Doğru Log
import com.fasterxml.jackson.annotation.JsonProperty // Jackson anotasyonu
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor // DoodLaExtractor importu
import com.lagradost.cloudstream3.extractors.Voe // Doğru Voe importu
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink // newExtractorLink importu
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall // suspendSafeApiCall importu
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
// ========================

open class AniworldMC : MainAPI() {
    override var mainUrl = "https://aniworld.to"
    override var name = "AniworldMC"
    override val hasMainPage = true
    override var lang = "de" // Ana dil Almanca, içerik farklı dillerde olabilir

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    // ... (getMainPage fonksiyonu aynı kalır) ...
     override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val item = arrayListOf<HomePageList>()
        document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map
            val home = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult() // Aşağıdaki toSearchResult kullanılır
            }
            if (home.isNotEmpty()) item.add(HomePageList(header, home))
        }
        // Eğer item boşsa, ana sayfada farklı bir yapı olabilir, log ekleyelim
        if (item.isEmpty()) {
            Log.w(name, "Ana sayfada içerik bulunamadı, seçiciler kontrol edilmeli.")
        }
        return HomePageResponse(item)
    }


    // ... (search fonksiyonu aynı kalır) ...
    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf("x-requested-with" to "XMLHttpRequest")
        ).text // .text olarak alalım

        // tryParseJson<T> null dönebilir, güvenli ? ile erişelim
        return tryParseJson<List<AnimeSearch>>(json)?.mapNotNull {
            // Filtreleme ve link kontrolü
            if (!it.link.contains("episode-") && it.link.contains("/stream")) {
                 newAnimeSearchResponse(
                    // Başlıktaki <em> etiketlerini temizle
                    it.title?.replace(Regex("</?em>"), "") ?: return@mapNotNull null,
                    fixUrl(it.link),
                    TvType.Anime // Varsayılan tip
                ) // Boş lambda bloğu gereksizse kaldırılabilir {}
            } else {
                null
            }
        } ?: emptyList() // JSON parse edilemezse veya null ise boş liste dön
    }

    // ... (load fonksiyonu - Episode constructor düzeltmesi) ...
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        // Aktör isimlerini alalım, eğer boşsa boş liste dönsün
        val actorNames = document.select("li:contains(Schauspieler:) ul li a span")
                            .mapNotNull { it.text() }
                            .filter { it.isNotBlank() }

        val episodes = mutableListOf<Episode>()
        // Sezon sekmelerini seç
        val seasonTabs = document.select("div#stream > ul:first-child li a")

        // Her sezon için bölümleri al (Paralel yapmak için async kullanılabilir ama şimdilik sıralı)
        seasonTabs.forEach { seasonTab ->
            val seasonUrl = fixUrlNull(seasonTab.attr("href")) ?: return@forEach
            val seasonNum = seasonTab.text().toIntOrNull()
            if(seasonNum == null) {
                 Log.w(name, "Sezon numarası alınamadı: ${seasonTab.text()}")
                 return@forEach
            }

            // Sezon sayfasını (veya AJAX endpoint'ini) getir
            // Eğer AJAX değilse direkt get, AJAX ise post gerekebilir
            // Şimdilik get varsayalım
            val epsDocument = try { app.get(seasonUrl, referer = url).document } catch (e:Exception) {
                 Log.e(name, "Bölüm sayfası alınamadı: $seasonUrl", e)
                 return@forEach // Bu sezonu atla
            }

            // Bölümleri seç (ul:nth-child(4) yerine daha sağlam bir selector?)
            // Belki de bölüm listesi için ID vardır? div#episodeList ul li gibi?
            epsDocument.select("div.episodeList ul li, div#stream > ul:nth-child(4) li").mapNotNull { eps -> // Daha genel seçici
                val epLink = fixUrlNull(eps.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                // Bölüm numarasını daha sağlam almayı dene
                val epNum = eps.selectFirst("a")?.text()?.trim()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: eps.text().trim().filter{ it.isDigit() }.toIntOrNull()

                if (epNum != null) {
                    // newEpisode kullan
                    episodes.add(
                        newEpisode(epLink) {
                            this.name = "Bölüm $epNum" // İsim belirle
                            this.episode = epNum
                            this.season = seasonNum
                        }
                    )
                } else {
                     Log.w(name, "Bölüm numarası alınamadı: ${eps.text()}")
                }
            }
        }

        // Yükleme yanıtını oluştur
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title // İngilizce adı da aynı varsayalım
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes.sortedWith(compareBy({ it.season }, { it.episode }))) // Sıralı ekle
            // Aktör isimlerinden ActorData listesi oluştur
            addActors(actorNames.map { Actor(it) }) // Sadece isimleri kullan
            plot = description
            this.tags = tags
        }
    }


    // loadLinks fonksiyonu (Düzeltilmiş)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val document = try { app.get(data).document } catch (e: Exception) {
            Log.e(name, "Bölüm link sayfası alınamadı: $data", e)
            return@coroutineScope false
        }
        var foundLinks = false

        val hosterLinks = document.select("div.hosterSiteVideo ul li").mapNotNull {
            val langKey = it.attr("data-lang-key")
            val target = it.attr("data-link-target")
            val hosterName = it.select("h4").text() // hosterName'i burada al
            if (target.isBlank() || hosterName.equals("Vidoza", ignoreCase = true)) null
            else Triple(langKey, target, hosterName) // Triple olarak döndür
        }

        Log.d(name, "Bulunan Hosterlar (${hosterLinks.size}): ${hosterLinks.map { it.third }}")

        val results = hosterLinks.map { (langKey, linkTarget, hosterName) -> // Değişken isimleri doğru
            async { // async ile paralel işlem
                val lang = langKey.getLanguage(document) ?: langKey
                val sourceName = "$hosterName [$lang]" // Doğru değişken adı
                Log.d(name, "İşleniyor: $sourceName - $linkTarget")
                var success = false

                try {
                    // linkTarget zaten fixUrl ile düzeltilmiş olmalı ama garanti olsun
                    val initialUrl = fixUrl(linkTarget)
                    // Redirect URL'sini almak için app.get kullan
                    val redirectUrl = fixUrl(app.get(initialUrl, referer = data).url) // Referer olarak ana bölüm sayfasını kullan
                    Log.d(name, "Redirect URL: $redirectUrl for $sourceName")

                    if (hosterName.equals("VOE", ignoreCase = true)) {
                        // Voe extractor'ını suspendSafeApiCall ile çağır
                        suspendSafeApiCall {
                            // Voe().getUrl'in callback'i suspend olmadığı için burada doğrudan newExtractorLink çağıramayız.
                            // Voe linklerini bir listede toplayıp dışarıda işleyeceğiz.
                            val voeResults = mutableListOf<ExtractorLink>()
                            Voe().getUrl(redirectUrl, data, subtitleCallback) { link -> // subtitleCallback'i Voe'ya da verelim
                                // Gelen linki doğrudan listeye ekle (daha sonra işlenecek)
                                voeResults.add(link)
                            }
                            // Voe().getUrl çağrısı bittikten sonra listeyi işle
                            if(voeResults.isNotEmpty()){
                                voeResults.forEach { voeLink ->
                                     callback(
                                        newExtractorLink(
                                            source = sourceName, // Doğru değişken
                                            name = voeLink.name,
                                            url = voeLink.url,
                                            type = voeLink.type ?: ExtractorLinkType.VIDEO
                                        ) {
                                            this.referer = voeLink.referer // 'this' ile erişim
                                            this.quality = voeLink.quality
                                            this.isM3u8 = voeLink.type == ExtractorLinkType.M3U8 // Tipe göre ayarla
                                            this.headers = voeLink.headers
                                            this.extractorData = voeLink.extractorData
                                        }
                                    )
                                }
                                success = true // En az bir link işlendiyse başarılı
                            } else {
                                 Log.w(name, "Voe linki bulunamadı: $redirectUrl")
                            }
                        } // suspendSafeApiCall sonu

                    } else {
                        // Diğerleri için loadExtractor
                         suspendSafeApiCall {
                             loadExtractor(redirectUrl, data, subtitleCallback) { link ->
                                callback(
                                    newExtractorLink(
                                        source = sourceName, // Doğru değişken
                                        name = link.name,
                                        url = link.url,
                                        type = link.type ?: ExtractorLinkType.VIDEO
                                    ) {
                                        this.referer = link.referer
                                        this.quality = link.quality
                                        this.isM3u8 = link.isM3u8
                                        this.headers = link.headers
                                        this.extractorData = link.extractorData
                                    }
                                )
                                success = true
                            }
                        } // suspendSafeApiCall sonu
                         if (!success) {
                             Log.w(name, "loadExtractor link bulamadı: $sourceName - $redirectUrl")
                        }
                    }
                } catch (e: Exception) {
                    // Log.e 3 argüman alabilir
                    Log.e(name, "Link işlenirken hata: $sourceName - $linkTarget", e)
                }
                success // async bloğunun dönüş değeri
            }
        }.awaitAll()

        foundLinks = results.any { it }

        Log.d(name, "loadLinks tamamlandı. Link bulundu mu: $foundLinks")
        return@coroutineScope foundLinks
    }


    // ... (toSearchResult, getLanguage fonksiyonları aynı kalır) ...
    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        // Ana sayfadaki başlık yapısı farklı olabilir, h3 veya .serienTitle deneyelim
        val title = this.selectFirst("h3, .serienTitle")?.text()?.trim() ?: return null
        // data-src veya src attribute'unu dene
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

     private fun String.getLanguage(document: Document): String? {
         // Dil bayrağının title'ından dili al
         return document.selectFirst("div.changeLanguageBox img[data-lang-key=\"$this\"]")?.attr("title")
             ?.replace("mit", "", ignoreCase = true) // "mit" kelimesini kaldır (büyük/küçük harf duyarsız)
             ?.replace("Untertiteln", "", ignoreCase = true) // "Untertiteln" kelimesini kaldır
             ?.replace("synchronisiert", "", ignoreCase = true) // "synchronisiert" kelimesini kaldır
             ?.trim() // Boşlukları temizle
    }


    // JSON parse için data class
    private data class AnimeSearch(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )

}

// Özel Dood Extractor (mainUrl override olmadan)
class Dooood : DoodLaExtractor() {
    // override var mainUrl = "https://urochsunloath.com" // override kaldırıldı
    var mainUrl = "https://urochsunloath.com" // Normal değişken olarak tanımla
}