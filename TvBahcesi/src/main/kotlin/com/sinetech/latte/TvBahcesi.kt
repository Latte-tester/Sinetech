package com.sinetech.latte

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Duration
import java.time.Instant

class TvBahcesi : MainAPI() {
    override var name = "TV Bahçesi"
    override var mainUrl = "https://raw.githubusercontent.com/TVGarden/tv-garden-channel-list/main/channels/raw/countries"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true

    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private var lastUpdate: Instant = Instant.EPOCH
    private val updateInterval = Duration.ofHours(2)
    private val defaultPosterUrl = "https://raw.githubusercontent.com/GitLatte/m3ueditor/refs/heads/site/images/kanal-gorselleri/referans/isimsizkanal.png"

    data class Channel(
        val nanoid: String,
        val name: String,
        val iptv_urls: List<String>,
        val youtube_urls: List<String>,
        val language: String,
        val country: String,
        val isGeoBlocked: Boolean
    )

    private fun fetchChannels(): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val request = Request.Builder()
                .url(mainUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to fetch country list: ${response.code}")
                }

                val body = response.body?.string() ?: throw Exception("Empty response body")
                val countryFiles = try {
                    parseJson<List<String>>(body)
                } catch (e: Exception) {
                    throw Exception("Failed to parse country list: ${e.message}")
                }

                for (file in countryFiles) {
                    if (!file.endsWith(".json")) continue

                    val countryRequest = Request.Builder()
                        .url("$mainUrl/$file")
                        .build()

                    client.newCall(countryRequest).execute().use { countryResponse ->
                        if (!countryResponse.isSuccessful) {
                            continue
                        }

                        val countryBody = countryResponse.body?.string() ?: continue
                        try {
                            val countryChannels = mapper.readValue<List<Channel>>(countryBody)
                            channels.addAll(countryChannels)
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log the error or handle it appropriately
            e.printStackTrace()
        }

        return channels
    }

    private fun generateM3U(channels: List<Channel>): String {
        val m3u = StringBuilder("#EXTM3U\n")

        channels.forEach { channel ->
            channel.iptv_urls.forEachIndexed { index, url ->
                if (url.endsWith(".m3u8")) {
                    val channelName = if (channel.iptv_urls.size > 1) {
                        "${channel.name} (${index + 1})"
                    } else {
                        channel.name
                    }

                    m3u.append("#EXTINF:-1 tvg-id=\"${channel.nanoid}\" ")
                    m3u.append("tvg-name=\"$channelName\" ")
                    m3u.append("tvg-country=\"${channel.country}\" ")
                    m3u.append("tvg-language=\"${channel.language}\"\n")
                    m3u.append("$url\n")
                }
            }
        }

        return m3u.toString()
    }

    private fun updateChannelList() {
        val now = Instant.now()
        if (Duration.between(lastUpdate, now) >= updateInterval) {
            val channels = fetchChannels()
            val m3uContent = generateM3U(channels)

            File("channels/tvbahcesi.m3u").apply {
                parentFile?.mkdirs()
                writeText(m3uContent)
            }

            lastUpdate = now
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        updateChannelList()
        val channels = fetchChannels()
        
        return HomePageResponse(
            channels.groupBy { it.country }.map { (country, countryChannels) ->
                HomePageList(
                    name = country.uppercase(),
                    list = countryChannels.flatMap { channel ->
                        channel.iptv_urls
                            .filter { it.endsWith(".m3u8") }
                            .mapIndexed { index, url ->
                                val title = if (channel.iptv_urls.size > 1) {
                                    "${channel.name} (${index + 1})"
                                } else {
                                    channel.name
                                }

                                newLiveSearchResponse(
                                    name = title,
                                    url = url,
                                    type = TvType.Live
                                ) {
                                    this.posterUrl = defaultPosterUrl
                                }
                            }
                    },
                    isHorizontalImages = true
                )
            },
            hasNext = false
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = data,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = emptyMap()
            )
        )
        return true
    }
}