package com.sinetech.latte

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.schemaStripRegex
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory

open class YouTubeExtractor(private val hls: Boolean) : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"

    constructor() : this(true)

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val link =
                YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(
                    url.replace(schemaStripRegex, "")
                )

            val extractor = object : YoutubeStreamExtractor(
                ServiceList.YouTube,
                link
            ) {}

            try {
                extractor.fetchPage()
            } catch (e: Exception) {
                Log.e("YoutubeExtractor", "Error fetching page: ${e.message}")
                throw e
            }

            val hlsUrl = try {
                extractor.hlsUrl
            } catch (e: Exception) {
                Log.e("YoutubeExtractor", "Error getting HLS URL: ${e.message}")
                null
            }
            
            val dashUrl = try {
                extractor.dashMpdUrl
            } catch (e: Exception) {
                Log.e("YoutubeExtractor", "Error getting DASH URL: ${e.message}")
                null
            }
            
            val streamUrl = if (hls) hlsUrl else dashUrl
            
            if (streamUrl.isNullOrEmpty()) {
                Log.e("YoutubeExtractor", "Could not extract stream URL")
                if (hls && dashUrl != null) {
                    Log.d("YoutubeExtractor", "Falling back to DASH URL")
                    return@getUrl
                }
                throw Exception("Could not extract any valid stream URL")
            }

            Log.d("YoutubeExtractor", "Is HLS enabled: $hls")
            Log.d("YoutubeExtractor", "Stream Url: $streamUrl")

            if (hls) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        streamUrl,
                        referer ?: "",
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            } else {
                val stream = M3u8Helper.generateM3u8(this.name, hlsUrl, "")
                stream.forEach {
                    callback.invoke(it)
                }
                val subtitles = try {
                    extractor.subtitlesDefault.filterNotNull()
                } catch (e: Exception) {
                    logError(e)
                    emptyList()
                }
                subtitles.mapNotNull {
                    SubtitleFile(it.languageTag ?: return@mapNotNull null, it.content ?: return@mapNotNull null)
                }.forEach(subtitleCallback)
            }
        } catch (e: Exception) {
            Log.e("YoutubeExtractor", "Error in getUrl: ${e.message}")
            throw e
        }
    }
}