package com.sinetech.latte

import com.lagradost.cloudstream3.utils.ExtractorLink

class YouTubeChannelProvider {
    companion object {
        private const val SINETECH_CHANNEL_URL = "https://www.youtube.com/@sinetechone"
        private val youtubeParser = YouTubeParser()

        suspend fun getChannelVideos(
            callback: (ExtractorLink) -> Unit = {}
        ) {
            // Kanal videolarını NewPipe API'si üzerinden çekme işlemi
            val service = ServiceList.YouTube
            val channelUrl = SINETECH_CHANNEL_URL
            val channelHandler = service.channelLinkHandlerFactory.fromUrl(channelUrl)
            val channelExtractor = service.getChannelExtractor(channelHandler)
            channelExtractor.fetchPage()

            // Son yüklenen videoları al
            val videos = channelExtractor.initialPage.items
            videos.forEach { video ->
                val videoUrl = video.url
                if (videoUrl != null) {
                    getVideoFromUrl(videoUrl, callback)
                }
            }
        }

        suspend fun getVideoFromUrl(
            url: String,
            callback: (ExtractorLink) -> Unit = {}
        ) {
            if (url.contains("@sinetechone")) {
                youtubeParser.getVideoLinks(url, null, callback)
            }
        }
    }
}