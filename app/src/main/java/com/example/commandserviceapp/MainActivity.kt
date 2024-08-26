package com.example.commandserviceapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Uruchomienie usługi
        val serviceIntent = Intent(this, CommandService::class.java)
        startForegroundService(serviceIntent)
        // Zamknięcie aktywności
        //finish()
    }
}