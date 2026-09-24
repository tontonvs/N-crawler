package com.noven.ncrawler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.noven.ncrawler.ui.NCrawlerNavGraph
import com.noven.ncrawler.ui.theme.NCrawlerTheme

class MainActivity : ComponentActivity() {

    // CHANGE (Downloads overhaul — reliability fix): without this the
    // download worker's foreground notification silently fails to show on
    // API 33+ (the service still runs, but the user gets zero visual
    // feedback that anything is happening — same "looks dead" symptom as
    // the original bug, just from a different cause).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            NCrawlerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NCrawlerNavGraph()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
