package com.sinetech.latte

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

class YouTubeChannelProvider : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "CloudStream"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = "tr"

    private val ytParser = YouTubeParser(this.name)

    companion object{
        const val MAIN_URL = "https://www.youtube.com"
        const val CHANNEL_URL = "https://www.youtube.com/@sinetechone"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelContent = ytParser.channelToSearchResponseList(CHANNEL_URL, page)
        return HomePageResponse(listOf(channelContent))
    }

    override suspend fun load(url: String): LoadResponse {
        val video = ytParser.channelToLoadResponse(url)
        return video
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {

        YouTubeExtractor().getUrl(data, "", subtitleCallback, callback)
        return true
    }
}