package com.sinetech.latte

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

data class TmdbMovieData(
    val movieId: String,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val director: String?,
    val cast: List<String>,
    val overview: String?,
    val genres: List<String>,
    val rating: Double?,
    val posterPath: String?,
    val backdropPath: String?,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(json: String): TmdbMovieData = parseJson(json)
    }

    fun toJson(): String = toJson()
}