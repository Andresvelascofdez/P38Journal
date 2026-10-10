package dev.p38journal.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.ZoneId

private val Context.productStore by preferencesDataStore(name = "product")
data class Settings(
    val zone: String = "Asia/Nicosia",
    val leadDays: Int = 30,
    val leadKm: Int = 1000,
    val hour: Int = 9,
    val enabled: Boolean = false,
    val weekly: Boolean = true,
    val checks: Boolean = true,
    val odometerReminder: Boolean = true,
    val externalFolderUri: String = ""
)
class Preferences(context: Context) {
    private val store = context.applicationContext.productStore
    private val key = stringPreferencesKey("settings")
    private val gson = Gson()
    val flow: Flow<Settings> = store.data.map { prefs ->
        prefs[key]?.let { runCatching { gson.fromJson(it, Settings::class.java) }.getOrNull() } ?: Settings()
    }
    suspend fun current() = flow.first()
    suspend fun update(settings: Settings) {
        ZoneId.of(settings.zone)
        require(settings.leadDays in 0..365 && settings.leadKm in 0..50000 && settings.hour in 0..23) { "Preferencias fuera de rango" }
        store.edit { it[key] = gson.toJson(settings) }
    }
}
