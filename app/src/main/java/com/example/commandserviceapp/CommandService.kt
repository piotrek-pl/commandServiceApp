package com.example.commandserviceapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class CommandService : Service() {

    companion object {
        private const val CHANNEL_ID = "CommandServiceChannel"
        private const val CHANNEL_NAME = "Command Service Notifications"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CommandService", "Service started in onCreate")

        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Usługa uruchomiona")
            .setContentText("Usługa działa po restarcie.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)

        // Wyświetlanie komunikatu Toast
        Toast.makeText(this, "Usługa została uruchomiona", Toast.LENGTH_LONG).show()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
