package com.oam.timecard.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.oam.timecard.MainActivity
import com.oam.timecard.R
import com.oam.timecard.clock.ClockController
import com.oam.timecard.data.db.toDomain
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.data.settings.SettingsRepository
import com.oam.timecard.domain.engine.HoursCalculator
import com.oam.timecard.domain.engine.RunningState
import com.oam.timecard.ui.formatHmText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repo(): TimeCardRepository
    fun settings(): SettingsRepository
    fun controller(): ClockController
    fun clock(): Clock
}

private fun Context.entry() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

private data class WidgetState(
    val hasJob: Boolean,
    val running: Boolean,
    val onBreak: Boolean,
    val jobName: String?,
    /** SystemClock.elapsedRealtime()-based chronometer base showing worked time. */
    val chronometerBase: Long,
    val today: String,
)

/** Home-screen widget: status, live timer, today's hours, Clock in/out. Always ad-free. */
class TimeCardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { GlanceTheme { Content(state) } }
    }

    private suspend fun load(context: Context): WidgetState {
        val e = context.entry()
        val now = e.clock().instant()
        val jobs = e.repo().activeJobs.first()
        val running = e.repo().running()
        val snapshot = running?.let { RunningState.snapshot(it.toDomain(), now) }
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val worked = e.repo().observeShifts(today, today).first().fold(Duration.ZERO) { acc, s ->
            val d = s.toDomain()
            val end = d.end ?: now
            acc.plus(Duration.between(d.start, end).minus(HoursCalculator.breakTotal(d.breaks, d.start, end, now)))
        }
        return WidgetState(
            hasJob = jobs.isNotEmpty(),
            running = running != null,
            onBreak = snapshot?.onBreak == true,
            jobName = running?.let { r -> jobs.firstOrNull { it.job.id == r.shift.jobId }?.job?.name },
            chronometerBase = SystemClock.elapsedRealtime() - (snapshot?.worked?.toMillis() ?: 0L),
            today = context.getString(R.string.widget_today, formatHmText(context, worked)),
        )
    }

    @Composable
    private fun Content(s: WidgetState) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    !s.running -> context.getString(R.string.not_clocked_in)
                    s.onBreak -> context.getString(R.string.on_break_label) + (s.jobName?.let { " · $it" } ?: "")
                    else -> context.getString(R.string.working_label) + (s.jobName?.let { " · $it" } ?: "")
                },
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
            if (s.running) {
                // A real Chronometer keeps counting without widget updates; stopped (frozen) during a break.
                AndroidRemoteViews(
                    RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
                        setChronometer(R.id.widget_chronometer, s.chronometerBase, null, !s.onBreak)
                    }
                )
            } else {
                Text("0:00:00", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            }
            Text(s.today, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = context.getString(
                    when {
                        !s.hasJob -> R.string.widget_open_app
                        s.running -> R.string.clock_out
                        else -> R.string.clock_in
                    }
                ),
                onClick = if (s.hasJob) actionRunCallback<ClockToggleAction>() else actionStartActivity<MainActivity>(),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TimeCardWidget().updateAll(context)
    }
}

/** Clock in (selected or first job) / clock out, straight from the home screen. */
class ClockToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val e = context.entry()
        if (e.repo().running() != null) {
            e.controller().clockOut()
        } else {
            val jobs = e.repo().activeJobs.first()
            val selected = e.settings().settings.first().selectedJobId
            val jobId = jobs.firstOrNull { it.job.id == selected }?.job?.id ?: jobs.firstOrNull()?.job?.id ?: return
            e.controller().clockIn(jobId)
        }
    }
}

class TimeCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeCardWidget()
}
