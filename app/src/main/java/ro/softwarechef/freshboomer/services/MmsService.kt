package ro.softwarechef.freshboomer.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MmsService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle MMS messages here if needed
        return START_NOT_STICKY
    }
} 