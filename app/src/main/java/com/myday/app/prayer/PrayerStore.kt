package com.myday.app.prayer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * إعدادات الموقع وطريقة الحساب. تُخزَّن في SharedPreferences لأن قراءتها متزامنة
 * وسريعة — تحتاجها جدولة المنبهات حتى داخل BootReceiver.
 */
object PrayerStore {
    private const val PREFS = "prayer_settings"
    private var prefs: SharedPreferences? = null
    private val _config = MutableStateFlow<PrayerConfig?>(null)
    val config: StateFlow<PrayerConfig?> = _config
    private val cache = ConcurrentHashMap<LocalDate, DayPrayerTimes>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = read()
    }

    fun current(): PrayerConfig? = _config.value

    private fun read(): PrayerConfig? {
        val p = prefs ?: return null
        if (!p.contains("lat")) return null
        return PrayerConfig(
            cityName = p.getString("city", "") ?: "",
            latitude = p.getFloat("lat", 0f).toDouble().let { d -> p.getString("latS", null)?.toDoubleOrNull() ?: d },
            longitude = p.getFloat("lng", 0f).toDouble().let { d -> p.getString("lngS", null)?.toDoubleOrNull() ?: d },
            method = CalcMethod.fromName(p.getString("method", null)),
            hanafiAsr = p.getBoolean("hanafi", false),
            adjustments = (p.getString("adj", null) ?: "0,0,0,0,0,0").split(',').map { it.toIntOrNull() ?: 0 }
                .let { if (it.size == 6) it else List(6) { 0 } },
        )
    }

    fun save(cfg: PrayerConfig) {
        val p = prefs ?: return
        p.edit()
            .putString("city", cfg.cityName)
            .putFloat("lat", cfg.latitude.toFloat()).putString("latS", cfg.latitude.toString())
            .putFloat("lng", cfg.longitude.toFloat()).putString("lngS", cfg.longitude.toString())
            .putString("method", cfg.method.name)
            .putBoolean("hanafi", cfg.hanafiAsr)
            .putString("adj", cfg.adjustments.joinToString(","))
            .apply()
        cache.clear()
        _config.value = cfg
    }

    /** أوقات يوم معيّن (مع ذاكرة مؤقتة). */
    fun timesFor(date: LocalDate, cfg: PrayerConfig? = current()): DayPrayerTimes? {
        cfg ?: return null
        if (cfg != current()) return PrayerEngine.compute(date, cfg)
        if (cache.size > 400) cache.clear()
        return cache.getOrPut(date) { PrayerEngine.compute(date, cfg) }
    }

    /** للاختبارات فقط. */
    internal fun setForTest(cfg: PrayerConfig?) {
        cache.clear()
        _config.value = cfg
    }
}
