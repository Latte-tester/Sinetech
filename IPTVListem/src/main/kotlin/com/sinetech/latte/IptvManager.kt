package com.sinetech.latte

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

object IptvManager {
    data class Channel(
        val name: String,
        val url: String,
        val group: String = "Genel",
        val logo: String = "",
        val vlcOpts: Map<String, String> = emptyMap()
    )

    suspend fun parseM3uFile(url: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val connection = URL(url).openConnection()
            BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                var line: String?
                var currentChannel: Channel? = null
                var currentGroup = "Genel"
                val currentVlcOpts = mutableMapOf<String, String>()

                while (reader.readLine().also { line = it } != null) {
                    line?.trim()?.let { trimmedLine ->
                        when {
                            trimmedLine.startsWith("#EXTVLCOPT:") -> {
                                val optMatch = "#EXTVLCOPT:([^=]+)=(.+)".toRegex().find(trimmedLine)
                                if (optMatch != null) {
                                    val key = optMatch.groupValues[1].trim()
                                    val value = optMatch.groupValues[2].trim()
                                    currentVlcOpts[key] = value
                                }
                            }
                            trimmedLine.startsWith("#EXTINF:") -> {
                                val groupMatch = "group-title=\"([^\"]*)\"".toRegex().find(trimmedLine)
                                val nameMatch = ",[^,]*$".toRegex().find(trimmedLine)
                                val logoMatch = "tvg-logo=\"([^\"]*)\"".toRegex().find(trimmedLine)

                                currentGroup = groupMatch?.groupValues?.get(1) ?: currentGroup
                                val name = nameMatch?.value?.substring(1)?.trim() ?: ""
                                val logo = logoMatch?.groupValues?.get(1) ?: ""

                                currentChannel = Channel(name, "", currentGroup, logo, currentVlcOpts.toMap())
                                currentVlcOpts.clear()
                            }
                            !trimmedLine.startsWith("#") && currentChannel != null -> {
                                channels.add(currentChannel!!.copy(url = trimmedLine))
                                currentChannel = null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    fun createHomePageList(channels: List<Channel>): List<HomePageList> {
        return channels.groupBy { it.group }.map { (group, groupChannels) ->
            HomePageList(
                name = group,
                list = groupChannels.map { channel ->
                    LiveTvSearchResponse(
                        name = channel.name,
                        url = channel.url,
                        apiName = "IPTVListem",
                        type = TvType.Live,
                        posterUrl = channel.logo
                    )
                }
            )
        }
    }

    fun createExtractorLink(channel: Channel): ExtractorLink {
        return ExtractorLink(
            source = "IPTVListem",
            name = channel.name,
            url = channel.url,
            referer = channel.vlcOpts["http-referrer"] ?: "",
            quality = Qualities.Unknown.value,
            isM3u8 = true
        )
    }
}