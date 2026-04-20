package com.example.myapplication.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class YearlyStats(
    val year: Int,
    val count: Int,
    val distanceKm: Double,
    val elevationGain: Float,
    val calories: Float,
    val durationMinutes: Int
)

sealed class StravaState {
    object Idle : StravaState()
    object Loading : StravaState()
    data class Success(
        val activities: List<BikeActivity>,
        val yearlyStats: List<YearlyStats> = emptyList(),
        val rawActivities: List<BikeActivity> = emptyList()
    ) : StravaState()
    data class Error(val message: String) : StravaState()
    data class NeedsAuth(val authUrl: String) : StravaState()
}

class StravaViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = application.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)
    
    val CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET
    private val REDIRECT_URI = "https://wear.googleapis.com/3p_auth/com.example.myapplication"
    
    var currentCodeVerifier: CodeVerifier? = null
        private set

    var uiState by mutableStateOf<StravaState>(StravaState.Idle)
        private set

    var lastWeeklyKm by mutableStateOf<Double?>(null)
        private set

    var lastWeeklyDateRange by mutableStateOf<String?>(null)
        private set

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val stravaApi = retrofit.create(StravaApi::class.java)

    init {
        checkExistingToken()
    }

    private fun checkExistingToken() {
        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            fetchLastWeekActivities(accessToken)
        } else {
            startLogin()
        }
    }

    fun refreshData() {
        val accessToken = prefs.getString("access_token", null)
        if (accessToken != null) {
            fetchLastWeekActivities(accessToken)
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        startLogin()
        lastWeeklyKm = null
        lastWeeklyDateRange = null
    }

    fun getAuthUrl(): String {
        val verifier = CodeVerifier()
        currentCodeVerifier = verifier
        // Salva para persistência caso o app seja fechado pelo sistema
        prefs.edit().putString("pkce_verifier_value", verifier.value).apply()
        
        val encodedUri = Uri.encode(REDIRECT_URI)
        return "https://www.strava.com/oauth/mobile/authorize" +
                "?client_id=$CLIENT_ID" +
                "&redirect_uri=$encodedUri" +
                "&response_type=code" +
                "&approval_prompt=force" +
                "&scope=read,profile:read_all,activity:read_all"
    }

    fun startLogin() {
        uiState = StravaState.NeedsAuth(getAuthUrl())
    }

    fun loginWithCode(code: String) {
        android.util.Log.d("StravaAuth", "loginWithCode called with code: $code")
        uiState = StravaState.Loading
        val verifierValue = currentCodeVerifier?.value ?: prefs.getString("pkce_verifier_value", null)
        
        viewModelScope.launch {
            try {
                val response = stravaApi.exchangeToken(
                    clientId = CLIENT_ID, 
                    clientSecret = CLIENT_SECRET, 
                    code = code.trim(),
                    codeVerifier = verifierValue
                )
                saveTokens(response.access_token, response.refresh_token)
                fetchLastWeekActivities(response.access_token)
            } catch (e: Exception) {
                android.util.Log.e("StravaAuth", "Token exchange failed", e)
                uiState = StravaState.Error("Login failed: ${e.localizedMessage}")
            }
        }
    }

    private fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString("access_token", access).putString("refresh_token", refresh).apply()
    }

    fun fetchLastWeekActivities(accessToken: String) {
        android.util.Log.d("StravaAuth", "fetchLastWeekActivities starting...")
        uiState = StravaState.Loading
        viewModelScope.launch {
            try {
                // Buscamos as últimas 100 atividades para garantir que pegamos algo
                val response = stravaApi.getActivities("Bearer $accessToken", perPage = 100)
                android.util.Log.d("StravaAuth", "Total activities received: ${response.size}")
                
                val allBikeActivities = response
                    .filter { 
                        // Strava type pode ser "Ride", "EBikeRide", "VirtualRide", "MountainBikeRide"
                        val type = it.type.lowercase()
                        val isBike = type.contains("ride") || type.contains("bike")
                        
                        android.util.Log.d("StravaAuth", "Activity: ${it.name} | Type: ${it.type} | isBike: $isBike")
                        isBike
                    }
                    .map {
                        BikeActivity(
                            id = it.id,
                            date = LocalDate.parse(it.substringDate()),
                            distanceKm = (it.distance / 1000.0),
                            durationMinutes = it.moving_time / 60,
                            elevationGain = it.total_elevation_gain,
                            calories = it.calories ?: it.kilojoules ?: 0f,
                            name = it.name
                        )
                    }

                android.util.Log.d("StravaAuth", "Bike activities found: ${allBikeActivities.size}")

                if (allBikeActivities.isEmpty()) {
                    uiState = StravaState.Success(emptyList(), emptyList())
                    return@launch
                }

                // Definimos o "resumo semanal" baseado na data da atividade mais recente encontrada
                val mostRecentDate = allBikeActivities.maxOf { it.date }
                val startOfWeek = mostRecentDate.minusDays(6)
                
                // Pegamos as atividades dessa "semana" (dos últimos 7 dias a partir da última pedalada)
                val weeklyActivities = allBikeActivities.filter { 
                    !it.date.isBefore(startOfWeek) && !it.date.isAfter(mostRecentDate)
                }

                // Agrupamos por data para o resumo
                val displayList = (if (weeklyActivities.isNotEmpty()) weeklyActivities else allBikeActivities.take(10))
                    .groupBy { it.date }
                    .map { (date, acts) ->
                        BikeActivity(
                            id = acts.first().id,
                            date = date,
                            distanceKm = acts.sumOf { it.distanceKm },
                            durationMinutes = acts.sumOf { it.durationMinutes },
                            elevationGain = acts.sumOf { it.elevationGain.toDouble() }.toFloat(),
                            calories = acts.sumOf { it.calories.toDouble() }.toFloat(),
                            name = if (acts.size > 1) "${acts.size} Activities" else acts.first().name,
                            activityCount = acts.size
                        )
                    }
                    .sortedByDescending { it.date }

                lastWeeklyKm = weeklyActivities.sumOf { it.distanceKm }
                val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", java.util.Locale.US)
                lastWeeklyDateRange = "${startOfWeek.format(dateFormatter)} - ${mostRecentDate.format(dateFormatter)}"

                // Estatísticas anuais
                val yearlyStatsList = allBikeActivities
                    .groupBy { it.date.year }
                    .map { (year, acts) ->
                        YearlyStats(year, acts.size, acts.sumOf { it.distanceKm }, acts.sumOf { it.elevationGain.toDouble() }.toFloat(), acts.sumOf { it.calories.toDouble() }.toFloat(), acts.sumOf { it.durationMinutes })
                    }
                    .sortedByDescending { it.year }.take(6)

                uiState = StravaState.Success(displayList, yearlyStatsList, allBikeActivities)
                android.util.Log.d("StravaAuth", "UI State updated to Success with ${displayList.size} items")
            } catch (e: Exception) {
                android.util.Log.e("StravaAuth", "Error fetching activities", e)
                if (e.message?.contains("401") == true) tryRefreshToken()
                else uiState = StravaState.Error("Error: ${e.message}")
            }
        }
    }

    private fun tryRefreshToken() {
        val refreshToken = prefs.getString("refresh_token", null) ?: run { uiState = StravaState.Idle; return }
        viewModelScope.launch {
            try {
                val response = stravaApi.refreshToken(CLIENT_ID, CLIENT_SECRET, refreshToken)
                saveTokens(response.access_token, response.refresh_token)
                fetchLastWeekActivities(response.access_token)
            } catch (e: Exception) { uiState = StravaState.Idle }
        }
    }

    private fun StravaActivity.substringDate() = if (start_date_local.length >= 10) start_date_local.substring(0, 10) else start_date_local
}
