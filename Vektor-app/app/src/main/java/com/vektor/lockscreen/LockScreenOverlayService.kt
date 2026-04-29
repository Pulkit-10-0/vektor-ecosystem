package com.vektor.lockscreen

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class LockScreenOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.vektor.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.vektor.HIDE_OVERLAY"
    }

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> hideOverlay()
            else -> showOverlay() // SHOW_OVERLAY or null — always show
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            setPadding(48, 48, 48, 48)
        }

        // Close button at top
        val closeBtn = Button(this).apply {
            text = "✕ Close"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 80, 80, 80))
            setOnClickListener { hideOverlay() }
        }
        layout.addView(closeBtn)

        val title = TextView(this).apply {
            text = "VEKTOR — Emergency QR"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        layout.addView(title)

        val qrFile = File(filesDir, "qr_card.png")
        if (qrFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(qrFile.absolutePath)
            if (bitmap != null) {
                val imageView = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    val size = 400
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        topMargin = 24
                    }
                }
                layout.addView(imageView)
            }
        } else {
            val placeholder = TextView(this).apply {
                text = "QR not generated yet"
                textSize = 16f
                setTextColor(Color.YELLOW)
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            layout.addView(placeholder)
        }

        val subtitle = TextView(this).apply {
            text = "Scan for medical information"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        layout.addView(subtitle)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(layout, params)
            overlayView = layout
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        stopSelf()
    }

    override fun onDestroy() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
