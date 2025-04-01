package com.sinetech.latte

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

@CloudstreamPlugin
class IPTVListemPlugin : Plugin() {
    companion object {
        private const val IPTV_LISTS_KEY = "iptv_lists"
        private const val SELECTED_LISTS_KEY = "selected_iptv_lists"
    }

    override fun load(context: Context) {
        // Register main API
        registerMainAPI(IPTVListem(context))
    }

    class IPTVListem(private val context: Context) : MainAPI() {
        override var name = "IPTVListem"
        override var mainUrl = ""
        override val supportedTypes = setOf(TvType.Live)
        override val hasMainPage = true
        override val hasQuickSearch = true
        override val hasDownloadSupport = false
        override val hasSettings = false

        private var channels = mutableListOf<IptvManager.Channel>()

        private fun getIptvLists(): List<String> {
            return getKey<List<String>>(IPTV_LISTS_KEY) ?: emptyList()
        }

        private fun getSelectedLists(): List<String> {
            return getKey<List<String>>(SELECTED_LISTS_KEY) ?: emptyList()
        }

        fun addIptvList(url: String) {
            val currentLists = getIptvLists().toMutableList()
            if (!currentLists.contains(url)) {
                currentLists.add(url)
                setKey(IPTV_LISTS_KEY, currentLists)
                Toast.makeText(context, "IPTV listesi eklendi", Toast.LENGTH_SHORT).show()
            }
        }

        fun removeIptvList(url: String) {
            val currentLists = getIptvLists().toMutableList()
            if (currentLists.remove(url)) {
                setKey(IPTV_LISTS_KEY, currentLists)
                // Remove from selected lists if present
                val selectedLists = getSelectedLists().toMutableList()
                selectedLists.remove(url)
                setKey(SELECTED_LISTS_KEY, selectedLists)
                Toast.makeText(context, "IPTV listesi kaldırıldı", Toast.LENGTH_SHORT).show()
            }
        }

        fun updateSelectedLists(selectedUrls: List<String>) {
            setKey(SELECTED_LISTS_KEY, selectedUrls)
        }

        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            channels.clear()
            val selectedLists = getSelectedLists()
            
            selectedLists.forEach { url ->
                try {
                    channels.addAll(IptvManager.parseM3uFile(url))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val homePageLists = IptvManager.createHomePageList(this, channels)
            return HomePageResponse(homePageLists)
        }

        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            val channel = channels.find { it.url == data }
            if (channel != null) {
                callback.invoke(IptvManager.createExtractorLink(channel))
                return true
            }
            return false
        }
    }
}