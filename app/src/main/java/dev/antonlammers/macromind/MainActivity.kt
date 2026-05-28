package dev.antonlammers.macromind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ExperimentalGetImage
import dagger.hilt.android.AndroidEntryPoint
import dev.antonlammers.macromind.ui.navigation.AppNavigation
import dev.antonlammers.macromind.ui.theme.MacroMindTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @OptIn(ExperimentalGetImage::class)
        setContent {
            MacroMindTheme {
                AppNavigation()
            }
        }
    }
}
