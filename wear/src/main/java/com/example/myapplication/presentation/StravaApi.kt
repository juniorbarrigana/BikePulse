package com.example.myapplication.presentation

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import java.time.LocalDate

data class BikeActivity(
    val id: Long,
    val date: LocalDate,
    val distanceKm: Double,
    val durationMinutes: Int,
    val elevationGain: Float = 0f,
    val calories: Float = 0f,
    val name: String = "",
    val activityCount: Int = 1
)

data class StravaActivity(
    val id: Long,
    val name: String,
    val distance: Float, 
    val moving_time: Int, 
    val total_elevation_gain: Float,
    val calories: Float?,
    val kilojoules: Float?,
    val type: String,
    val start_date_local: String
)

data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long
)

interface StravaApi {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code_verifier") codeVerifier: String? = null
    ): TokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): TokenResponse

    @GET("api/v3/athlete/activities")
    suspend fun getActivities(
        @Header("Authorization") token: String,
        @Query("before") before: Long? = null,
        @Query("after") after: Long? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<StravaActivity>

    @GET("api/v3/athletes/{id}/stats")
    suspend fun getAthleteStats(
        @Header("Authorization") token: String,
        @retrofit2.http.Path("id") id: Long
    ): AthleteStats

    @GET("api/v3/athlete")
    suspend fun getAuthenticatedAthlete(
        @Header("Authorization") token: String
    ): StravaAthlete
}

data class StravaAthlete(
    val id: Long
)

data class AthleteStats(
    val ytd_ride_totals: ActivityTotal
)

data class ActivityTotal(
    val count: Int,
    val distance: Float,
    val moving_time: Int,
    val elevation_gain: Float
)
