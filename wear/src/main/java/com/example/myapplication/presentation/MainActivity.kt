package com.example.myapplication.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.myapplication.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material3.TextButton
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import androidx.wear.remote.interactions.RemoteActivityHelper
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main Activity for the Wear OS application.
 * Handles the lifecycle and entry point for the Strava Bike App.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: StravaViewModel by viewModels()
    private lateinit var remoteActivityHelper: RemoteActivityHelper
    private lateinit var remoteAuthClient: RemoteAuthClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        remoteActivityHelper = RemoteActivityHelper(this)
        remoteAuthClient = RemoteAuthClient.create(this)
        
        // Handle initial intent if app was opened via deep link
        handleIntent(intent)

        setContent {
            StravaBikeApp(viewModel) { url ->
                try {
                    // Try to get the verifier from ViewModel or SharedPreferences
                    val savedVerifier = viewModel.prefs.getString("pkce_verifier_value", null)
                    val verifier = viewModel.currentCodeVerifier 
                        ?: if (savedVerifier != null) CodeVerifier(savedVerifier) else CodeVerifier()
                    
                    val authRequest = OAuthRequest.Builder(this@MainActivity)
                        .setAuthProviderUrl(Uri.parse(url))
                        .setClientId(viewModel.CLIENT_ID)
                        .setCodeChallenge(CodeChallenge(verifier))
                        .build()

                    remoteAuthClient.sendAuthorizationRequest(authRequest, 
                        mainExecutor, 
                        object : RemoteAuthClient.Callback() {
                            override fun onAuthorizationResponse(
                                request: OAuthRequest,
                                response: OAuthResponse
                            ) {
                                val responseUrl = response.responseUrl
                                if (responseUrl != null) {
                                    handleIntent(Intent(Intent.ACTION_VIEW, responseUrl))
                                }
                            }

                            override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                                android.util.Log.e("StravaAuth", "Auth Error: $errorCode")
                                runOnUiThread {
                                    android.widget.Toast.makeText(this@MainActivity, "Auth Error: $errorCode", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    android.widget.Toast.makeText(this, "Check your phone to authorize", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("StravaAuth", "Failed to build AuthRequest", e)
                    android.widget.Toast.makeText(this, "Error starting login: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        android.util.Log.d("StravaAuth", "Received intent: $data")
        
        if (data != null) {
            val code = data.getQueryParameter("code")
            val path = data.path ?: ""
            
            // More flexible verification to accept Google and Samsung redirects
            val isAuthRedirect = path.contains("3p_auth") || 
                                 data.host == "wear.googleapis.com" || 
                                 data.scheme == "bikeapp" || 
                                 data.host == "localhost"

            if (code != null && isAuthRedirect) {
                android.util.Log.d("StravaAuth", "Found code: $code. Starting token exchange...")
                viewModel.loginWithCode(code)
            } else {
                android.util.Log.w("StravaAuth", "Intent received but code is null or path doesn't match. Path: $path")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the app comes to foreground
        viewModel.refreshData()
    }

    override fun onDestroy() {
        if (::remoteAuthClient.isInitialized) {
            remoteAuthClient.close()
        }
        super.onDestroy()
    }
}

// App Colors
val BikeBlue = Color(0xFF00E5FF)
val BikeGreen = Color(0xFF00FF88)
val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF1A1A1A)

@Composable
fun StravaBikeApp(viewModel: StravaViewModel, onOpenUrl: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val uiState = viewModel.uiState
    val weeklyKm = viewModel.lastWeeklyKm
    var showCodeEntry by remember { mutableStateOf(false) }
    var selectedDateActivities by remember { mutableStateOf<List<BikeActivity>?>(null) }

    // Reset scroll to top when data is successfully loaded (e.g. after splash or resume)
    LaunchedEffect(uiState) {
        if (uiState is StravaState.Success) {
            listState.animateScrollToItem(0)
        }
    }

    MaterialTheme(
        colors = Colors(
            primary = BikeBlue,
            background = DarkBg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            Scaffold(
                timeText = { if (selectedDateActivities == null) TimeText() },
                vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
                positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
            ) {
                if (uiState is StravaState.Loading) {
                    // Show animation while fetching data
                    BikeRunningAnimation(weeklyKm, viewModel.lastWeeklyDateRange)
                } else if (uiState is StravaState.NeedsAuth) {
                    // Centralized login screen
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "BIKEPULSE",
                                style = MaterialTheme.typography.caption2,
                                color = BikeBlue,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = { onOpenUrl(uiState.authUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "LOGIN ON PHONE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(top = 15.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
                        autoCentering = null
                    ) {
                        when (uiState) {
                            is StravaState.Success -> {
                                item {
                                    Text(
                                        text = "BIKEPULSE",
                                        style = MaterialTheme.typography.caption2,
                                        color = BikeBlue,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                item { SummaryCard(uiState.activities, viewModel.lastWeeklyDateRange) }

                                if (uiState.activities.isEmpty()) {
                                    item { Text("No activities found.", style = MaterialTheme.typography.caption2) }
                                } else {
                                    items(uiState.activities) { activity ->
                                        BikeActivityCard(activity) {
                                            // Handle multiple activities on the same day
                                            selectedDateActivities = if (activity.activityCount > 1) {
                                                uiState.rawActivities.filter { it.date == activity.date }
                                            } else {
                                                listOf(activity)
                                            }
                                        }
                                    }
                                }

                                // Show yearly statistics panels
                                uiState.yearlyStats.forEach { stats ->
                                    item { YearStatsCard(stats) }
                                }

                                item { Spacer(modifier = Modifier.height(10.dp)) }
                                
                                item {
                                    Chip(
                                        onClick = { viewModel.logout() },
                                        label = { Text("LOGOUT", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        colors = ChipDefaults.secondaryChipColors(),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    )
                                }
                            }
                            is StravaState.Error -> {
                                item { Text(uiState.message, color = Color.Red, textAlign = TextAlign.Center) }
                                item { Chip(onClick = { viewModel.startLogin() }, label = { Text("TRY AGAIN") }) }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Detail view when a day with multiple activities is selected
            selectedDateActivities?.let { activities ->
                ActivitiesDayDetailOverlay(activities) { selectedDateActivities = null }
            }
        }
        
        if (showCodeEntry) {
            CodeEntryDialog(onDismiss = { showCodeEntry = false }) { code ->
                viewModel.loginWithCode(code)
                showCodeEntry = false
            }
        }
    }
}

@Composable
fun SummaryCard(activities: List<BikeActivity>, dateRange: String?) {
    val totalKm = activities.sumOf { it.distanceKm }
    val totalMin = activities.sumOf { it.durationMinutes }
    val totalCount = activities.sumOf { it.activityCount }
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        enabled = false,
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Color(0xFF0089A1),
            endBackgroundColor = Color(0xFF003747)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(dateRange?.uppercase() ?: "THIS WEEK", style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            Text(
                text = String.format(Locale.US, "%.1f KM", totalKm),
                style = MaterialTheme.typography.title2,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            val h = totalMin / 60
            val m = totalMin % 60
            Text(text = if (h > 0) "${h}h ${m}m" else "${m}m", style = MaterialTheme.typography.caption1, color = BikeBlue, fontWeight = FontWeight.ExtraBold)
            
            Text(
                text = "TOTAL TIME • $totalCount ACTIVITIES".uppercase(),
                style = MaterialTheme.typography.caption3.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun BikeActivityCard(activity: BikeActivity, onClick: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Color(0xFF0089A1),
            endBackgroundColor = Color(0xFF003747)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = activity.date.format(dateFormatter).uppercase(),
                        style = MaterialTheme.typography.caption2,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (activity.activityCount == 1 && activity.startTime.isNotEmpty()) {
                        Text(
                            text = "${activity.startTime} - ${activity.endTime}",
                            style = MaterialTheme.typography.caption3.copy(fontSize = 9.sp),
                            color = BikeBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (activity.activityCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${activity.activityCount}x",
                            style = MaterialTheme.typography.caption2,
                            color = BikeBlue,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("🚲", fontSize = 10.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f KM", activity.distanceKm),
                    style = MaterialTheme.typography.title3.copy(fontSize = 17.sp, fontWeight = FontWeight.Black),
                    color = BikeGreen
                )
                
                val h = activity.durationMinutes / 60
                val m = activity.durationMinutes % 60
                Text(
                    text = if (h > 0) "${h}h ${m}m" else "${m}m",
                    style = MaterialTheme.typography.caption1,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun YearStatsCard(stats: YearlyStats) {
    val numberFormat = java.text.NumberFormat.getInstance(Locale.US)
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = false,
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = Color(0xFF0089A1),
            endBackgroundColor = Color(0xFF003747)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TOTAL ${stats.year}".uppercase(),
                style = MaterialTheme.typography.caption2,
                fontWeight = FontWeight.Black,
                color = BikeBlue,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                YearStatItem("${stats.count}", "Acts")
                Spacer(modifier = Modifier.width(30.dp))
                YearStatItem("${numberFormat.format(stats.distanceKm.toInt())}k", "Kms")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                YearStatItem("${numberFormat.format(stats.elevationGain.toInt())}m", "Elev")
                Spacer(modifier = Modifier.width(30.dp))
                val h = stats.durationMinutes / 60
                YearStatItem("${h}h", "Time")
            }
        }
    }
}

@Composable
fun YearStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.caption2, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActivitiesDayDetailOverlay(activities: List<BikeActivity>, onClose: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(top = 25.dp, bottom = 25.dp, start = 10.dp, end = 10.dp)) {
            item { Text(activities.first().date.format(dateFormatter).uppercase(), style = MaterialTheme.typography.caption1, color = BikeBlue, fontWeight = FontWeight.Bold) }
            items(activities) { activity ->
                Card(onClick = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF111111))) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activity.name, style = MaterialTheme.typography.caption2, maxLines = 1)
                            if (activity.startTime.isNotEmpty()) {
                                Text(
                                    text = "${activity.startTime} - ${activity.endTime}",
                                    style = MaterialTheme.typography.caption3.copy(fontSize = 8.sp),
                                    color = BikeBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${String.format(Locale.US, "%.1f", activity.distanceKm)}km", color = BikeGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.caption2)
                            Text("${activity.durationMinutes}m", style = MaterialTheme.typography.caption2)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            item { Chip(onClick = onClose, label = { Text("BACK", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.width(100.dp)) }
        }
    }
}

/**
 * Custom Canvas animation showing a bicycle (Specialized Sirrus X 3.0 style)
 */
@Composable
fun BikeRunningAnimation(weeklyKm: Double?, dateRange: String?) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(600, easing = LinearEasing)))
    val runVibration by infiniteTransition.animateFloat(0f, 3f, infiniteRepeatable(tween(150, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Box(modifier = Modifier.fillMaxSize().background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF0089A1), Color(0xFF003747)),
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    ), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (dateRange != null) Text(dateRange, style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.8f))
            if (weeklyKm != null) Text(String.format("%.1f KM", weeklyKm), style = MaterialTheme.typography.display3.copy(fontSize = 22.sp, fontWeight = FontWeight.Black), color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(modifier = Modifier.size(70.dp)) {
                val scale = size.width / 100f
                withTransform({ 
                    translate(top = runVibration * scale)
                }) {
                    rotate(rotation, pivot = Offset(20f * scale, 60f * scale)) {
                        drawCircle(Color.White, radius = 15f * scale, center = Offset(20f * scale, 60f * scale), style = Stroke(width = 4f * scale))
                    }
                    rotate(rotation, pivot = Offset(80f * scale, 60f * scale)) {
                        drawCircle(Color.White, radius = 15f * scale, center = Offset(80f * scale, 60f * scale), style = Stroke(width = 4f * scale))
                    }
                    val path = Path().apply {
                        moveTo(20f * scale, 60f * scale)
                        lineTo(45f * scale, 25f * scale)
                        lineTo(80f * scale, 60f * scale)
                        lineTo(48f * scale, 60f * scale)
                        close()
                    }
                    drawPath(path, Color.White, style = Stroke(width = 5f * scale, join = StrokeJoin.Round))
                    // Handlebars
                    drawLine(Color.White, Offset(45f * scale, 25f * scale), Offset(60f * scale, 15f * scale), 5f * scale, cap = StrokeCap.Round)
                    // Seat
                    drawLine(Color.White, Offset(35f * scale, 35f * scale), Offset(25f * scale, 35f * scale), 5f * scale, cap = StrokeCap.Round)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(BuildConfig.BIKE_MODEL_NAME, style = MaterialTheme.typography.caption2, color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

/**
 * Dialog for manual entry of the Strava authorization code
 */
@Composable
fun CodeEntryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).padding(15.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("STRAVA CODE", style = MaterialTheme.typography.caption2, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            BasicTextField(
                value = code, onValueChange = { code = it },
                textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp),
                cursorBrush = SolidColor(BikeBlue),
                modifier = Modifier.fillMaxWidth().background(Color(0xFF222222), MaterialTheme.shapes.medium).padding(12.dp)
            )
            Spacer(modifier = Modifier.height(15.dp))
            Row {
                Button(onClick = onDismiss, modifier = Modifier.size(ButtonDefaults.SmallButtonSize), colors = ButtonDefaults.secondaryButtonColors()) { Text("X") }
                Spacer(modifier = Modifier.width(15.dp))
                Button(onClick = { if (code.isNotEmpty()) onConfirm(code) }, modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) { Text("OK") }
            }
        }
    }
}
