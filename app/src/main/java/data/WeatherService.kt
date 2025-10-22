// WeatherService.kt
package com.example.project_2.data.weather

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherMain(val temp: Double)
data class WeatherDesc(val main: String, val description: String)
data class WeatherResp(val weather: List<WeatherDesc>, val main: WeatherMain)

interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun currentByLatLng(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "kr"
    ): WeatherResp
}

object WeatherService {
    private const val BASE = "https://api.openweathermap.org/"
    private var api: OpenWeatherApi? = null

    fun init(openWeatherApiKey: String) {
        val auth = Interceptor { chain ->
            val url = chain.request().url.newBuilder()
                .addQueryParameter("appid", openWeatherApiKey)
                .build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        val client = OkHttpClient.Builder().addInterceptor(auth).build()
        api = Retrofit.Builder()
            .baseUrl(BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    data class Current(val tempC: Double, val condition: String, val icon: String?)
    suspend fun currentByLatLng(lat: Double, lng: Double): Current? {
        val res = runCatching { api?.currentByLatLng(lat, lng) }.getOrNull() ?: return null
        val cond = res.weather.firstOrNull()?.main ?: "Unknown"
        val icon = null // 필요 시 확장
        return Current(res.main.temp, cond, icon)
    }
}
