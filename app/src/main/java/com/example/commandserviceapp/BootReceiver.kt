package com.example.commandserviceapp

import BootWorker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/*class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "onReceive called")  // Dodaj to logowanie
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "BOOT_COMPLETED received, starting service")
            val serviceIntent = Intent(context, CommandService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}*/
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "onReceive: BOOT_COMPLETED received")
            Toast.makeText(context, "Aplikacja uruchomiona po starcie systemu", Toast.LENGTH_LONG).show()
            // Tworzenie i uruchomienie zadania WorkManagera
            val workRequest = OneTimeWorkRequestBuilder<BootWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
