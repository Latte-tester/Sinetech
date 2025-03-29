package com.sinetech.latte

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SinetechYOUTUBEPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPreferences = context.getSharedPreferences("sinetech_youtube_prefs", Context.MODE_PRIVATE)
        registerMainAPI(SinetechYOUTUBE(sharedPreferences))
    }
}