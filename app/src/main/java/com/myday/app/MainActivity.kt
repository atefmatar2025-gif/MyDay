package com.myday.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.settings.AppSettings
import com.myday.app.ui.navigation.MyDayRoot
import com.myday.app.ui.theme.MyDayTheme
import com.myday.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** مهمة مطلوب فتحها (من إشعار أو منبه). */
    private val openTaskRequest = MutableStateFlow<Long?>(null)
    /** طلب فتح الإضافة السريعة بالصوت (من الودجت) */
    private val quickAddRequest = MutableStateFlow(false)
    @Volatile private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings: AppSettings? by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s != null) {
                SideEffect { settingsLoaded = true }
                MyDayTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor, textScale = s.textScale) {
                    MyDayRoot(settings = s, openTaskRequest = openTaskRequest, quickAddRequest = quickAddRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TASK, -1L) ?: -1L
        if (id > 0) openTaskRequest.value = id
        if (intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true) quickAddRequest.value = true
    }

    override fun onStop() {
        super.onStop()
        WidgetUpdater.request(this)
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task_id"
        const val EXTRA_QUICK_ADD = "quick_add"

        fun quickAddIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("myday://quickadd"))
                .putExtra(EXTRA_QUICK_ADD, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun openTaskIntent(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("myday://task/$taskId"))
                .putExtra(EXTRA_OPEN_TASK, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
