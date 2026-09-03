package com.example.shoplog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.example.shoplog.data.repository.AuthRepository
import com.example.shoplog.ui.navigation.MainNavGraph
import com.example.shoplog.ui.screens.settings.SettingsViewModel
import com.example.shoplog.ui.theme.ShopLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            runCatching { authRepository.signInAnonymously() }
        }

        setContent {
            val settingsViewModel = hiltViewModel<SettingsViewModel>()
            val themeMode by settingsViewModel.themeMode.collectAsState()

            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            ShopLogTheme(darkTheme = useDarkTheme) {
                MainNavGraph()
            }
        }
    }
}
