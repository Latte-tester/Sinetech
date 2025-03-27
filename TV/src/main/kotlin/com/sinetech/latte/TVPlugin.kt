package com.sinetech.latte

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity.activity

@CloudstreamPlugin
class TVPlugin : Plugin() {
    private val sharedPref = activity?.getSharedPreferences("TV", Context.MODE_PRIVATE)
    private val playlistsToLang = mapOf(
        "iptvsevenler.m3u" to "tr",
        "power-yabanci-dizi.m3u" to "tr",
        "someone.m3u" to "tr",
        "power-sinema.m3u" to "tr"
    )

    init {
        sharedPref?.edit()?.clear()?.apply()
    }

    override fun load(context: Context) {
        val playlistSettings = playlistsToLang.keys.associateWith {
            sharedPref?.getBoolean(it, false) ?: false
        }
        val selectedPlaylists = playlistSettings.filter { it.value }.keys
        val selectedLanguages = selectedPlaylists.map { playlistsToLang[it] }
        val lang = if(selectedLanguages.isNotEmpty()){
            if(selectedLanguages.all { it == selectedLanguages.first() && it != null }){
                selectedLanguages.first()!! } else{ "un" }
        } else{ "un" }

        registerMainAPI(TV(selectedPlaylists.toList(), lang, sharedPref))

        val activity = context as AppCompatActivity
        openSettings = {
            val frag = Settings(this, sharedPref, playlistsToLang.keys.toList())
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}