package dev.antonlammers.trainist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.antonlammers.trainist.notification.RestTimerNotifier
import dev.antonlammers.trainist.ui.navigation.AppNavigation
import dev.antonlammers.trainist.ui.onboarding.OnboardingScreen
import dev.antonlammers.trainist.ui.onboarding.OnboardingState
import dev.antonlammers.trainist.ui.onboarding.OnboardingViewModel
import dev.antonlammers.trainist.ui.theme.ThemeViewModel
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

    // Where the @ExperimentalGetImage chain from the barcode analyzer terminates. It must be
    // androidx's OptIn, not Kotlin's: the marker is a Java @RequiresOptIn, so the Kotlin compiler
    // ignores it entirely and only lint enforces it — and lint reads the annotation off the
    // enclosing declaration, not off a statement inside it.
    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Nothing is cancelled here on purpose: the same extra is carried by the *ongoing* countdown
        // notification, so tearing anything down would kill a rest that is still running. The alert
        // notification auto-cancels on tap, and its ~2 s tone has all but finished by then anyway.
        val openWorkoutSession = intent.getBooleanExtra(RestTimerNotifier.EXTRA_OPEN_WORKOUT_SESSION, false)
        setContent {
            // App appearance: the user's choice wins over the system setting (ThemeMode.SYSTEM
            // defers to it). The value is known synchronously on the first composition, so a pinned
            // shade never flashes the other one on a cold start.
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())

            TrainistTheme(darkTheme = darkTheme) {
                // Everything outside the Compose hierarchy still follows the *system* uiMode on its
                // own — the system-bar icon contrast (from enableEdgeToEdge's dark-mode detection)
                // and the window background (from the DayNight XML theme). Both are re-applied here
                // against the resolved shade, or forcing light on a dark device would leave white
                // status-bar icons on a light bar and a dark hairline under the bottom nav.
                val chrome = MaterialTheme.colorScheme.surfaceContainerLowest
                LaunchedEffect(darkTheme, chrome) {
                    applyWindowAppearance(darkTheme, chrome.toArgb())
                }

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

    /**
     * Re-applies the edge-to-edge system-bar styling and the window background for [darkTheme].
     * The scrims mirror `enableEdgeToEdge()`'s own defaults (its constants are private); the
     * `detectDarkMode` lambda is what actually pins the bar-icon contrast to the app's shade
     * instead of the device's.
     */
    private fun applyWindowAppearance(darkTheme: Boolean, chromeColor: Int) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF),
                AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b),
            ) { darkTheme },
        )
        window.setBackgroundDrawable(ColorDrawable(chromeColor))
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
