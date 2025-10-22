package com.example.project_2.domain.repo

import com.example.project_2.domain.model.*

interface TravelRepository {
    suspend fun getWeather(region: String): WeatherInfo?
    suspend fun getWeatherByLatLng(lat: Double, lng: Double): WeatherInfo?

    // 기본 추천 (GPT 재랭크 없음)
    suspend fun recommend(filter: FilterState, weather: WeatherInfo? = null): RecommendationResult

    // ✅ GPT 재랭크 추천
    suspend fun recommendWithGpt(
        filter: FilterState,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Int = 2500,
        candidateSize: Int = 15
    ): RecommendationResult
}
