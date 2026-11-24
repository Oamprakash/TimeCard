package com.oamsapps.punchd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oamsapps.punchd.ads.ConsentManager
import com.oamsapps.punchd.ui.TimeCardRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var consent: ConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TimeCardRoot() }
        // Shows the consent form only where it is required (EU/UK, some US states), then allows ads.
        consent.gather(this)
    }
}
