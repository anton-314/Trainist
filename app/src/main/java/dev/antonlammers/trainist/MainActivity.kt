package dev.antonlammers.trainist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.antonlammers.trainist.notification.RestTimerNotifier
import dev.antonlammers.trainist.ui.navigation.AppNavigation
import dev.antonlammers.trainist.ui.onboarding.OnboardingScreen
import dev.antonlammers.trainist.ui.onboarding.OnboardingState
import dev.antonlammers.trainist.ui.onboarding.OnboardingViewModel
import dev.antonlammers.trainist.ui.theme.TrainistTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val openWorkoutSession = intent.getBooleanExtra(RestTimerNotifier.EXTRA_OPEN_WORKOUT_SESSION, false)
        // Tapping the rest-over alert notification to open the app should silence it immediately —
        // otherwise the alert sound/vibration keeps running until its own short cap elapses.
        if (openWorkoutSession) RestTimerNotifier.cancel(this)
        @OptIn(ExperimentalGetImage::class)
        setContent {
            TrainistTheme {
                // First-launch gate: show the welcome flow until onboarding is completed, then the
                // main app. Loading is a brief blank while the persisted flag is read (avoids a
                // welcome-screen flash for returning users).
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
                when (onboardingState) {
                    OnboardingState.Loading -> Unit
                    OnboardingState.Onboarding ->
                        OnboardingScreen(onFinished = onboardingViewModel::complete)

                    OnboardingState.Completed ->
                        AppNavigation(openWorkoutSession = openWorkoutSession)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
