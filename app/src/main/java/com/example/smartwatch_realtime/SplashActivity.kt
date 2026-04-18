package com.example.smartwatch_realtime

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val preferenceManager = PreferenceManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val deviceId = preferenceManager.getDeviceId()
            if (deviceId == null) {
                // First install: Generate UUID and go to Setup
                val newId = UUID.randomUUID().toString()
                preferenceManager.setDeviceId(newId)
                
                val intent = Intent(this, SetupActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // Already setup: Go to Main
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }, 2000) // 2 seconds delay
    }
}
