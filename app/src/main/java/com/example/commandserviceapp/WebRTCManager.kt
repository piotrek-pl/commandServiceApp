package com.example.commandserviceapp

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.webrtc.*
import java.nio.ByteBuffer
import kotlin.random.Random

class WebRTCManager(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private val iceCandidateBuffer = mutableListOf<IceCandidate>() // Bufor do kandydatów ICE
    private var isRemoteDescriptionSet = false // Flaga oznaczająca, czy Remote SDP został ustawiony
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        Log.d("WebRTCManager", "Initializing WebRTCManager")

        // Sprawdzanie i żądanie uprawnień do nagrywania dźwięku
        checkAudioPermissions()

        // Ustawienie trybu audio na komunikację
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.d("WebRTCManager", "AudioManager mode set to IN_COMMUNICATION")

        // Obsługa urządzeń audio (np. wbudowany głośnik)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.forEach { device ->
                Log.d("WebRTCManager", "Output device: ${device.type}")
            }
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
                audioManager.isSpeakerphoneOn = true  // Zastąp setPreferredDevice na starszych wersjach
                Log.d("WebRTCManager", "Speakerphone is set to ON")
            }
        } else {
            // Dla starszych wersji Androida
            audioManager.isSpeakerphoneOn = true
            Log.d("WebRTCManager", "Speakerphone set to ON (legacy)")
        }

        // Inicjalizacja PeerConnectionFactory
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        Log.d("WebRTCManager", "PeerConnectionFactory initialized")

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        Log.d("WebRTCManager", "PeerConnectionFactory created")

        // Ustawienia dźwięku - Constraints (wyłączanie automatycznych funkcji WebRTC)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))  // Wyłącz typowe wykrywanie szumu
        }

        Log.d("WebRTCManager", "Audio constraints set: $audioConstraints")

        // Tworzenie AudioSource na podstawie tych samych parametrów
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        Log.d("WebRTCManager", "AudioSource created: $audioSource")

        // Tworzenie AudioTrack na podstawie AudioSource
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        Log.d("WebRTCManager", "AudioTrack created: ${audioTrack.id()}")

        // Sprawdzenie właściwości AudioTrack
        Log.d("WebRTCManager", "AudioTrack ID: ${audioTrack.id()}, kind: ${audioTrack.kind()}, enabled: ${audioTrack.enabled()}")

        // Sprawdzenie i logowanie głośności
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        Log.d(
            "WebRTCManager",
            "Current VOICE_CALL stream volume: $volume (max=${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)})"
        )
    }

    private fun checkAudioPermissions() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("WebRTCManager", "Requesting RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        } else {
            Log.d("WebRTCManager", "RECORD_AUDIO permission already granted")
        }
    }

    private fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        observer: PeerConnection.Observer
    ) {
        peerConnection?.close() // Upewnij się, że poprzednie połączenie jest zamknięte przed utworzeniem nowego
        Log.d("WebRTCManager", "Creating PeerConnection with ICE servers: $iceServers")

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, observer)
            ?: throw NullPointerException("PeerConnection is null")
        Log.d("WebRTCManager", "PeerConnection created")

        // Dodanie strumienia audio do PeerConnection
        val sender = peerConnection?.addTrack(audioTrack)
        if (sender != null) {
            Log.d("WebRTCManager", "Audio track added to PeerConnection: ${audioTrack.id()}")
        } else {
            Log.e("WebRTCManager", "Failed to add audio track to PeerConnection")
        }

        // Potwierdzenie, że ścieżka audio jest włączona
        Log.d("WebRTCManager", "Audio track enabled: ${audioTrack.enabled()}")

        audioTrack.setEnabled(true)
        Log.d("WebRTCManager", "Audio track enabled for transmission")
    }

    fun startCall(iceServers: List<PeerConnection.IceServer>, observer: PeerConnection.Observer) {
        Log.d("WebRTCManager", "Attempting to start call and register audio")

        if (audioTrack != null) {
            Log.d("WebRTCManager", "Audio track is set and ready for transmission")
        } else {
            Log.e("WebRTCManager", "Audio track is null, no audio will be transmitted")
        }

        createPeerConnection(iceServers, observer)
        Log.d("WebRTCManager", "Call started with ICE servers: $iceServers")

        // Zamiast generować dane, uruchamiamy AudioRecord do nagrywania z mikrofonu
        val bufferSize = AudioRecord.getMinBufferSize(
            48000, // 48kHz
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            48000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val audioBuffer = ByteArray(bufferSize)

        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording()
            Log.d("WebRTCManager", "Audio recording started with buffer size: $bufferSize")

            // Uruchamiamy wątek do zbierania danych audio
            Thread {
                Log.d("WebRTCManager", "Audio recording thread started")
                while (true) {
                    val read = audioRecord.read(audioBuffer, 0, bufferSize)
                    if (read > 0) {
                        // Debugowanie danych audio
                        val maxAmplitude = audioBuffer.maxOrNull() ?: 0
                        Log.d("WebRTCManager", "Audio data captured: $read bytes, max amplitude: $maxAmplitude")
                    } else {
                        Log.d("WebRTCManager", "No audio data captured, read: $read")
                    }
                }
            }.start()
        } else {
            Log.e("WebRTCManager", "AudioRecord initialization failed")
        }
    }


    // Funkcja generująca biały szum
    fun generateWhiteNoise(buffer: ByteArray) {
        val random = java.util.Random()
        for (i in buffer.indices) {
            buffer[i] = (random.nextInt(256) - 128).toByte()  // Generowanie losowych bajtów
        }
    }



    fun createOffer(sdpCallback: (String) -> Unit) {
        val mediaConstraints = MediaConstraints()
        Log.d("WebRTCManager", "Creating offer with constraints: $mediaConstraints")
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("WebRTCManager", "Offer created: ${sdp.description}")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        Log.d("WebRTCManager", "Local description create success")
                    }

                    override fun onSetSuccess() {
                        Log.d("WebRTCManager", "Local description set successfully")
                        sdpCallback(sdp.description)
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e("WebRTCManager", "Failed to create local description: $error")
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTCManager", "Failed to set local description: $error")
                    }
                }, sdp)
            }

            override fun onSetSuccess() {
                Log.d("WebRTCManager", "Offer set success")
            }

            override fun onCreateFailure(error: String) {
                Log.e("WebRTCManager", "Failed to create offer: $error")
            }

            override fun onSetFailure(error: String) {
                Log.e("WebRTCManager", "Failed to set offer: $error")
            }
        }, mediaConstraints)
    }

    fun handleAnswer(sdp: SessionDescription) {
        Log.d("WebRTCManager", "Handling remote SDP answer")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d("WebRTCManager", "Remote description create success")
            }

            override fun onSetSuccess() {
                Log.d(
                    "WebRTCManager",
                    "Remote description set successfully, ready for audio transmission"
                )
                isRemoteDescriptionSet = true // Ustawienie flagi po ustawieniu Remote SDP

                // Dodanie zbuforowanych kandydatów ICE
                iceCandidateBuffer.forEach { candidate ->
                    val success = peerConnection?.addIceCandidate(candidate)
                    if (success == true) {
                        Log.d("WebRTCManager", "Buffered ICE candidate added: ${candidate.sdp}")
                    } else {
                        Log.e("WebRTCManager", "Failed to add buffered ICE candidate: ${candidate.sdp}")
                    }
                }
                iceCandidateBuffer.clear()
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "Failed to create remote description: $error")
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Failed to set remote description: $error")
            }
        }, sdp)
    }

    fun handleIceCandidate(candidate: IceCandidate) {
        if (isRemoteDescriptionSet) {
            val success = peerConnection?.addIceCandidate(candidate)
            if (success == true) {
                Log.d("WebRTCManager", "ICE candidate added: ${candidate.sdp}")
            } else {
                Log.e("WebRTCManager", "Failed to add ICE candidate: ${candidate.sdp}")
            }
        } else {
            iceCandidateBuffer.add(candidate)
            Log.d("WebRTCManager", "Buffered ICE candidate: ${candidate.sdp}")
        }
    }

    fun endCall() {
        peerConnection?.close()
        peerConnection = null
        Log.d("WebRTCManager", "Call ended")
    }
}
