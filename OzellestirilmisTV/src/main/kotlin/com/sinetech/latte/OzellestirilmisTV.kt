package com.sinetech.latte

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager // SharedPreferences için context'e alternatif

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.ui.dialogs.InputDialogResult
import com.lagradost.cloudstream3.ui.dialogs.showConfirmationDialog
import com.lagradost.cloudstream3.ui.dialogs.showInputDialog
import com.lagradost.cloudstream3.ui.dialogs.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

class OzellestirilmisTVProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(OzellestirilmisTV(context))
    }
}

@Serializable
data class M3uSource(
    val url: String,
    var name: String = url, // Varsayılan isim URL olsun
    var isActive: Boolean = true
)

@Serializable
data class M3uChannel(
    val title: String,
    val streamUrl: String,
    val logoUrl: String?,
    val group: String,
    val country: String?
)

@Serializable
data class LoadData(
    val streamUrl: String,
    val title: String,
    val posterUrl: String?,
    val group: String,
    val nation: String?
)

class OzellestirilmisTV(val context: Context) : MainAPI() {
    override var name = "OzellestirilmisTV"
    override var mainUrl = "ozellestirilmistv://home" // Benzersiz bir ana URL
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)
    override val hasChromecastSupport = true // İsteğe bağlı

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun getSources(): List<M3uSource> {
        val jsonString = context.getDataStore("ozellestirilmistv_sources", "[]")
        return try {
            json.decodeFromString<List<M3uSource>>(jsonString)
        } catch (e: Exception) {
            Log.e(name, "Error loading sources", e)
            emptyList()
        }
    }

    private fun saveSources(sources: List<M3uSource>) {
        val jsonString = json.encodeToString(sources)
        context.setDataStore("ozellestirilmistv_sources", jsonString)
    }

    private suspend fun parseM3u(m3uString: String): List<M3uChannel> {
        return withContext(Dispatchers.IO) {
            val channels = mutableListOf<M3uChannel>()
            var currentTitle: String? = null
            var currentLogo: String? = null
            var currentGroup: String? = null
            var currentCountry: String? = null

            m3uString.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("#EXTINF:")) {
                    val infoLine = trimmedLine.substringAfter("#EXTINF:")
                    val parts = infoLine.split(",", limit = 2)
                    currentTitle = parts.getOrNull(1)

                    // Extract attributes like tvg-logo, group-title, tvg-country
                    val attributesPart = parts.getOrNull(0)?.substringAfter(" ") ?: ""
                    currentLogo = attributesPart.extract("tvg-logo")
                    currentGroup = attributesPart.extract("group-title")
                    currentCountry = attributesPart.extract("tvg-country")

                } else if (!trimmedLine.startsWith("#") && trimmedLine.isNotEmpty() && currentTitle != null) {
                    channels.add(
                        M3uChannel(
                            title = currentTitle!!.trim(),
                            streamUrl = trimmedLine,
                            logoUrl = currentLogo?.trim()?.takeIf { it.isNotEmpty() },
                            group = currentGroup?.trim()?.takeIf { it.isNotEmpty() } ?: "Diğer",
                            country = currentCountry?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    )
                    // Reset for next entry
                    currentTitle = null
                    currentLogo = null
                    currentGroup = null
                    currentCountry = null
                }
            }
            channels
        }
    }

    private fun String.extract(key: String): String? {
        // Simple regex to find key="value"
        val regex = Regex("""$key\s*=\s*"([^"]*)"""")
        return regex.find(this)?.groupValues?.getOrNull(1)
    }

    private suspend fun fetchAndParseActivePlaylists(): List<M3uChannel> {
        val activeSources = getSources().filter { it.isActive }
        if (activeSources.isEmpty()) return emptyList()

        val allChannels = mutableListOf<M3uChannel>()

        activeSources.forEach { source ->
            try {
                Log.d(name, "Fetching playlist: ${source.url}")
                val response = app.get(source.url, timeout = 20_000) // 20 saniye timeout
                if (response.code == 200) {
                    val parsedChannels = parseM3u(response.text)
                    allChannels.addAll(parsedChannels)
                    Log.d(name, "Parsed ${parsedChannels.size} channels from ${source.url}")
                } else {
                     Log.e(name, "Error fetching playlist ${source.url}: HTTP ${response.code}")
                     showToast(context, "Liste alınamadı: ${source.name} (Hata: ${response.code})", ToastDuration.LONG)
                }

            } catch (e: Exception) {
                Log.e(name, "Error fetching or parsing playlist ${source.url}", e)
                showToast(context, "Liste hatası: ${source.name}", ToastDuration.LONG)
            }
        }
        return allChannels
    }


    override suspend fun loadMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val allChannels = fetchAndParseActivePlaylists()
        val homePageLists = mutableListOf<HomePageList>()

        // Group channels
        val groupedChannels = allChannels.groupBy { it.group }

        groupedChannels.forEach { (group, channels) ->
            val searchResponses = channels.mapNotNull { channel ->
                try {
                    val loadData = LoadData(
                        streamUrl = channel.streamUrl,
                        title = channel.title,
                        posterUrl = channel.logoUrl,
                        group = channel.group,
                        nation = channel.country
                    )
                    val encodedData = URLEncoder.encode(json.encodeToString(loadData), "UTF-8")

                    newLiveSearchResponse(
                        channel.title,
                        // URL içinde JSON taşımak yerine dataUrl kullanalım
                        this.name // `url` burada önemli değil, `dataUrl` kullanılacak
                    ) {
                        this.posterUrl = channel.logoUrl
                        // dataUrl'e kodlanmış veriyi ekleyelim
                        this.dataUrl = encodedData
                        this.quality = SearchQuality.HD // Varsayılan veya Unknown
                    }
                } catch (e: Exception) {
                    Log.e(name, "Error creating search response for ${channel.title}", e)
                    null
                }
            }
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList(group, searchResponses, isHorizontalImages = false))
            }
        }

        // Ayarlar Menüsü
         val settingsItems = listOf(
             NewButton("Listeleri Yönet", "settings://manage")
         )
         homePageLists.add(0, HomePageList("Ayarlar", settingsItems)) // Ayarları başa ekle


        return HomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun loadPage(url: String): PageResponse? {
        if (url == "settings://manage") {
            val sources = getSources()
            val items = mutableListOf<ListPage>()

            items.add(
                ListButton(
                    "Yeni Liste Ekle",
                    "settings://add",
                    icon = R.drawable.ic_baseline_add_24 // CloudStream'in yerleşik ikonu
                )
            )

            sources.forEach { source ->
                items.add(
                    ListButton(
                        "${if (source.isActive) "✅" else "❌"} ${source.name}",
                        "settings://toggle?url=${URLEncoder.encode(source.url, "UTF-8")}",
                         icon = R.drawable.ic_baseline_playlist_play_24, // Veya başka uygun ikon
                         description = source.url
                    )
                )
                 items.add(
                     ListButton(
                         "    Sil: ${source.name.take(20)}...", // Silme butonu için girinti
                         "settings://delete?url=${URLEncoder.encode(source.url, "UTF-8")}",
                         icon = R.drawable.ic_baseline_delete_outline_24
                     )
                 )
            }
            return ListPage(items)

        } else if (url == "settings://add") {
             val result = showInputDialog(
                 context,
                 title = "Yeni M3U Listesi Ekle",
                 label = "Liste URL",
                 placeholder = "https://...",
                 confirmText = "Ekle",
                 cancelText = "İptal"
            )

            if (result is InputDialogResult.Confirm && result.input.isNotBlank()) {
                val newUrl = result.input.trim()
                val sources = getSources().toMutableList()
                 if (sources.none { it.url == newUrl }) {
                     // İsim almak için ikinci bir dialog (opsiyonel)
                     val nameResult = showInputDialog(
                         context,
                         title = "Liste Adı (Opsiyonel)",
                         label = "Bu liste için bir isim girin (boş bırakırsanız URL kullanılır)",
                         placeholder = newUrl,
                         confirmText = "Kaydet",
                         cancelText = "Atla"
                     )
                     val listName = if (nameResult is InputDialogResult.Confirm && nameResult.input.isNotBlank()) {
                         nameResult.input.trim()
                     } else {
                         newUrl // İsim girilmezse URL'i kullan
                     }

                    sources.add(M3uSource(url = newUrl, name = listName, isActive = true))
                    saveSources(sources)
                    showToast(context, "Liste eklendi: $listName", ToastDuration.SHORT)
                    // Sayfayı yenilemek için null dönmek genellikle yeterlidir
                     return null
                 } else {
                     showToast(context, "Bu URL zaten ekli.", ToastDuration.SHORT)
                 }
            }
             return null // Dialog kapandığında sayfayı yenile

        } else if (url.startsWith("settings://toggle?url=")) {
            val encodedUrl = url.substringAfter("settings://toggle?url=")
            val targetUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            val sources = getSources().map {
                if (it.url == targetUrl) {
                    it.copy(isActive = !it.isActive)
                } else {
                    it
                }
            }
            saveSources(sources)
             return null // Sayfayı yenile

        } else if (url.startsWith("settings://delete?url=")) {
             val encodedUrl = url.substringAfter("settings://delete?url=")
             val targetUrl = URLDecoder.decode(encodedUrl, "UTF-8")
             val sourceToDelete = getSources().find { it.url == targetUrl }

             if (sourceToDelete != null) {
                 val confirmed = showConfirmationDialog(
                     context,
                     title = "Listeyi Sil",
                     message = "'${sourceToDelete.name}' listesini silmek istediğinizden emin misiniz?",
                     confirmText = "Sil",
                     cancelText = "İptal"
                 )

                 if (confirmed) {
                     val sources = getSources().filter { it.url != targetUrl }
                     saveSources(sources)
                     showToast(context, "Liste silindi: ${sourceToDelete.name}", ToastDuration.SHORT)
                 }
             }
             return null // Sayfayı yenile
        }
        return null
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val allChannels = fetchAndParseActivePlaylists()

        return allChannels
            .filter { it.title.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true) }
            .mapNotNull { channel ->
                try {
                     val loadData = LoadData(
                         streamUrl = channel.streamUrl,
                         title = channel.title,
                         posterUrl = channel.logoUrl,
                         group = channel.group,
                         nation = channel.country
                     )
                    val encodedData = URLEncoder.encode(json.encodeToString(loadData), "UTF-8")

                    newLiveSearchResponse(
                        channel.title,
                        this.name // url
                    ) {
                        this.posterUrl = channel.logoUrl
                        this.dataUrl = encodedData // Veriyi buraya koy
                        this.quality = SearchQuality.HD
                    }
                } catch (e: Exception) {
                    Log.e(name, "Error creating search response during search for ${channel.title}", e)
                    null
                }
            }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // load fonksiyonu artık gerekli değil gibi, çünkü bilgiyi SearchResponse'a gömdük.
    // Ancak API gerektiriyorsa boş veya temel bir şey döndürebilir.
    override suspend fun load(url: String): LoadResponse? {
       // Ana sayfa veya arama sonuçlarından gelen url burada işlenmeyecek.
       // dataUrl kullanıldığı için bu fonksiyonun çağrılması beklenmez.
       // Eğer çağrılırsa, belki bir hata durumu veya farklı bir akıştır.
        Log.w(name, "Load function called unexpectedly with url: $url")
        return null
    }


    override suspend fun loadLinks(
        data: String, // Bu bizim dataUrl'den gelen kodlanmış LoadData JSON'ımız
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val decodedData = URLDecoder.decode(data, "UTF-8")
            val loadData = json.decodeFromString<LoadData>(decodedData)

            callback(
                ExtractorLink(
                    source = this.name,
                    name = loadData.title, // Kanal adı
                    url = loadData.streamUrl, // Asıl yayın URL'si
                    referer = "", // Gerekirse referer ekle
                    quality = Qualities.Unknown.value // Kalite genellikle IPTV için bilinmez
                )
            )
            true
        } catch (e: Exception) {
            Log.e(name, "Error in loadLinks", e)
            false
        }
    }
}