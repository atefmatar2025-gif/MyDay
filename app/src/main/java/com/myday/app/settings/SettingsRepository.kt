package com.myday.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) { SYSTEM("حسب النظام"), LIGHT("نهاري"), DARK("ليلي") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val userName: String = "",
    val defaultSoundUri: String? = null,
    val defaultSoundName: String = "نغمة المنبه الافتراضية",
    val ringDurationSec: Int = 60,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    val defaultReminderMin: Int = 0,
    val repeatIfNoResponse: Boolean = true,
    val repeatIntervalMin: Int = 5,
    val maxRepeats: Int = 3,
    val use24h: Boolean = false,
    val onboardingDone: Boolean = false,
    /** حجم الخط: 1.0 عادي، 1.15 كبير، 1.3 كبير جداً */
    val textScale: Float = 1f,
    val careContacts: List<CareContact> = emptyList(),
    val careMessage: String = DEFAULT_CARE_MESSAGE,
    /** معرّف الشريحة للإرسال؛ -1 = الشريحة الافتراضية في الهاتف */
    val careSimSubId: Int = -1,
    val careSimLabel: String = "",
)

data class CareContact(val name: String, val phone: String)

const val DEFAULT_CARE_MESSAGE =
    "تنبيه من تطبيق يومي: لم يؤكد {الاسم} «{المهمة}» المقررة الساعة {الوقت}. يُرجى الاطمئنان عليه."


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val NAME = stringPreferencesKey("user_name")
        val SOUND_URI = stringPreferencesKey("default_sound_uri")
        val SOUND_NAME = stringPreferencesKey("default_sound_name")
        val RING_DURATION = intPreferencesKey("ring_duration_sec")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val GRADUAL = booleanPreferencesKey("gradual_volume")
        val REMINDER = intPreferencesKey("default_reminder_min")
        val REPEAT_NO_RESPONSE = booleanPreferencesKey("repeat_if_no_response")
        val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_min")
        val MAX_REPEATS = intPreferencesKey("max_repeats")
        val USE_24H = booleanPreferencesKey("use_24h")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val CARE_CONTACTS = stringPreferencesKey("care_contacts")
        val CARE_MESSAGE = stringPreferencesKey("care_message")
        val CARE_SIM = intPreferencesKey("care_sim_sub_id")
        val CARE_SIM_LABEL = stringPreferencesKey("care_sim_label")
    }

    private fun encodeContacts(list: List<CareContact>) =
        list.joinToString("\n") { "${it.name.replace("\t", " ").replace("\n", " ")}\t${it.phone.trim()}" }

    private fun decodeContacts(s: String?): List<CareContact> =
        s.orEmpty().split('\n').mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[1].isNotBlank()) CareContact(parts[0], parts[1]) else null
        }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: d.themeMode.name) }.getOrDefault(d.themeMode),
                dynamicColor = p[Keys.DYNAMIC] ?: d.dynamicColor,
                userName = p[Keys.NAME] ?: d.userName,
                defaultSoundUri = p[Keys.SOUND_URI],
                defaultSoundName = p[Keys.SOUND_NAME] ?: d.defaultSoundName,
                ringDurationSec = p[Keys.RING_DURATION] ?: d.ringDurationSec,
                vibrate = p[Keys.VIBRATE] ?: d.vibrate,
                gradualVolume = p[Keys.GRADUAL] ?: d.gradualVolume,
                defaultReminderMin = p[Keys.REMINDER] ?: d.defaultReminderMin,
                repeatIfNoResponse = p[Keys.REPEAT_NO_RESPONSE] ?: d.repeatIfNoResponse,
                repeatIntervalMin = p[Keys.REPEAT_INTERVAL] ?: d.repeatIntervalMin,
                maxRepeats = p[Keys.MAX_REPEATS] ?: d.maxRepeats,
                use24h = p[Keys.USE_24H] ?: d.use24h,
                onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
                textScale = p[Keys.TEXT_SCALE] ?: d.textScale,
                careContacts = decodeContacts(p[Keys.CARE_CONTACTS]),
                careMessage = p[Keys.CARE_MESSAGE]?.takeIf { it.isNotBlank() } ?: d.careMessage,
                careSimSubId = p[Keys.CARE_SIM] ?: d.careSimSubId,
                careSimLabel = p[Keys.CARE_SIM_LABEL] ?: d.careSimLabel,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = v }
    suspend fun setUserName(v: String) = context.dataStore.edit { it[Keys.NAME] = v.trim() }
    suspend fun setDefaultSound(uri: String?, name: String) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.SOUND_URI) else it[Keys.SOUND_URI] = uri
        it[Keys.SOUND_NAME] = name
    }
    suspend fun setRingDuration(v: Int) = context.dataStore.edit { it[Keys.RING_DURATION] = v.coerceIn(10, 600) }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[Keys.VIBRATE] = v }
    suspend fun setGradualVolume(v: Boolean) = context.dataStore.edit { it[Keys.GRADUAL] = v }
    suspend fun setDefaultReminder(v: Int) = context.dataStore.edit { it[Keys.REMINDER] = v.coerceAtLeast(0) }
    suspend fun setRepeatIfNoResponse(v: Boolean) = context.dataStore.edit { it[Keys.REPEAT_NO_RESPONSE] = v }
    suspend fun setRepeatInterval(v: Int) = context.dataStore.edit { it[Keys.REPEAT_INTERVAL] = v.coerceIn(1, 60) }
    suspend fun setMaxRepeats(v: Int) = context.dataStore.edit { it[Keys.MAX_REPEATS] = v.coerceIn(0, 10) }
    suspend fun setUse24h(v: Boolean) = context.dataStore.edit { it[Keys.USE_24H] = v }
    suspend fun setTextScale(v: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = v.coerceIn(0.9f, 1.5f) }
    suspend fun setCareContacts(v: List<CareContact>) = context.dataStore.edit { it[Keys.CARE_CONTACTS] = encodeContacts(v.take(3)) }
    suspend fun setCareMessage(v: String) = context.dataStore.edit { it[Keys.CARE_MESSAGE] = v.trim() }
    suspend fun setCareSim(subId: Int, label: String) = context.dataStore.edit {
        it[Keys.CARE_SIM] = subId
        it[Keys.CARE_SIM_LABEL] = label
    }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = v }
}
