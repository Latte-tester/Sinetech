package com.sinetech.latte

import android.content.SharedPreferences
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.amap

class YouTubeProvider(language: String, private val sharedPrefs: SharedPreferences?) : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "SinetechOne"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = language

    private val ytParser = YouTubeParser(this.name)
    private val CHANNEL_ID = "@sinetechone"

    companion object {
        const val MAIN_URL = "https://www.youtube.com"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sections = mutableListOf<HomePageList>()
        
        // Son Yüklenenler
        val latestVideos = ytParser.channelToSearchResponseList("$MAIN_URL/$CHANNEL_ID/videos", page)
        latestVideos?.let { sections.add(HomePageList("Son Yüklenenler", it)) }
        
        // En Popüler
        val popularVideos = ytParser.channelToSearchResponseList("$MAIN_URL/$CHANNEL_ID/videos?sort=p", page)
        popularVideos?.let { sections.add(HomePageList("En Popüler", it)) }
        
        // Oynatma Listeleri
        val playlists = ytParser.channelToSearchResponseList("$MAIN_URL/$CHANNEL_ID/playlists", page)
        playlists?.let { sections.add(HomePageList("Oynatma Listeleri", it)) }
        if (sections.isEmpty()) {
            sections.add(
                HomePageList(
                    "Henüz içerik yüklenemedi",
                    emptyList()
                )
            )
        }
        return newHomePageResponse(
            sections, true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return ytParser.channelToSearchResponseList("$MAIN_URL/$CHANNEL_ID/search?query=$query", 1) ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val video = ytParser.videoToLoadResponse(url)
        return video
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val hls = sharedPrefs?.getBoolean("hls", true) ?: true
        YouTubeExtractor(hls).getUrl(data, "", subtitleCallback, callback)
        return true
    }
}