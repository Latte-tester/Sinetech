package com.sinetech.latte

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.io.File

class TmdbDataManager(private val context: Context) {
    private val tmdbApiKey = System.getenv("TMDB_SECRET_API")
    private val cacheFile = File(context.filesDir, "tmdb_movie_cache.json")
    private val movieCache = mutableMapOf<String, TmdbMovieData>()

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
            val json = toJson(movieCache)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e("TmdbDataManager", "Cache kaydedilirken hata oluştu: ${e.message}")
        }
    }

    suspend fun updateMovieData(movieTitle: String) {
        try {
            // TMDB API'den film bilgilerini al
            // API entegrasyonu burada yapılacak
            // Örnek veri yapısı:
            val movieData = TmdbMovieData(
                movieId = "sample_id",
                title = movieTitle,
                originalTitle = movieTitle,
                year = 2024,
                director = "Örnek Yönetmen",
                cast = listOf("Oyuncu 1", "Oyuncu 2"),
                overview = "Film özeti burada olacak",
                genres = listOf("Aksiyon", "Macera"),
                rating = 8.5,
                posterPath = null,
                backdropPath = null
            )

            movieCache[movieTitle] = movieData
            saveCache()
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