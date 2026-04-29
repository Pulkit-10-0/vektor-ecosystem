package com.vektor.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LockScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_BOOT_COMPLETED -> {
                val serviceIntent = Intent(context, LockScreenOverlayService::class.java).apply {
                    action = LockScreenOverlayService.ACTION_SHOW_OVERLAY
                }
                context.startService(serviceIntent)
            }
        }
    }
}
