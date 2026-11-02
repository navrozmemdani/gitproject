package com.visualtrace.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.visualtrace.ai.features.common.AppInfo
import com.visualtrace.ai.features.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val info = AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            supportEmail = BuildConfig.SUPPORT_EMAIL,
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
        setContent { AppRoot(info) }
    }
}
