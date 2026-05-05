package com.example.smartwatch_realtime

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val preferenceManager = PreferenceManager(this)
        val etDeviceName = findViewById<EditText>(R.id.etDeviceName)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnContinue.setOnClickListener {
            val deviceName = etDeviceName.text.toString().trim()
            if (deviceName.isNotEmpty()) {
                preferenceManager.setDeviceName(deviceName)
            } else {
                preferenceManager.setDeviceName("Smartwatch Device")
            }

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
