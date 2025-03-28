package com.sinetech.latte

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class powerSinemaPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPreferences = context.getSharedPreferences("power_sinema_prefs", Context.MODE_PRIVATE)
        registerMainAPI(powerSinema(sharedPreferences))
    }
}