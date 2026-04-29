package com.vektor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vektor.qr.QrManager
import com.vektor.sensor.SensorMonitorService
import com.vektor.ui.navigation.VektorNavGraph
import com.vektor.ui.theme.VektorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var qrManager: QrManager

    private var navigateToCountdown by mutableStateOf(false)

    private val fallDetectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SensorMonitorService.ACTION_FALL_DETECTED) {
                navigateToCountdown = true
            }
        }
    }

    private val shakeDetectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SensorMonitorService.ACTION_SHAKE_DETECTED) {
                // Bring app to foreground
                val bringToFront = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(bringToFront)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fallFilter = IntentFilter(SensorMonitorService.ACTION_FALL_DETECTED)
        registerReceiver(fallDetectedReceiver, fallFilter, RECEIVER_NOT_EXPORTED)

        val shakeFilter = IntentFilter(SensorMonitorService.ACTION_SHAKE_DETECTED)
        registerReceiver(shakeDetectedReceiver, shakeFilter, RECEIVER_NOT_EXPORTED)

        setContent {
            VektorTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VektorNavGraph(
                        qrManager = qrManager,
                        triggerCountdown = navigateToCountdown,
                        onCountdownTriggered = { navigateToCountdown = false }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(fallDetectedReceiver)
        unregisterReceiver(shakeDetectedReceiver)
        super.onDestroy()
    }
}
