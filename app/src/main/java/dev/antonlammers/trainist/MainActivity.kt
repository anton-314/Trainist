package dev.antonlammers.trainist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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

    // AppCompatDelegate.setApplicationLocales() only auto-recreates AppCompatActivity subclasses
    // below API 33 (it needs a registered AppCompatDelegate to intercept attachBaseContext).
    // MainActivity stays a plain ComponentActivity — switching to AppCompatActivity would require
    // a Theme.AppCompat descendant, at odds with the "Ink & Paper" plain-Material-theme setup
    // (see CLAUDE.md's theme-isolation note) — so the per-app locale is applied manually here for
    // API < 33. On API 33+ AppCompatDelegate already delegates to the platform LocaleManager,
    // which applies app-wide regardless of the Activity's base class.
    override fun attachBaseContext(newBase: Context) {
        val context = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppCompatDelegate.getApplicationLocales().get(0)?.let { locale ->
                val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
                newBase.createConfigurationContext(config)
            } ?: newBase
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Nothing is cancelled here on purpose: the same extra is carried by the *ongoing* countdown
        // notification, so tearing anything down would kill a rest that is still running. The alert
        // notification auto-cancels on tap, and its ~2 s tone has all but finished by then anyway.
        val openWorkoutSession = intent.getBooleanExtra(RestTimerNotifier.EXTRA_OPEN_WORKOUT_SESSION, false)
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
