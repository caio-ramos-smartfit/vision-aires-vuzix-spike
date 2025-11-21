package com.caio.vuzixhello.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

private const val BASE_URL = "https://workout.bioritmo.com.br/api/v1/"
private const val TOKEN = "1a5d60e8b33bc60a7bce29b953546fca"

interface BioritmoApiService {
    @GET("people/info")
    suspend fun getPersonInfo(
        @Query("integration_id") integrationId: String,
        @Header("Authorization") token: String = "Token token=\"$TOKEN\""
    ): BioritmoResponse
}

object BioritmoApi {
    val retrofitService: BioritmoApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(BioritmoApiService::class.java)
    }
}
