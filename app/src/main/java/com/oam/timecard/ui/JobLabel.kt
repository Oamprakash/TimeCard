package com.oam.timecard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oam.timecard.data.db.JobWithRules
import java.time.Duration

/** The parts of a job that screens display. */
data class JobLabel(
    val id: Long,
    val name: String,
    val color: Int,
    val currency: String,
    val scheduledPerWeek: Duration?,
)

fun JobWithRules.toLabel() = JobLabel(
    id = job.id,
    name = job.name,
    color = job.color,
    currency = job.currency,
    scheduledPerWeek = job.scheduledMinPerWeek?.let { Duration.ofMinutes(it.toLong()) },
)

@Composable
fun JobDot(color: Int, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(Color(color), CircleShape))
}
