@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myday.app.ui.prayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myday.app.prayer.CalcMethod
import com.myday.app.prayer.Cities
import com.myday.app.prayer.City
import com.myday.app.prayer.Prayer
import com.myday.app.prayer.PrayerConfig
import com.myday.app.prayer.PrayerEngine
import com.myday.app.settings.AppSettings
import com.myday.app.ui.AppViewModel
import com.myday.app.util.Fmt
import com.myday.app.util.LocationHelper
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PrayerSettingsScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by vm.prayerConfig.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }

    fun choose(city: City) {
        val old = cfg
        vm.savePrayerConfig(
            PrayerConfig(
                cityName = city.name, latitude = city.lat, longitude = city.lng,
                method = city.method, hanafiAsr = city.hanafi,
                adjustments = old?.adjustments ?: List(6) { 0 },
            )
        )
        query = ""
    }

    fun locate() {
        locating = true
        scope.launch {
            val loc = LocationHelper.current(context)
            locating = false
            if (loc == null) {
                vm.message("تعذر تحديد الموقع — فعّل خدمة الموقع أو اختر مدينتك من القائمة")
                return@launch
            }
            val (near, km) = LocationHelper.nearestCity(loc.latitude, loc.longitude)
            vm.savePrayerConfig(
                PrayerConfig(
                    cityName = if (km < 25) near.name else "موقعي (قرب ${near.name})",
                    latitude = loc.latitude, longitude = loc.longitude,
                    method = cfg?.method ?: near.method, hanafiAsr = cfg?.hanafiAsr ?: near.hanafi,
                    adjustments = cfg?.adjustments ?: List(6) { 0 },
                )
            )
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locate() else vm.message("بدون إذن الموقع يمكنك اختيار مدينتك من القائمة")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("أوقات الصلاة والموقع") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } }
            )
        }
    ) { padding ->
        val cities = remember(query) { Cities.search(query) }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                val c = cfg
                if (c != null) {
                    TodayTimes(c, settings.use24h)
                    Spacer(Modifier.height(12.dp))
                    SettingRow("طريقة الحساب", c.method.label) { showMethods = true }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مذهب العصر", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FilterChip(selected = !c.hanafiAsr, onClick = { vm.savePrayerConfig(c.copy(hanafiAsr = false), false) }, label = { Text("الجمهور") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = c.hanafiAsr, onClick = { vm.savePrayerConfig(c.copy(hanafiAsr = true), false) }, label = { Text("الحنفي") })
                    }
                    Text("تعديل يدوي بالدقائق (لمطابقة تقويم مسجدك)", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Prayer.entries.forEachIndexed { i, p ->
                        val v = c.adjustments.getOrElse(i) { 0 }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.savePrayerConfig(c.copy(adjustments = c.adjustments.toMutableList().also { it[i] = (v - 1).coerceAtLeast(-30) }), false) }) {
                                Icon(Icons.Rounded.Remove, "إنقاص")
                            }
                            Text(if (v > 0) "+$v" else "$v", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { vm.savePrayerConfig(c.copy(adjustments = c.adjustments.toMutableList().also { it[i] = (v + 1).coerceAtMost(30) }), false) }) {
                                Icon(Icons.Rounded.Add, "زيادة")
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp)
                    ) {
                        Text("اختر مدينتك", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "تُحسب أوقات الصلاة على هاتفك دون إنترنت، ثم يمكنك ربط مهامك بها مثل «بعد العصر بنصف ساعة».",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) locate() else permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "جارٍ تحديد الموقع…" else "استخدام موقعي الحالي")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("ابحث عن مدينة أو دولة") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            items(cities, key = { "${it.name}_${it.country}" }) { city ->
                val selected = cfg?.cityName == city.name
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { choose(city) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(city.name, style = MaterialTheme.typography.bodyLarge)
                        Text(city.country, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Rounded.CheckCircle, "المختارة", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    val c = cfg
    if (showMethods && c != null) {
        AlertDialog(
            onDismissRequest = { showMethods = false },
            title = { Text("طريقة الحساب") },
            text = {
                LazyColumn {
                    items(CalcMethod.entries) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.savePrayerConfig(c.copy(method = m), false); showMethods = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = c.method == m, onClick = { vm.savePrayerConfig(c.copy(method = m), false); showMethods = false })
                            Text(m.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMethods = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayTimes(cfg: PrayerConfig, use24h: Boolean) {
    val today = LocalDate.now()
    val times = remember(cfg, today) { PrayerEngine.compute(today, cfg) }
    val now = System.currentTimeMillis()
    val next = Prayer.entries.firstOrNull { (times[it] ?: 0) > now }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)
    ) {
        Text(cfg.cityName, style = MaterialTheme.typography.titleMedium)
        Text("${Fmt.fullDate(today)} • ${Fmt.hijri(today)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Prayer.entries.forEach { p ->
                val isNext = p == next
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val fg = if (isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Text(p.label, style = MaterialTheme.typography.labelMedium, color = fg)
                    Text(times[p]?.let { Fmt.time(it, use24h) } ?: "—", style = MaterialTheme.typography.titleSmall, color = fg)
                }
            }
        }
    }
}
