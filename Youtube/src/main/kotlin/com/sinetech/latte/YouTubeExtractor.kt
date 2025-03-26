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

open class YouTubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"

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

            val streamUrl = try {
                extractor.hlsUrl ?: extractor.dashMpdUrl
            } catch (e: Exception) {
                Log.e("YoutubeExtractor", "Error getting stream URL: ${e.message}")
                throw e
            }
            
            if (streamUrl.isNullOrEmpty()) {
                Log.e("YoutubeExtractor", "Could not extract stream URL")
                throw Exception("Could not extract any valid stream URL")
            }

            Log.d("YoutubeExtractor", "Stream Url: $streamUrl")

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