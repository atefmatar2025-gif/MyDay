package com.dayflow.app.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.ui.components.DfCard
import com.dayflow.app.ui.components.ListRow
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.theme.Spacing

@Composable
fun MoreScreen(nav: NavController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DfCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm)) {
            ListRow(stringResource(R.string.ask_dayflow), stringResource(R.string.ask_dayflow_sub), Icons.Filled.AutoAwesome, { nav.navigate(Routes.ASSISTANT) })
            ListRow(stringResource(R.string.save_my_day), stringResource(R.string.save_my_day_sub), Icons.Filled.Bolt, { nav.navigate(Routes.saveMyDay()) })
            ListRow(stringResource(R.string.my_day_timeline), stringResource(R.string.timeline_sub), Icons.Filled.Timeline, { nav.navigate(Routes.timeline()) })
            ListRow(stringResource(R.string.focus_mode), stringResource(R.string.focus_sub), Icons.Filled.Timer, { nav.navigate(Routes.focus()) })
        }
        DfCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm)) {
            ListRow(stringResource(R.string.statistics), stringResource(R.string.stats_sub), Icons.Filled.BarChart, { nav.navigate(Routes.STATS) })
            ListRow(stringResource(R.string.productivity_memory), stringResource(R.string.memory_sub), Icons.Filled.History, { nav.navigate(Routes.MEMORY) })
            ListRow(stringResource(R.string.life_balance), stringResource(R.string.balance_sub), Icons.Filled.PieChart, { nav.navigate(Routes.BALANCE) })
        }
        DfCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm)) {
            ListRow(stringResource(R.string.categories), null, Icons.Filled.Category, { nav.navigate(Routes.CATEGORIES) })
            ListRow(stringResource(R.string.alarm_permissions), null, Icons.Filled.NotificationsActive, { nav.navigate(Routes.PERMISSIONS) })
            ListRow(stringResource(R.string.settings), null, Icons.Filled.Settings, { nav.navigate(Routes.SETTINGS) })
        }
    }
}
