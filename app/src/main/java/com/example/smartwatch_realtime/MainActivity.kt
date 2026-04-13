package com.example.smartwatch_realtime

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private var socket: Socket? = null

    // Replace with your local machine IP if testing on a real device
    private val BACKEND_URL = "http://192.168.100.17:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        healthConnectManager = HealthConnectManager(this)
        setupSocket()
        checkPermissionsAndStart()
    }

    private fun setupSocket() {
        try {
            socket = IO.socket(BACKEND_URL)
            socket?.connect()
            Log.d("SmartwatchApp", "Connecting to socket...")
        } catch (e: Exception) {
            Log.e("SmartwatchApp", "Socket connection failed", e)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthConnectManager.permissions)) {
            startDataPolling()
        }
    }

    private fun checkPermissionsAndStart() {
        lifecycleScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                startDataPolling()
            } else {
                requestPermissionLauncher.launch(healthConnectManager.permissions)
            }
        }
    }

    private fun startDataPolling() {
        lifecycleScope.launch {
            while (true) {
                try {
                    // Generate Dummy Data for Testing
                    val hr = (60..100).random()
                    val steps = (0..10000).random()
                    val spo2 = (95..99).random() + Math.random()
                    
                    val data = JSONObject().apply {
                        put("hr", hr)
                        put("steps", steps)
                        put("spo2", String.format("%.1f", spo2))
                        put("timestamp", System.currentTimeMillis())
                    }

                    Log.d("SmartwatchApp", "Sending data: $data")
                    socket?.emit("sensor_data", data)

                } catch (e: Exception) {
                    Log.e("SmartwatchApp", "Error polling data", e)
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
    }
}