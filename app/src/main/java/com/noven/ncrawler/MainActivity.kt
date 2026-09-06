package com.noven.ncrawler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noven.ncrawler.ui.NavGraph
import com.noven.ncrawler.ui.theme.NCrawlerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NCrawlerTheme {
                NavGraph()
            }
        }
    }
}
