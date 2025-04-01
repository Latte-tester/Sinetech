package com.sinetech.latte

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("IPTV_PREFS", Context.MODE_PRIVATE)
    }

    fun saveSelectedLists(selectedUrls: Set<String>) {
        prefs.edit().putStringSet("SELECTED_LISTS", selectedUrls).apply()
    }

    fun getSelectedLists(): Set<String> {
        return prefs.getStringSet("SELECTED_LISTS", mutableSetOf()) ?: mutableSetOf()
    }

    fun addNewList(url: String) {
        val current = getSelectedLists().toMutableSet()
        current.add(url)
        saveSelectedLists(current)
    }
}