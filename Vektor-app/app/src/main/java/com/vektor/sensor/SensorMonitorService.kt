package com.vektor.sensor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vektor.data.local.prefs.ProfileDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class SensorMonitorService : Service() {

    companion object {
        const val ACTION_FALL_DETECTED = "com.vektor.ACTION_FALL_DETECTED"
        const val ACTION_SHAKE_DETECTED = "com.vektor.ACTION_SHAKE_DETECTED"

        private const val FALL_THRESHOLD = 25.0f   // m/s²
        private const val FALL_CONSECUTIVE_REQUIRED = 3
        private const val FALL_WINDOW_MS = 500L

        private const val SHAKE_THRESHOLD = 20.0f  // m/s²
        private const val SHAKE_CONSECUTIVE_REQUIRED = 5
        private const val SHAKE_WINDOW_MS = 1000L
        private const val SHAKE_COOLDOWN_MS = 3000L

        private const val TAG = "SensorMonitorService"
    }

    @Inject lateinit var profileStore: ProfileDataStore

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Ring buffer for fall detection
    private val recentFallReadings = ArrayDeque<Long>()

    // Ring buffer for shake detection
    private val recentShakeReadings = ArrayDeque<Long>()
    private var lastShakeTime = 0L

    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            val now = System.currentTimeMillis()

            // ── Fall detection ────────────────────────────────────────────────
            if (magnitude > FALL_THRESHOLD) {
                recentFallReadings.addLast(now)
            }
            while (recentFallReadings.isNotEmpty() && now - recentFallReadings.first() > FALL_WINDOW_MS) {
                recentFallReadings.removeFirst()
            }
            if (recentFallReadings.size >= FALL_CONSECUTIVE_REQUIRED) {
                recentFallReadings.clear()
                Log.w(TAG, "Fall detected! Magnitude: $magnitude")
                val intent = Intent(ACTION_FALL_DETECTED)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }

            // ── Shake detection ───────────────────────────────────────────────
            if (magnitude > SHAKE_THRESHOLD) {
                recentShakeReadings.addLast(now)
            }
            while (recentShakeReadings.isNotEmpty() && now - recentShakeReadings.first() > SHAKE_WINDOW_MS) {
                recentShakeReadings.removeFirst()
            }
            if (recentShakeReadings.size >= SHAKE_CONSECUTIVE_REQUIRED) {
                recentShakeReadings.clear()
                val timeSinceLastShake = now - lastShakeTime
                if (timeSinceLastShake > SHAKE_COOLDOWN_MS) {
                    lastShakeTime = now
                    Log.d(TAG, "Shake detected! Magnitude: $magnitude")
                    val intent = Intent(ACTION_SHAKE_DETECTED)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "vektor_sensor_channel")
            .setContentTitle("VEKTOR is active")
            .setContentText("Monitoring sensors for emergency detection")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            sensorManager.registerListener(
                accelerometerListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(accelerometerListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vektor_sensor_channel",
                "Sensor Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
