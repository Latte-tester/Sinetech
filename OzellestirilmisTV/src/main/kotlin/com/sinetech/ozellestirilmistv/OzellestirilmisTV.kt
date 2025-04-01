package com.sinetech.latte

import com.sinetech.latte.common.PluginBase

class OzellestirilmisTV : PluginBase() {
    override fun initialize() {
        // TODO: IPTV listelerini yükleme ve birleştirme mantığı
    }

    override fun getSettingsIcon(): Int {
        // TODO: Ayarlar simgesi için drawable resource
        return R.drawable.ic_settings
    }
}