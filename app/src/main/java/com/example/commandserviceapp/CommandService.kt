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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class CommandService : Service() {

    companion object {
        private const val CHANNEL_ID = "CommandServiceChannel"
        private const val CHANNEL_NAME = "Command Service Notifications"
    }

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

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

        // Inicjalizacja połączenia WebSocket
        connectWebSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Service destroyed")
        }
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

    private fun connectWebSocket() {
        val request = Request.Builder().url("ws://51.20.193.191:8765").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("WebSocket", "Connected to server")
                webSocket.send("777")  // Wysyłanie identyfikatora klienta po połączeniu
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received message: $text")
                handleCommand(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Received bytes: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
            }
        })

        client.dispatcher.executorService.shutdown()
    }

    private fun handleCommand(command: String) {
        when (command) {
            "start" -> startSomeFunction()
            "stop" -> stopSomeFunction()
            else -> Log.d("WebSocket", "Unknown command: $command")
        }
    }

    private fun startSomeFunction() {
        Log.d("CommandService", "Some function started")
        // Tutaj dodaj logikę dla funkcji start
    }

    private fun stopSomeFunction() {
        Log.d("CommandService", "Some function stopped")
        // Tutaj dodaj logikę dla funkcji stop
    }

}
