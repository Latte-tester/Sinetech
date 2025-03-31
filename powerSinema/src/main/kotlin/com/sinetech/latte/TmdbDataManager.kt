package com.sinetech.latte

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.File

class TmdbDataManager(private val context: Context) {
    private val tmdbApi = TmdbApi()
    private val cacheFile = File(context.filesDir, "tmdb_movie_cache.json")
    private val movieCache = mutableMapOf<String, TmdbMovieData>()
    private val cacheValidityPeriod = 24 * 60 * 60 * 1000 // 24 saat (milisaniye cinsinden)

    init {
        loadCache()
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cacheData = parseJson<Map<String, TmdbMovieData>>(json)
                movieCache.putAll(cacheData)
            } catch (e: Exception) {
                Log.e("TmdbDataManager", "Cache yüklenirken hata oluştu: ${e.message}")
            }
        }
    }

    private fun saveCache() {
        try {
            val json = movieCache.toJson()
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e("TmdbDataManager", "Cache kaydedilirken hata oluştu: ${e.message}")
        }
    }

    suspend fun updateMovieData(movieTitle: String) {
        try {
            val cachedMovie = movieCache[movieTitle]
            val currentTime = System.currentTimeMillis()

            // Cache'de film varsa ve son güncelleme üzerinden 24 saat geçmediyse, cache'den al
            if (cachedMovie != null && (currentTime - cachedMovie.lastUpdated) < cacheValidityPeriod) {
                return
            }

            // TMDB API'den film bilgilerini al
            val movieData = tmdbApi.searchMovie(movieTitle)
            if (movieData != null) {
                movieCache[movieTitle] = movieData
                saveCache()
            }
        } catch (e: Exception) {
            Log.e("TmdbDataManager", "Film verisi güncellenirken hata oluştu: ${e.message}")
        }
    }

    fun getMovieData(movieTitle: String): TmdbMovieData? {
        return movieCache[movieTitle]
    }

    fun clearCache() {
        movieCache.clear()
        cacheFile.delete()
    }
}