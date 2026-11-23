package com.oam.timecard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oam.timecard.domain.engine.PayPeriodConfig
import com.oam.timecard.domain.engine.PayPeriodType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboardingDone: Boolean,
    val weekStart: DayOfWeek,
    val payPeriod: PayPeriodConfig,
    val removeAds: Boolean,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val WEEK_START = stringPreferencesKey("week_start")
        val PAY_TYPE = stringPreferencesKey("pay_period_type")
        val PAY_ANCHOR = longPreferencesKey("pay_period_anchor_epoch_day")
        val REMOVE_ADS = booleanPreferencesKey("remove_ads") // cache only; Play Billing is the source of truth
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val weekStart = p[Keys.WEEK_START]?.let(DayOfWeek::valueOf)
            ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            weekStart = weekStart,
            payPeriod = PayPeriodConfig(
                type = p[Keys.PAY_TYPE]?.let(PayPeriodType::valueOf) ?: PayPeriodType.WEEKLY,
                anchor = p[Keys.PAY_ANCHOR]?.let(LocalDate::ofEpochDay)
                    ?: LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart)),
            ),
            removeAds = p[Keys.REMOVE_ADS] ?: false,
        )
    }

    suspend fun setWeekStart(day: DayOfWeek) = context.dataStore.edit { it[Keys.WEEK_START] = day.name }

    suspend fun setPayPeriod(config: PayPeriodConfig) = context.dataStore.edit {
        it[Keys.PAY_TYPE] = config.type.name
        it[Keys.PAY_ANCHOR] = config.anchor.toEpochDay()
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    suspend fun setRemoveAds(owned: Boolean) = context.dataStore.edit { it[Keys.REMOVE_ADS] = owned }
}
