package com.sinetech.latte

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.InputStream

data class TvPassLoadData(
    val url: String,
    val name: String,
    val posterUrl: String? = null
)

class TvPass : MainAPI() {
    override var mainUrl = "https://tvpass.org"
    override var name = "TvPass"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    // Tarayıcıdan aldığınız başlıkları buraya kopyalayın
    private val browserHeaders = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-TR;q=0.8,en;q=0.7,en-US;q=0.6",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
        "Connection" to "keep-alive",
        "Referer" to mainUrl, // Ana sayfa için Referer'ı ana domain tutalım
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    )

    // Ana sayfayı yükler (HTML Parsing ile)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val channelsUrl = "$mainUrl/channels"
            Log.d("TvPass", "Attempting to load homepage from: $channelsUrl")

            val homepageHeaders = browserHeaders.toMutableMap()
            // Referer'ı burada özellikle belirtmeye gerek yok, browserHeaders içinde ana domain olarak var.
            // Eğer Referer'ın channels sayfası olmasını isterseniz:
            // homepageHeaders["Referer"] = channelsUrl

            val response = app.get(channelsUrl, headers = homepageHeaders)

            if (!response.isSuccessful) {
                Log.e("TvPass", "Failed to load homepage HTML from $channelsUrl. Status: ${response.code}")
                throw Exception("Ana sayfa yüklenemedi. Durum Kodu: ${response.code}")
            }

            val document = response.document
            Log.d("TvPass", "Homepage HTML loaded successfully. Parsing HTML.")

            val channelItems = document.select("ul#channelsContainer > li > a")
            Log.d("TvPass", "Found ${channelItems.size} potential channel items using selector.")

            val tvChannels = ArrayList<SearchResponse>()

            for (channelItem in channelItems) {
                try {
                    val channelRelativeLink = channelItem.attr("href")?.trim()
                    if (channelRelativeLink.isNullOrBlank() || channelRelativeLink == "#") {
                        Log.d("TvPass", "Skipping item with empty or invalid link: ${channelItem.outerHtml()}")
                        continue
                    }
                    val channelUrl = "$mainUrl$channelRelativeLink"

                    val channelName = channelItem.selectFirst("h2")?.text()?.trim()
                    if (channelName.isNullOrBlank()) {
                         Log.d("TvPass", "Skipping item with empty name: ${channelItem.outerHtml()}")
                         continue
                    }
                     Log.d("TvPass", "Found channel: $channelName, URL: $channelUrl")

                    val channelPoster = channelItem.selectFirst("img")?.attr("src")?.trim()
                     val fullChannelPoster = if (channelPoster != null && !channelPoster.startsWith("http")) "$mainUrl$channelPoster" else channelPoster


                    val searchResponse = newLiveSearchResponse(
                        channelName,
                        TvPassLoadData(url = channelUrl, name = channelName, posterUrl = fullChannelPoster).toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = fullChannelPoster
                    }
                    tvChannels.add(searchResponse)

                } catch (e: Exception) {
                    Log.e("TvPass", "Error parsing a channel item: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.d("TvPass", "Finished parsing channels from HTML. Found ${tvChannels.size} valid channels.")

            val homePageList = HomePageList("Tüm Kanallar", tvChannels, isHorizontalImages = false)

            return newHomePageResponse(
                list = homePageList,
                hasNext = false
            )

        } catch (e: Exception) {
            Log.e("TvPass", "Error loading homepage from HTML: ${e.message}")
            e.printStackTrace()
             return newHomePageResponse(HomePageList("Hata Oluştu", emptyList()), hasNext = false)
        }
    }

    // Bir kanalın detaylarını ve akış kaynağını yükler
    override suspend fun load(url: String): LoadResponse {
        try {
            val loadData = parseJson<TvPassLoadData>(url)
            val channelUrl = loadData.url // Kanal detay sayfası URL'si

            Log.d("TvPass", "Loading stream for channel: ${loadData.name} from URL: $channelUrl")

            val detailPageHeaders = browserHeaders.toMutableMap()
            detailPageHeaders["Referer"] = channelUrl // Referer'ı kanal detay sayfası olarak ayarla

            val document = app.get(channelUrl, headers = detailPageHeaders).document

            // JavaScript kodundan stream endpoint'ini (örn: /token/AEEast?quality=sd) bulalım
            var streamEndpoint: String? = null
            val scriptTags = document.select("script")
            for (script in scriptTags) {
                val scriptContent = script.html()
                // JavaScript kodunda "file:" veya "sources:" alanlarındaki değeri ara
                // Bu değer muhtemelen "/token/{slug}?quality={kalite}" formatında olacak
                val regex = "file:\\s*['\"](.*?)['\"]".toRegex()
                val matchResult = regex.find(scriptContent)
                if (matchResult != null) {
                    streamEndpoint = matchResult.groupValues[1]
                    Log.d("TvPass", "Found stream endpoint in JWPlayer script: $streamEndpoint")
                    break
                }
                 val sourcesRegex = "sources:\\s*\\[\\s*\\{.*?file:\\s*['\"](.*?)['\"].*?\\}\\]".toRegex()
                  val sourcesMatchResult = sourcesRegex.find(scriptContent)
                  if (sourcesMatchResult != null) {
                      streamEndpoint = sourcesMatchResult.groupValues[1]
                       Log.d("TvPass", "Found stream endpoint in JWPlayer sources: $streamEndpoint")
                       break
                  }
            }

            if (streamEndpoint.isNullOrBlank()) {
                Log.e("TvPass", "Stream endpoint not found in HTML for ${loadData.name}")
                throw Exception("Akış kaynağı endpoint'i HTML'de bulunamadı.")
            }

            // Stream endpoint'e istek yaparak dönen JSON'dan asıl stream URL'sini çekelim
            val fullStreamEndpointUrl = if (streamEndpoint.startsWith("http")) streamEndpoint else "$mainUrl$streamEndpoint"
             Log.d("TvPass", "Attempting to fetch actual stream URL from endpoint: $fullStreamEndpointUrl")

             // Endpoint isteği için Referer başlığı kanal detay sayfası olmalı
             val endpointHeaders = browserHeaders.toMutableMap()
             endpointHeaders["Referer"] = channelUrl // Referer kanal detay sayfası

            // Endpoint'ten dönen JSON'u çek
            val streamJsonResponse = app.get(fullStreamEndpointUrl, headers = endpointHeaders)

            if (!streamJsonResponse.isSuccessful) {
                 Log.e("TvPass", "Failed to fetch actual stream URL JSON from endpoint $fullStreamEndpointUrl. Status: ${streamJsonResponse.code}")
                 throw Exception("Akış URL'si JSON'u endpoint'ten çekilemedi. Durum Kodu: ${streamJsonResponse.code}")
            }

            // JSON içeriğini parse et
            // JavaScript koduna göre JSON yapısı { "url": "...", "status": "...", ... } gibi
            val streamJsonData = parseJson<Map<String, Any>>(streamJsonResponse.text)

            // JSON'daki "url" alanından asıl stream URL'sini alalım
            val actualStreamUrl = streamJsonData["url"] as? String
            Log.d("TvPass", "Fetched actual stream URL from JSON: $actualStreamUrl")

            // JSON'daki "status" alanını kontrol edebiliriz (isteğe bağlı)
             val streamStatus = streamJsonData["status"] as? String
             if (streamStatus == "false" || streamStatus == "failed") {
                 Log.e("TvPass", "Stream status indicates failure: $streamStatus for ${loadData.name}")
                 throw Exception("Akış şu anda mevcut değil. Lütfen daha sonra tekrar deneyin.")
             }


            if (actualStreamUrl.isNullOrBlank()) {
                 Log.e("TvPass", "Actual stream URL not found in JSON response for ${loadData.name}")
                 throw Exception("Gerçek akış URL'si JSON yanıtında bulunamadı.")
            }

            // Stream URL'sinin tipini belirleyelim
            val linkType = if (actualStreamUrl.endsWith(".m3u8", true) || actualStreamUrl.contains("m3u8", true)) {
                ExtractorLinkType.M3U8
            } else {
                 ExtractorLinkType.VIDEO
            }

            // ExtractorLink objesi oluşturma
            // Stream URL'si için de Referer başlığı gerekiyor olabilir
            val extractorLink = ExtractorLink(
                source = name,
                name = "TvPass Stream",
                url = actualStreamUrl, // Asıl stream URL'si
                referer = channelUrl, // referer parametresi (genellikle stream'in geldiği sayfa URL'si)
                quality = Qualities.Unknown.value, // quality parametresi
                headers = mapOf("Referer" to channelUrl), // headers parametresi (stream isteği için Referer)
                type = linkType // type parametresi
            )

            // LoadResponse objesi oluşturma
            // Tek bir stream kaynağı bulduğumuz için newLiveStreamLoadResponse yeterli
            return newLiveStreamLoadResponse(
                name = loadData.name,
                url = actualStreamUrl, // LoadResponse'un ana URL'si asıl stream URL'si
                dataUrl = url // LoadData JSON'u
            ) {
                 this.posterUrl = loadData.posterUrl
                 val currentProgramTitle = document.selectFirst("span.text-base.text-gray-900.whitespace-normal.break-words")?.attr("title")?.trim()
                 if (!currentProgramTitle.isNullOrBlank()) {
                     this.plot = "Şu An Yayınlanan: $currentProgramTitle"
                 }
                 // Extractor linkleri LoadResponse'a eklemeye gerek yok,
                 // newLiveStreamLoadResponse direkt url parametresi üzerinden ExtractorLink oluşturur.
            }

        } catch (e: Exception) {
            Log.e("TvPass", "Error loading stream for ${parseJson<TvPassLoadData>(url).name}: ${e.message}")
            e.printStackTrace()
             throw e
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
         return emptyList()
    }
}