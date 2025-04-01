package com.sinetech.latte

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class M3uPlaylistItem(
    val url: String,
    val title: String,
    val attributes: Map<String, String>
)

data class LoadData(
    val streamUrl: String,
    val title: String,
    val posterUrl: String,
    val group: String,
    val nation: String
)

class OzellestirilmisTV(private val sharedPref: SharedPreferences?) : MainAPI() {
    override var mainUrl              = ""
    override var name                 = "OzellestirilmisTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Live)

    private val settingsManager = SettingsManager(sharedPref)

    class SettingsManager(private val sharedPref: SharedPreferences?) {
        companion object {
            private const val PLAYLIST_URLS_KEY = "playlist_urls"
            private const val ENABLED_PLAYLISTS_KEY = "enabled_playlists"
        }

        fun savePlaylistUrl(url: String) {
            val urls = getPlaylistUrls().toMutableSet()
            urls.add(url)
            sharedPref?.edit()?.putStringSet(PLAYLIST_URLS_KEY, urls)?.apply()
        }

        fun removePlaylistUrl(url: String) {
            val urls = getPlaylistUrls().toMutableSet()
            urls.remove(url)
            sharedPref?.edit()?.putStringSet(PLAYLIST_URLS_KEY, urls)?.apply()
        }

        fun getPlaylistUrls(): Set<String> {
            return sharedPref?.getStringSet(PLAYLIST_URLS_KEY, emptySet()) ?: emptySet()
        }

        fun setPlaylistEnabled(url: String, enabled: Boolean) {
            val enabledUrls = getEnabledPlaylists().toMutableSet()
            if (enabled) {
                enabledUrls.add(url)
            } else {
                enabledUrls.remove(url)
            }
            sharedPref?.edit()?.putStringSet(ENABLED_PLAYLISTS_KEY, enabledUrls)?.apply()
        }

        fun getEnabledPlaylists(): Set<String> {
            return sharedPref?.getStringSet(ENABLED_PLAYLISTS_KEY, emptySet()) ?: emptySet()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val enabledPlaylists = settingsManager.getEnabledPlaylists()
        if (enabledPlaylists.isEmpty()) {
            return HomePageResponse(emptyList())
        }

        val allChannels = mutableListOf<IptvPlaylistParser.M3uPlaylistItem>()
        enabledPlaylists.forEach { url ->
            try {
                val playlist = IptvPlaylistParser().parseM3U(app.get(url).text)
                allChannels.addAll(playlist.items)
            } catch (e: Exception) {
                Log.e("OzellestirilmisTV", "Error parsing playlist $url: ${e.message}")
            }
        }

        val groupedChannels = allChannels.groupBy { it.attributes["group-title"]?.toString()?.trim() ?: "Diğer" }

        val homePageLists = mutableListOf<HomePageList>()

        groupedChannels.forEach { (group, channels) ->
            val searchResponses = channels.map { channel ->
                val streamUrl = channel.url.toString()
                val channelName = channel.title.toString()
                val posterUrl = channel.attributes["tvg-logo"]?.toString() ?: ""
                val nation = channel.attributes["tvg-country"]?.toString() ?: "TR"

                newLiveSearchResponse(
                    channelName,
                    LoadData(streamUrl, channelName, posterUrl, group, nation).toJson(),
                    TvType.Live
                ) {
                    this.posterUrl = posterUrl
                    this.lang = nation
                }
            }
            
            if (searchResponses.isNotEmpty()) {
                homePageLists.add(HomePageList(group, searchResponses, isHorizontalImages = true))
            }
        }

        return HomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val enabledPlaylists = settingsManager.getEnabledPlaylists()
        if (enabledPlaylists.isEmpty()) {
            return emptyList()
        }

        val allChannels = mutableListOf<IptvPlaylistParser.M3uPlaylistItem>()
        enabledPlaylists.forEach { url ->
            try {
                val playlist = IptvPlaylistParser().parseM3U(app.get(url).text)
                allChannels.addAll(playlist.items)
            } catch (e: Exception) {
                Log.e("OzellestirilmisTV", "Error parsing playlist $url: ${e.message}")
            }
        }

        return allChannels.filter { it.title.toString().lowercase().contains(query.lowercase()) }.map { channel ->
            val streamUrl = channel.url.toString()
            val channelName = channel.title.toString()
            val posterUrl = channel.attributes["tvg-logo"]?.toString() ?: ""
            val chGroup = channel.attributes["group-title"]?.toString() ?: "Diğer"
            val nation = channel.attributes["tvg-country"]?.toString() ?: "TR"

            newLiveSearchResponse(
                channelName,
                LoadData(streamUrl, channelName, posterUrl, chGroup, nation).toJson(),
                TvType.Live
            ) {
                this.posterUrl = posterUrl
                this.lang = nation
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        return LiveStreamLoadResponse(
            name = loadData.title,
            url = loadData.streamUrl,
            apiName = this.name,
            dataUrl = loadData.streamUrl,
            posterUrl = loadData.posterUrl,
            type = TvType.Live
        )
    }

    data class LoadData(
        val streamUrl: String,
        val title: String,
        val posterUrl: String,
        val group: String,
        val nation: String
    )
}