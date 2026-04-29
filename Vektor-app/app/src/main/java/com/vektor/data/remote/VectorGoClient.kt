package com.vektor.data.remote

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.http.Body
import retrofit2.http.POST

interface VectorGoApi {
    @POST("/emergency")
    suspend fun sendEmergencyPayload(@Body payload: String): retrofit2.Response<Unit>
}

object VectorGoClient {
    private const val BASE_URL = "https://api.vektor.app/"
    
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()

    val api: VectorGoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(VectorGoApi::class.java)
    }
}
