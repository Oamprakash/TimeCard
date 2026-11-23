package com.oamsapps.punchd.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.oamsapps.punchd.R
import dagger.hilt.android.EntryPointAccessors

@Composable
private fun rememberShowAds(): Boolean {
    val context = LocalContext.current
    val gate = remember { EntryPointAccessors.fromApplication(context.applicationContext, AdsEntryPoint::class.java).adsGate() }
    val show by gate.showAds.collectAsStateWithLifecycle()
    return show
}

/**
 * Anchored adaptive banner. Renders nothing when ads are off (no consent yet, or Remove ads bought)
 * or when [visible] is false (e.g. a dialog is open).
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier, visible: Boolean = true) {
    if (LocalInspectionMode.current) {
        if (visible) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            androidx.compose.foundation.layout.Box(modifier.fillMaxWidth().height(58.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        return
    }
    if (!rememberShowAds() || !visible) return
    val lifecycleOwner = LocalLifecycleOwner.current
    // Anchored strip: thin divider on top, flush with the navigation bar below.
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    BoxWithConstraints(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp)) {
        val widthDp = maxWidth.value.toInt()
        var adView by remember { mutableStateOf<AdView?>(null) }
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = AdUnits.banner
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    loadAd(AdRequest.Builder().build())
                }.also { adView = it }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth(),
        )
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView?.pause()
                    Lifecycle.Event.ON_RESUME -> adView?.resume()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
}

/** A native ad styled as a timesheet row (spec: one per 10 days of entries). Nothing if ads are off or no fill. */
@Composable
fun NativeAdRow(modifier: Modifier = Modifier) {
    val show = rememberShowAds()
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(show) {
        if (!show || ad != null) return@LaunchedEffect
        AdLoader.Builder(context, AdUnits.native)
            .forNativeAd { loaded -> ad = loaded }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) = Unit
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }
    DisposableEffect(Unit) { onDispose { ad?.destroy() } }

    val loaded = ad
    if (!show || loaded == null) return
    AndroidView(
        factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.native_ad_row, null) as NativeAdView },
        update = { view -> bind(view, loaded) },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun bind(view: NativeAdView, ad: NativeAd) {
    val headline = view.findViewById<TextView>(R.id.ad_headline)
    val body = view.findViewById<TextView>(R.id.ad_body)
    val cta = view.findViewById<Button>(R.id.ad_call_to_action)
    val icon = view.findViewById<ImageView>(R.id.ad_icon)

    view.headlineView = headline
    view.bodyView = body
    view.callToActionView = cta
    view.iconView = icon

    headline.text = ad.headline
    body.text = ad.body
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    cta.text = ad.callToAction
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    val drawable = ad.icon?.drawable
    icon.setImageDrawable(drawable)
    icon.visibility = if (drawable == null) View.GONE else View.VISIBLE

    view.setNativeAd(ad)
}
