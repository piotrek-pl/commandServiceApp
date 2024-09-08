package com.example.commandserviceapp

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRTCManager(context: Context) {

    private val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack


    init {
        // Inicjalizacja PeerConnectionFactory
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        // Inicjalizacja AudioSource i AudioTrack, bez lokalnego nagrywania
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)

        // Inicjalizacja PeerConnection
        peerConnection = peerConnectionFactory.createPeerConnection(listOf(), object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d("WebRTCManager", "Signaling state changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("WebRTCManager", "ICE connection state changed: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("WebRTCManager", "ICE connection receiving change: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d("WebRTCManager", "ICE gathering state changed: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d("WebRTCManager", "New ICE candidate: ${candidate.sdp}")
            }

            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                Log.d("WebRTCManager", "ICE candidates removed")
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d("WebRTCManager", "Stream added: ${stream.id}")
            }

            override fun onRemoveStream(stream: MediaStream) {
                Log.d("WebRTCManager", "Stream removed: ${stream.id}")
            }

            override fun onDataChannel(channel: DataChannel) {
                Log.d("WebRTCManager", "Data channel received: ${channel.label()}")
            }

            override fun onRenegotiationNeeded() {
                Log.d("WebRTCManager", "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {
                val trackKind = receiver.track()?.kind()
                if (trackKind != null) {
                    Log.d("WebRTCManager", "Track added: $trackKind")
                } else {
                    Log.w("WebRTCManager", "Track added, but kind is null")
                }
            }
        })

        peerConnection?.addTrack(audioTrack)
    }

    fun startCall(iceServers: List<PeerConnection.IceServer>, observer: PeerConnection.Observer) {
        // Ponowna inicjalizacja PeerConnection z serwerami ICE
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, observer)
        peerConnection?.addTrack(audioTrack) ?: throw NullPointerException("PeerConnection is null")
        Log.d("WebRTCManager", "Call started with ICE servers: $iceServers")
    }

    fun createOffer(sdpCallback: (String) -> Unit) {
        val mediaConstraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("WebRTCManager", "Offer created: ${sdp.description}")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d("WebRTCManager", "Local description set successfully")

                        // Przekazanie SDP do CommandService przez callback
                        sdpCallback(sdp.description)
                    }

                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTCManager", "Failed to set local description: $error")
                    }
                }, sdp)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {
                Log.e("WebRTCManager", "Failed to create offer: $error")
            }

            override fun onSetFailure(error: String) {}
        }, mediaConstraints)
    }


    fun handleAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRTCManager", "Remote description set successfully")
            }

            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Failed to set remote description: $error")
            }
        }, sdp)
    }

    fun handleIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d("WebRTCManager", "ICE candidate added: ${candidate.sdp}")
    }
}
