package com.sinetech.latte

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities

class YouTubeParser {
    companion object {
        private val youtubeExtractor = YouTubeExtractor()

        suspend fun getVideoLinks(
            url: String,
            referer: String? = null,
            subtitleCallback: (SubtitleFile) -> Unit = {},
            callback: (ExtractorLink) -> Unit = {}
        ) {
            youtubeExtractor.getUrl(url, referer, subtitleCallback, callback)
        }
    }
}