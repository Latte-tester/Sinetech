package com.sinetech.latte

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SinetechYoutube : MainAPI() {
    override var mainUrl = "https://iv.ggtyler.dev" // Hiç açılmazsa https://redirect.invidious.io/ bu adrese girip durumlarını kontrol ettikten sonra  url değiştirebiliriz.
    override var name = "(▷) Sinetech Youtube"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Others, TvType.Podcast)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelId = "UC3JhJrIYm9blzw5bsedENrg"
        android.util.Log.d("SinetechYoutube", "getMainPage başladı - channelId: $channelId")
        
        // En yeni videoları al
        val newestUrl = "$mainUrl/channel/$channelId?sort_by=newest"
        
        val newestDocument = app.get(newestUrl).document
        val newestVideos = newestDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { videoElement ->
            val titleElement = videoElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null
            
            val videoUrl = videoElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val videoId = videoUrl.substringAfter("watch?v=")
            
            val thumbnailUrl = videoElement.selectFirst(".thumbnail img")?.attr("src")
            
            newMovieSearchResponse(
                title,
                "$mainUrl/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        // En son paylaşılan videoyu al (sadece ilk sayfada göster)
        val latestVideo = if (page == 1 && newestVideos.isNotEmpty()) listOf(newestVideos[0]) else emptyList()
        
        // Popüler videoları al
        val popularUrl = "$mainUrl/channel/$channelId?sort_by=popular"
        
        val popularDocument = app.get(popularUrl).document
        val popularVideos = popularDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { videoElement ->
            val titleElement = videoElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null
            
            val videoUrl = videoElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val videoId = videoUrl.substringAfter("watch?v=")
            
            val thumbnailUrl = videoElement.selectFirst(".thumbnail img")?.attr("src")
            
            newMovieSearchResponse(
                title,
                "$mainUrl/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        // Oynatma listelerini al
        val playlistsUrl = "$mainUrl/channel/$channelId/playlists"
        
        val playlistsDocument = app.get(playlistsUrl).document
        val playlists = playlistsDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { playlistElement ->
            val titleElement = playlistElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null
            
            val playlistUrl = playlistElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val thumbnailUrl = playlistElement.selectFirst(".thumbnail img")?.attr("src")
            
            newMovieSearchResponse(
                title,
                "$mainUrl$playlistUrl",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else null
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }

        // Shorts videoları al
        val shortsUrl = "$mainUrl/channel/$channelId/shorts"
        
        val shortsDocument = app.get(shortsUrl).document
        val shorts = shortsDocument.select(".pure-u-1.pure-u-md-1-4").mapNotNull { shortElement ->
            val titleElement = shortElement.selectFirst(".video-card-row a p")
            val title = titleElement?.text() ?: return@mapNotNull null
            
            val videoUrl = shortElement.selectFirst(".thumbnail a")?.attr("href") ?: return@mapNotNull null
            val videoId = videoUrl.substringAfter("watch?v=")
            
            val thumbnailUrl = shortElement.selectFirst(".thumbnail img")?.attr("src")
            
            newMovieSearchResponse(
                title,
                "$mainUrl/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = if (thumbnailUrl != null) "$mainUrl$thumbnailUrl" else "$mainUrl/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }
        
        return newHomePageResponse(
            listOf(
                HomePageList(
                    "En Yeni Youtube Videosu!",
                    latestVideo,
                    true
                ),
                HomePageList(
                    "En son paylaşılan @SinetechONE Youtube Videoları",
                    newestVideos,
                    true
                ),
                HomePageList(
                    "Popüler @SinetechONE Youtube Videoları",
                    popularVideos,
                    true
                ),
                HomePageList(
                    "@SinetechONE Youtube Oynatma Listeleri",
                    playlists,
                    true
                ),
                HomePageList(
                    "@SinetechONE Youtube Shorts",
                    shorts,
                    true
                )
            ),
            false // Sayfalama özelliği kaldırıldı
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/api/v1/search?q=${query.encodeUri()}&page=1&type=video&fields=videoId,title").text
        )
        return res?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        // Oynatma listesi URL'si kontrolü
        if (url.contains("/playlist")) {
            val playlistId = Regex("playlist\\?list=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
            val playlistInfo = app.get("$mainUrl/api/v1/playlists/$playlistId?fields=title,description,videos").text
            val playlistData = tryParseJson<PlaylistEntry>(playlistInfo)

            if (playlistData != null) {
                val episodes = playlistData.videos.mapIndexed { index, video ->
                    Episode(
                        data = video.videoId,
                        name = video.title,
                        season = null,
                        episode = index + 1,
                        posterUrl = "$mainUrl/vi/${video.videoId}/maxres.jpg",
                        rating = null,
                        description = null
                    )
                }

                return newTvSeriesLoadResponse(
                    playlistData.title,
                    url,
                    TvType.Podcast,
                    episodes
                ) {
                    this.plot = playlistData.description
                    this.posterUrl = episodes.firstOrNull()?.posterUrl
                }
            }
            return null
        }

        // Normal video URL'si işleme
        val videoId = Regex("watch\\?v=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
        val res = tryParseJson<VideoEntry>(
            app.get("$mainUrl/api/v1/videos/$videoId?fields=videoId,title,description,recommendedVideos,author,authorThumbnails,formatStreams,lengthSeconds,viewCount,publishedText").text
        )
        return res?.toLoadResponse(this)
    }

    private data class SearchEntry(
        val title: String,
        val videoId: String,
        val lengthSeconds: Int,
        val viewCount: Int,
        val publishedText: String,
        val author: String,
        val authorId: String,
        val videoThumbnails: List<Thumbnail>
    ) {
        fun toSearchResponse(provider: SinetechYoutube): SearchResponse {
            android.util.Log.d("SinetechYoutube", "Video dönüştürülüyor - başlık: $title, id: $videoId")
            return provider.newMovieSearchResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Podcast
            ) {
                this.posterUrl = videoThumbnails.firstOrNull()?.let { "${provider.mainUrl}${it.url}" } ?: "${provider.mainUrl}/vi/$videoId/maxres.jpg"
                this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD
            }
        }
    }

    private data class VideoEntry(
        val title: String,
        val description: String,
        val videoId: String,
        val recommendedVideos: List<SearchEntry>,
        val author: String,
        val authorThumbnails: List<Thumbnail>,
        val lengthSeconds: Int = 0,
        val viewCount: Int = 0,
        val publishedText: String = "",
        val likeCount: Int = 0,
        val genre: String = "",
        val license: String = ""
    ) {
        suspend fun toLoadResponse(provider: SinetechYoutube): LoadResponse {
            return provider.newMovieLoadResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Podcast,
                videoId
            ) {
                val duration = if (lengthSeconds > 0) {
                    val hours = lengthSeconds / 3600
                    val minutes = (lengthSeconds % 3600) / 60
                    val seconds = lengthSeconds % 60
                    if (hours > 0) {
                        String.format("%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%02d:%02d", minutes, seconds)
                    }
                } else ""

                val views = if (viewCount > 0) {
                    when {
                        viewCount >= 1_000_000 -> String.format("%.1fM görüntülenme", viewCount / 1_000_000.0)
                        viewCount >= 1_000 -> String.format("%.1fB görüntülenme", viewCount / 1_000.0)
                        else -> "Görüntülenme: $viewCount"
                    }
                } else ""

                fun convertPublishedText(publishedText: String): String {
                    val today = LocalDate.now() // Mevcut tarihi al
                
                    // Regex ile tüm zaman ifadelerini tek seferde kontrol et
                    val match = Regex("(\\d+) (days?|weeks?|months?|years?) ago").find(publishedText)
                    match?.let {
                        val amount = it.groupValues[1].toInt() // Süre miktarı (örn: "3")
                        val unit = it.groupValues[2] // Süre birimi (örn: "weeks" veya "month")
                
                        // Süre birimine göre tarihten eksiltme yap
                        val actualDate = when {
                            unit.startsWith("day") -> today.minusDays(amount.toLong())
                            unit.startsWith("week") -> today.minusWeeks(amount.toLong())
                            unit.startsWith("month") -> today.minusMonths(amount.toLong())
                            unit.startsWith("year") -> today.minusYears(amount.toLong())
                            else -> today
                        }
                
                        // Formatlı tarih olarak döndür
                        return actualDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    }
                
                    // Eğer tarih zaten "19 Apr 2025" gibi net bir formatta geliyorsa, direkt çevir
                    return publishedText.split(" ").let { parts ->
                        if (parts.size >= 3) {
                            val turkishMonths = mapOf(
                                "Jan" to "Ocak", "Feb" to "Şubat", "Mar" to "Mart", "Apr" to "Nisan",
                                "May" to "Mayıs", "Jun" to "Haziran", "Jul" to "Temmuz", "Aug" to "Ağustos",
                                "Sep" to "Eylül", "Oct" to "Ekim", "Nov" to "Kasım", "Dec" to "Aralık"
                            )
                            val day = parts[0]
                            val month = turkishMonths[parts[1]] ?: parts[1]
                            val year = parts[2]
                            "$day $month $year"
                        } else publishedText
                    }
                }
                
                val turkishDate = convertPublishedText(publishedText)


                val detailText = buildString {
                    if (publishedText.isNotEmpty()) append("📅 <b>Yayınlanma Tarihi:</b> $turkishDate<br>")
                    if (duration.isNotEmpty()) append("⏱️ <b>Video Süresi:</b> $duration<br>")
                    if (views.isNotEmpty()) append("👁️ <b>$views</b> <br>")
                    if (likeCount > 0) append("👍 <b>Beğeni:</b> $likeCount<br>")
                    if (genre.isNotEmpty()) append("🎬 <b>Tür:</b> $genre<br>")
                    if (license.isNotEmpty()) append("📜 <b>Lisans:</b> $license<br>")
                    append("<br>")
                }

                this.plot = buildString {
                    append("📢 Bilgilendirme: Lütfen videoları @SinetechONE youtube kanalından da izlemeyi unutmayın. Bu eklenti ile izleyeceğiniz videolar size kolaylık sağlaması açısından eklenmiş olsa da youtube kanalı üzerinden de izlemeniz hem kanal içeriklerinin hem de web sitesi hizmetlerinin devam edebilmesi açısından oldukça önem taşıyor. Videoyu beğenmeyi, abone olmayı ve yorum yazmayı lütfen unutmayın ☺️")
                    if (detailText.isNotEmpty()) {
                        append("<br><br>")
                        append(detailText)
                    }
                }
                this.posterUrl = "${provider.mainUrl}/vi/$videoId/maxres.jpg"
                this.recommendations = recommendedVideos.map { video ->
                    provider.newMovieSearchResponse(
                        video.title,
                        "${provider.mainUrl}/watch?v=${video.videoId}",
                        TvType.Podcast
                    ) {
                        this.posterUrl = video.videoThumbnails.firstOrNull()?.url?.let { if (it.startsWith("/")) "${provider.mainUrl}$it" else it } ?: "${provider.mainUrl}/vi/${video.videoId}/maxres.jpg"
                        this.posterHeaders = mapOf()
                this.quality = SearchQuality.HD 
                    }
                }
                this.actors = listOf(
                    ActorData(
                        Actor(author, authorThumbnails.lastOrNull()?.url ?: ""),
                        roleString = "Kanal Sahibi"
                    )
                )
            }
        }
    }


    private data class Thumbnail(
        val url: String
    )

    private data class PlaylistEntry(
        val title: String,
        val description: String,
        val videos: List<PlaylistVideo>
    )

    private data class PlaylistVideo(
        val title: String,
        val videoId: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = data
        val videoInfo = app.get("$mainUrl/api/v1/videos/$videoId").text
        val videoDocument = app.get("$mainUrl/watch?v=$videoId").document
        
        // HTML'den ek bilgileri al
        val likeCount = videoDocument.selectFirst("p#likes")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
        val genre = videoDocument.selectFirst("p#genre")?.text()?.substringAfter("Tür: ")?.trim() ?: ""
        val license = videoDocument.selectFirst("p#license")?.text()?.substringAfter("Lisans: ")?.trim() ?: ""

        val videoData = tryParseJson<VideoEntry>(videoInfo)?.copy(
            likeCount = likeCount,
            genre = genre,
            license = license
        )

        if (videoData != null) {
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = "$mainUrl/api/manifest/dash/id/$videoId",
                    referer = mainUrl,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.DASH
                )
            )
            return true
        }
        return false
    }
}