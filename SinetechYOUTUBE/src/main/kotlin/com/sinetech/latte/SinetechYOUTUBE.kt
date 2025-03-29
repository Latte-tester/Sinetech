package com.sinetech.latte

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo

class SinetechYOUTUBE(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl = "https://www.youtube.com/@sinetechone"
    override var name = "SinetechYOUTUBE"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = Jsoup.connect(mainUrl).get()
        val recentVideos = parseVideos(doc)
        
        val popularUrl = "$mainUrl/videos?view=0&sort=p&flow=grid"
        val popularDoc = Jsoup.connect(popularUrl).get()
        val popularVideos = parseVideos(popularDoc)
        
        val playlistsUrl = "$mainUrl/playlists"
        val playlistsDoc = Jsoup.connect(playlistsUrl).get()
        val playlists = playlistsDoc.select("div[id=contents] ytd-playlist-renderer").map { playlist ->
            val playlistId = playlist.attr("playlist-id")
            val title = playlist.select("h3 a").text()
            val thumbnail = playlist.select("img").attr("src")
            
            newMovieSearchResponse(
                title,
                LoadData(playlistId, title, thumbnail, "", false, 0L).toJson(),
                TvType.Movie
            ) {
                this.posterUrl = thumbnail
            }
        }

        return newHomePageResponse(
            listOf(
                HomePageList("Son Yüklenenler", recentVideos),
                HomePageList("En Popüler", popularVideos),
                HomePageList("Oynatma Listeleri", playlists)
            ),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?query=${query}"
        val doc = Jsoup.connect(searchUrl).get()
        return parseVideos(doc)
    }

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        val doc = Jsoup.connect("https://www.youtube.com/watch?v=${loadData.videoId}").get()

        val title = doc.select("meta[property=og:title]").attr("content")
        val description = doc.select("meta[property=og:description]").attr("content")
        val thumbnail = doc.select("meta[property=og:image]").attr("content")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            loadData.videoId
        ) {
            this.posterUrl = thumbnail
            this.plot = description
            this.rating = loadData.isWatched.toInt()
            this.duration = if (loadData.watchProgress > 0) (loadData.watchProgress / 1000).toInt() else null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val link = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(
                "https://www.youtube.com/watch?v=$data"
            )

            val extractor = object : YoutubeStreamExtractor(ServiceList.YouTube, link) {}
            extractor.fetchPage()

            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = extractor.hlsUrl,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )

            val subtitles = extractor.subtitlesDefault.filterNotNull()
            subtitles.mapNotNull {
                SubtitleFile(it.languageTag ?: return@mapNotNull null, it.content ?: return@mapNotNull null)
            }.forEach(subtitleCallback)

            return true
        } catch (e: Exception) {
            Log.e("SinetechYOUTUBE", "Error in loadLinks: ${e.message}", e)
            return false
        }
    }

    private fun parseVideos(doc: Document): List<SearchResponse> {
        return doc.select("ytd-rich-grid-media, ytd-video-renderer").mapNotNull { video ->
            val videoElement = video.select("a#video-title, a#thumbnail[href]").firstOrNull()
            val videoUrl = videoElement?.attr("href") ?: return@mapNotNull null
            val videoId = videoUrl.substringAfter("v=").substringBefore("&")
            if (videoId.isBlank()) return@mapNotNull null

            val title = videoElement.attr("title").ifEmpty { videoElement.text() }
            if (title.isBlank()) return@mapNotNull null
            
            val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val publishTime = video.select("span.ytd-video-meta-block[aria-label], span.inline-metadata-item").firstOrNull()?.text() ?: ""

            val watchKey = "watch_${videoId.hashCode()}"
            val progressKey = "progress_${videoId.hashCode()}"
            val isWatched = sharedPref?.getBoolean(watchKey, false) ?: false
            val watchProgress = sharedPref?.getLong(progressKey, 0L) ?: 0L

            newMovieSearchResponse(
                title,
                LoadData(videoId, title, thumbnail, publishTime, isWatched, watchProgress).toJson(),
                TvType.Movie
            ) {
                this.posterUrl = thumbnail
            }
        }
    }

    data class LoadData(
        val videoId: String,
        val title: String,
        val thumbnail: String,
        val publishTime: String,
        val isWatched: Boolean = false,
        val watchProgress: Long = 0L
    )

    private fun Boolean.toInt() = if (this) 5 else 0
}