package com.sinetech.latte

import android.util.Log
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TmdbApi {
    private val tmdbApiKey = BuildConfig.TMDB_SECRET_API
    private val baseUrl = "https://api.themoviedb.org/3"
    private val client = OkHttpClient()
    private val language = "tr-TR"

    suspend fun searchMovie(title: String): TmdbMovieData? {
        try {
            val searchUrl = "$baseUrl/search/movie?api_key=$tmdbApiKey&language=$language&query=${title.encodeUrl()}"
            val response = makeRequest(searchUrl)
            val jsonObject = JSONObject(response)
            val results = jsonObject.getJSONArray("results")

            if (results.length() > 0) {
                val movieJson = results.getJSONObject(0)
                val movieId = movieJson.getString("id")
                return getMovieDetails(movieId)
            }
        } catch (e: Exception) {
            Log.e("TmdbApi", "Film arama hatası: ${e.message}")
        }
        return null
    }

    private suspend fun getMovieDetails(movieId: String): TmdbMovieData? {
        try {
            val detailsUrl = "$baseUrl/movie/$movieId?api_key=$tmdbApiKey&language=$language&append_to_response=credits"
            val response = makeRequest(detailsUrl)
            val movieJson = JSONObject(response)

            val director = movieJson.getJSONObject("credits")
                .getJSONArray("crew")
                .let { crew ->
                    (0 until crew.length())
                        .map { crew.getJSONObject(it) }
                        .firstOrNull { it.getString("job") == "Director" }
                        ?.getString("name")
                }

            val cast = movieJson.getJSONObject("credits")
                .getJSONArray("cast")
                .let { cast ->
                    (0 until minOf(cast.length(), 5))
                        .map { cast.getJSONObject(it).getString("name") }
                }

            val genres = movieJson.getJSONArray("genres")
                .let { genres ->
                    (0 until genres.length())
                        .map { genres.getJSONObject(it).getString("name") }
                }

            return TmdbMovieData(
                movieId = movieId,
                title = movieJson.getString("title"),
                originalTitle = movieJson.getString("original_title"),
                year = movieJson.getString("release_date").split("-").firstOrNull()?.toIntOrNull(),
                director = director,
                cast = cast,
                overview = movieJson.getString("overview"),
                genres = genres,
                rating = movieJson.getDouble("vote_average"),
                posterPath = if (!movieJson.isNull("poster_path")) {
                    "https://image.tmdb.org/t/p/w500${movieJson.getString("poster_path")}"
                } else null,
                backdropPath = if (!movieJson.isNull("backdrop_path")) {
                    "https://image.tmdb.org/t/p/original${movieJson.getString("backdrop_path")}"
                } else null
            )
        } catch (e: Exception) {
            Log.e("TmdbApi", "Film detayları alınırken hata oluştu: ${e.message}")
            return null
        }
    }

    private fun makeRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API isteği başarısız: ${response.code}")
            response.body?.string() ?: throw Exception("Boş yanıt alındı")
        }
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}