package com.oamsapps.punchd.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oamsapps.punchd.R

// ---------- Skeletons ----------

/** A gently pulsing grey block standing in for content that is loading. */
@Composable
fun Bone(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    )
}

/** Wraps a skeleton so screen readers announce "Loading" once instead of reading empty boxes. */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val loading = stringResource(R.string.loading)
    Column(
        modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun SkeletonCard(lines: Int = 3) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bone(width = 140.dp, height = 18.dp)
            repeat(lines) { Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Bone(width = 120.dp)
                Bone(width = 64.dp)
            } }
        }
    }
}

/** Clock tab: job chips, status, timer, the big round button, totals card. */
@Composable
fun ClockSkeleton() = SkeletonScreen {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Bone(width = 88.dp, height = 32.dp) } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Bone(width = 120.dp, height = 18.dp)
        Bone(width = 220.dp, height = 52.dp)
        Bone(width = 200.dp, height = 200.dp, shape = CircleShape)
    }
    SkeletonCard(lines = 3)
}

/** Timesheet: totals card and a list of day rows. */
@Composable
fun TimesheetSkeleton(rows: Int = 7) = SkeletonScreen {
    SkeletonCard(lines = 2)
    repeat(rows) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Bone(width = 96.dp)
                Bone(width = 56.dp)
            }
            Bone(width = 200.dp, height = 12.dp)
        }
    }
}

/** Reports: period header and two job cards. */
@Composable
fun ReportsSkeleton() = SkeletonScreen {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Bone(width = 200.dp, height = 20.dp) }
    SkeletonCard(lines = 4)
    SkeletonCard(lines = 4)
}

/** Editors (shift, job) and Settings: a column of form fields. */
@Composable
fun FormSkeleton(fields: Int = 5) = SkeletonScreen {
    repeat(fields) { Bone(height = 56.dp) }
    Spacer(Modifier.height(8.dp))
    Bone(height = 44.dp)
}

// ---------- Empty and error ----------

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Tighter spacing when shown inside a card above other content. */
    compact: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Full-screen "couldn't load" with Retry. Your data is still on the phone; this is almost always transient. */
@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, message: String = stringResource(R.string.load_error_text)) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.load_error_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
