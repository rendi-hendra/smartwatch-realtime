package com.example.smartwatch_realtime

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private var socket: Socket? = null
    private var pollingJob: Job? = null

    // UI Elements
    private lateinit var tvStatus: TextView
    private lateinit var tvHR: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvSpo2: TextView
    private lateinit var tvDebug: TextView
    private lateinit var switchConnect: MaterialSwitch

    // Use 10.0.2.2 to connect to host machine from Android Emulator
    private val BACKEND_URL = "http://10.0.2.2:3000"

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
        initUI()
    }

    private fun initUI() {
        tvStatus = findViewById(R.id.tvStatus)
        tvHR = findViewById(R.id.tvHR)
        tvSteps = findViewById(R.id.tvSteps)
        tvSpo2 = findViewById(R.id.tvSpo2)
        tvDebug = findViewById(R.id.tvDebug)
        switchConnect = findViewById(R.id.switchConnect)

        switchConnect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startService()
            } else {
                stopService()
            }
        }
    }

    private fun startService() {
        tvStatus.text = "Connecting..."
        tvStatus.setTextColor(Color.YELLOW)
        setupSocket()
        checkPermissionsAndStart()
    }

    private fun stopService() {
        pollingJob?.cancel()
        socket?.disconnect()
        tvStatus.text = "Disconnected"
        tvStatus.setTextColor(Color.parseColor("#B0B0B0"))
        tvDebug.text = "Service stopped"
    }

    private fun setupSocket() {
        try {
            socket = IO.socket(BACKEND_URL)
            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread {
                    tvStatus.text = "Connected"
                    tvStatus.setTextColor(Color.GREEN)
                    tvDebug.text = "Listening for sensor data..."
                }
            }
            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread {
                    tvStatus.text = "Disconnected"
                    tvStatus.setTextColor(Color.parseColor("#B0B0B0"))
                }
            }
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                runOnUiThread {
                    val error = args.getOrNull(0)?.toString() ?: "Unknown error"
                    tvStatus.text = "Error"
                    tvStatus.setTextColor(Color.RED)
                    tvDebug.text = "Connection failed: $error"
                    Log.e("SmartwatchApp", "Connection error: $error")
                }
            }
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
        } else {
            tvDebug.text = "Permissions denied"
            switchConnect.isChecked = false
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
        pollingJob?.cancel() // Ensure no duplicate jobs
        pollingJob = lifecycleScope.launch {
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

                    // Update UI
                    runOnUiThread {
                        tvHR.text = hr.toString()
                        tvSteps.text = steps.toString()
                        tvSpo2.text = String.format("%.1f", spo2)
                        tvDebug.text = "Last sent: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
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