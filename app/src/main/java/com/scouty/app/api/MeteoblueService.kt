package com.scouty.app.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MeteoblueService {

    @GET("packages/{packages}")
    suspend fun getForecast(
        @Path("packages") packages: String = "basic-1h_basic-day_current_clouds-1h_sunmoon",
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("asl") asl: Int?,
        @Query("apikey") apiKey: String,
        @Query("format") format: String = "json"
    ): Response<MeteoblueResponse>

    @GET("en/server/search/query3")
    suspend fun searchLocations(
        @Query("query") query: String,
        @Query("apikey") apiKey: String
    ): Response<MeteoblueLocationSearchResponse>

    companion object {
        const val BASE_URL = "https://my.meteoblue.com/"
    }
}
