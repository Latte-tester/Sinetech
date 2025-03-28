package com.sinetech.latte

import android.util.Log
import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream

class powerSinema(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = "https://raw.githubusercontent.com/GitLatte/patr0n/site/lists/power-sinema.m3u"
    override var name                 = "powerSinema"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = false
    override val supportedTypes       = setOf(TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return newHomePageResponse(
            kanallar.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show  = group.value.map { kanal ->
                    val streamurl   = kanal.url.toString()
                    val channelname = kanal.title.toString()
                    val posterurl   = kanal.attributes["tvg-logo"].toString()
                    val chGroup     = kanal.attributes["group-title"].toString()
                    val nation      = kanal.attributes["tvg-country"].toString()

                    val watchKey = "watch_${streamurl.hashCode()}"
                    val progressKey = "progress_${streamurl.hashCode()}"
                    val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
                    val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

                    newLiveSearchResponse(
                        channelname,
                        LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress).toJson(),
                        type = TvType.Movie
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }


                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return kanallar.items.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { kanal ->
            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()

            val watchKey = "watch_${streamurl.hashCode()}"
            val progressKey = "progress_${streamurl.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            newLiveSearchResponse(
                channelname,
                LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress).toJson(),
                type = TvType.Movie
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private var cachedPlaylist: Playlist? = null
    private suspend fun getPlaylist(): Playlist {
    if (cachedPlaylist != null) {
        Log.d("powerSinema", "Returning cached playlist")
        return cachedPlaylist!!
    }
    Log.d("powerSinema", "Fetching and parsing playlist from $mainUrl")
    return try {
        val inputStream = app.get(mainUrl).body // .body KULLAN
        val parsedPlaylist = IptvPlaylistParser().parseM3U(inputStream) // InputStream ile parse et
        cachedPlaylist = parsedPlaylist // Önbelleğe al
        Log.d("powerSinema", "Playlist fetched. Items: ${parsedPlaylist.items.size}")
        parsedPlaylist
    } catch (e: Exception) {
        Log.e("powerSinema", "Failed to fetch/parse playlist", e)
        cachedPlaylist = null // Hata durumunda önbelleği temizle
        Playlist() // Boş liste döndür
    }
    }


    override suspend fun load(url: String): LoadResponse {
    // url burada muhtemelen JSON verisidir
    val loadData = fetchDataFromUrlOrJson(url) // JSON'ı parse et veya URL'den fallback yap

    // GERÇEK stream URL'sini kullanarak anahtarları oluştur
    val actualStreamUrl = loadData.url
    val watchKey = "watch_${actualStreamUrl.hashCode()}"
    // val progressKey = "progress_${actualStreamUrl.hashCode()}" // Zaman damgası için (gerekirse)

    // İzleme durumunu burada, doğru anahtarla kontrol et
    val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
    // val watchTimestamp = sharedPref?.getLong(progressKey, 0L) ?: 0L // Zaman damgasını istersen alabilirsin

    val nation: String = if (loadData.group == "NSFW") {
        "⚠️🔞🔞🔞 » ${loadData.group} | ${loadData.nation} « 🔞🔞🔞⚠️"
    } else {
        "» ${loadData.group} | ${loadData.nation} «"
    }

    // --- Öneri Mantığı ---
    // DİKKAT: Burası hala app.get().text kullanıyor, getPlaylist() ile düzeltilmeli!
    val recommendations = mutableListOf<SearchResponse>() // MovieLoadResponse değil, SearchResponse olmalı
    try {
        val playlist = getPlaylist() // Önbellekten/yeniden çekilmiş listeyi al
        for (kanal in playlist.items) { // Ayrıştırılmış öğeler üzerinde döngü
            if (kanal.attributes["group-title"]?.toString() == loadData.group) {
                val rcStreamUrl = kanal.url.toString()
                // Kendisini önermemek için kontrol et
                if (rcStreamUrl == actualStreamUrl) continue

                val rcChannelName = kanal.title.toString()
                val rcPosterUrl = kanal.attributes["tvg-logo"] ?: "" // Varsayılan boş string
                val rcChGroup = kanal.attributes["group-title"] ?: "Diğer" // Varsayılan
                val rcNation = kanal.attributes["tvg-country"] ?: "TR" // Varsayılan

                // Öneriler için izleme durumu genellikle gerekmez, JSON verisini minimum tutalım
                recommendations.add(newMovieSearchResponse( // newLiveSearchResponse yerine newMovieSearchResponse
                    rcChannelName,
                    // Öneri için LoadData'nın basitleştirilmiş JSON'unu geç
                    LoadData(rcStreamUrl, rcChannelName, rcPosterUrl, rcChGroup, rcNation).toJson(),
                    type = TvType.Movie
                ) {
                    this.posterUrl = rcPosterUrl
                    // this.lang = rcNation // İsteğe bağlı
                })
            }
        }
    } catch (e: Exception) {
        Log.e("powerSinema", "Error fetching recommendations", e)
        // Hata durumunda öneriler boş kalır
    }
    // --- Öneri Mantığı Sonu ---


    // loadData.url yerine actualStreamUrl kullanıldı
    return newMovieLoadResponse(loadData.title, actualStreamUrl, TvType.Movie, actualStreamUrl) {
        this.posterUrl = loadData.poster
        this.plot = nation
        this.tags = listOfNotNull(loadData.group, loadData.nation.takeIf { it.isNotBlank() && it != "TR"}) // Daha temiz tag'ler
        this.recommendations = recommendations // Oluşturulan önerileri ata
        this.rating = if (isWatched) 5 else null // İzlenmemişse null daha iyi olabilir
        // this.duration = null // IPTV için süre bilinmiyor, bu yüzden ayarlamıyoruz
        // this.watchProgress = watchProgress // BU SATIRI SİL
    }
}

// Diğer fonksiyonlardaki (getMainPage, search, loadLinks, fetchDataFromUrlOrJson)
// TÜM app.get(mainUrl).text çağrılarını getPlaylist() kullanacak şekilde güncellemeyi unutmayın!

// Örneğin fetchDataFromUrlOrJson içindeki fallback kısmı:
private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
    try {
        // Önce JSON olarak ayrıştırmayı dene
        return parseJson<LoadData>(data)
    } catch (e: Exception) {
        // JSON değilse, ham URL varsay (fallback)
        Log.w("powerSinema", "fetchDataFromUrlOrJson treating as URL: $data")
        val playlist = getPlaylist() // Önbelleğe alınmış/yeni listeyi al
        val kanal = playlist.items.firstOrNull { it.url == data }
            ?: throw RuntimeException("URL $data playlist içinde bulunamadı") // Bulamazsa hata fırlat

        val streamurl = kanal.url.toString()
        val channelname = kanal.title.toString()
        val posterurl = kanal.attributes["tvg-logo"] ?: ""
        val chGroup = kanal.attributes["group-title"] ?: "Diğer"
        val nation = kanal.attributes["tvg-country"] ?: "TR"

        // BU URL için izleme durumunu al
        val watchKey = "watch_${streamurl.hashCode()}"
        val progressKey = "progress_${streamurl.hashCode()}"
        val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
        val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L // Zaman damgası

        // LoadData'ya izleme durumunu ekle (eğer LoadData içinde tutuluyorsa)
        return LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress)
    }
}

// LoadData sınıfı aynı kalabilir veya isWatched/watchProgress kaldırılabilir
// Eğer sadece load içinde kullanılacaksa kaldırılabilirler. Şimdilik kalsın.
data class LoadData(
    val url: String,
    val title: String,
    val poster: String,
    val group: String,
    val nation: String,
    val isWatched: Boolean = false,
    val watchProgress: Long = 0L // Zaman damgası
)

// loadLinks içinde de app.get().text yerine getPlaylist() kullanın (sadece header gerekiyorsa)
// ve SharedPreferences güncellemesini loadData.url ile yapın.
override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    val loadData = try {
        parseJson<LoadData>(data)
    } catch (e: Exception) {
        Log.e("powerSinema", "Failed to parse LoadData JSON in loadLinks", e)
        return false // JSON parse edilemezse başarısız ol
    }
    Log.d("IPTV", "loadLinks loadData » $loadData")

    // Header'lar gerçekten gerekliyse playlist'i al, yoksa boş bırak
    var headers = emptyMap<String, String>()
    var referrer = ""
    try {
        val playlist = getPlaylist() // Önbelleğe alınmış listeyi kullan
        val kanal = playlist.items.firstOrNull { it.url == loadData.url }
        if (kanal != null) {
            headers = kanal.headers
            referrer = kanal.headers["referrer"] ?: ""
            Log.d("powerSinema", "Found channel in playlist for headers: ${kanal.title}")
        } else {
            Log.w("powerSinema", "Channel not found in playlist for headers: ${loadData.url}")
        }
    } catch (e: Exception) {
        Log.e("powerSinema", "Error getting headers from playlist in loadLinks", e)
    }


    // İzleme durumunu loadData.url kullanarak güncelle
    val watchKey = "watch_${loadData.url.hashCode()}"
    // val progressKey = "progress_${loadData.url.hashCode()}" // Zaman damgası için
    sharedPref?.edit()?.apply {
        putBoolean(watchKey, true)
        // putLong(progressKey, System.currentTimeMillis()) // Zaman damgasını kaydet
        apply()
    }


    callback.invoke(
        ExtractorLink(
            source  = this.name,
            name    = loadData.title, // Film başlığını kullan
            url     = loadData.url,
            headers = headers, // Bulunan header'ları kullan
            referer = referrer, // Bulunan referrer'ı kullan
            quality = Qualities.Unknown.value,
            isM3u8  = loadData.url.contains(".m3u8", ignoreCase = true) // Basit kontrol
        )
    )

    return true
}

    data class LoadData(val url: String, val title: String, val poster: String, val group: String, val nation: String, val isWatched: Boolean = false, val watchProgress: Long = 0L)

    private suspend fun fetchDataFromUrlOrJson(data: String): LoadData {
        if (data.startsWith("{")) {
            return parseJson<LoadData>(data)
        } else {
            val kanallar = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
            val kanal    = kanallar.items.first { it.url == data }

            val streamurl   = kanal.url.toString()
            val channelname = kanal.title.toString()
            val posterurl   = kanal.attributes["tvg-logo"].toString()
            val chGroup     = kanal.attributes["group-title"].toString()
            val nation      = kanal.attributes["tvg-country"].toString()
            val watchKey = "watch_${data.hashCode()}"
            val progressKey = "progress_${data.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            return LoadData(streamurl, channelname, posterurl, chGroup, nation, isWatched, watchProgress)
        }
    }
}

data class Playlist(
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val title: String?                  = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String>    = emptyMap(),
    val url: String?                    = null,
    val userAgent: String?              = null
)

class IptvPlaylistParser {

    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<PlaylistItem> = mutableListOf()
        var currentIndex = 0

        var line: String? = reader.readLine()

        while (line != null) {
            if (line.isNotEmpty()) {
                if (line.startsWith(EXT_INF)) {
                    val title      = line.getTitle()
                    val attributes = line.getAttributes()

                    playlistItems.add(PlaylistItem(title, attributes))
                } else if (line.startsWith(EXT_VLC_OPT)) {
                    val item      = playlistItems[currentIndex]
                    val userAgent = item.userAgent ?: line.getTagValue("http-user-agent")
                    val referrer  = line.getTagValue("http-referrer")

                    val headers = mutableMapOf<String, String>()

                    if (userAgent != null) {
                        headers["user-agent"] = userAgent
                    }

                    if (referrer != null) {
                        headers["referrer"] = referrer
                    }

                    playlistItems[currentIndex] = item.copy(
                        userAgent = userAgent,
                        headers   = headers
                    )
                } else {
                    if (!line.startsWith("#")) {
                        val item       = playlistItems[currentIndex]
                        val url        = line.getUrl()
                        val userAgent  = line.getUrlParameter("user-agent")
                        val referrer   = line.getUrlParameter("referer")
                        val urlHeaders = if (referrer != null) {item.headers + mapOf("referrer" to referrer)} else item.headers

                        playlistItems[currentIndex] = item.copy(
                            url       = url,
                            headers   = item.headers + urlHeaders,
                            userAgent = userAgent ?: item.userAgent
                        )
                        currentIndex++
                    }
                }
            }

            line = reader.readLine()
        }
        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val urlRegex     = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex     = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()

        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex      = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()

        return attributesString
            .split(Regex("\\s"))
            .mapNotNull {
                val pair = it.split("=")
                if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
            }
            .toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)

        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U     = "#EXTM3U"
        const val EXT_INF     = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {

    class InvalidHeader : PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}
