package com.dayflow.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayflow.app.data.db.UserSettingsEntity
import com.dayflow.app.notification.IntentKeys
import com.dayflow.app.ui.navigation.DayflowNavHost
import com.dayflow.app.ui.navigation.DeepLink
import com.dayflow.app.ui.screens.onboarding.OnboardingScreen
import com.dayflow.app.ui.theme.DayflowTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<DeepLink?>(null)

    /** Arabic UI everywhere (also for system components such as the date picker), Latin digits. */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag("ar-u-nu-latn"))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val container = appContainer()
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null as UserSettingsEntity?)
            val s = settings
            DayflowTheme(themeMode = s?.themeMode ?: "SYSTEM", fontScale = s?.fontScale ?: 1f) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        s == null -> Unit // first frame while the settings row loads
                        !s.onboardingDone -> OnboardingScreen()
                        else -> DayflowNavHost(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val dest = intent?.getStringExtra(IntentKeys.EXTRA_DESTINATION) ?: return
        deepLink = DeepLink(dest, intent.getLongExtra(IntentKeys.EXTRA_TASK_ID, 0), System.nanoTime())
        intent.removeExtra(IntentKeys.EXTRA_DESTINATION)
    }
}
