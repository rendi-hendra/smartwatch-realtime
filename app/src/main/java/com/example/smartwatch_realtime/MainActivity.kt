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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var pollingJob: Job? = null

    // UI Elements
    private lateinit var tvStatus: TextView
    private lateinit var tvHR: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvSpo2: TextView
    private lateinit var tvDebug: TextView
    private lateinit var switchConnect: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        try {
            healthConnectManager = HealthConnectManager(this)
        } catch (e: Exception) {
            Log.e("SmartwatchApp", "Failed to init HealthConnectManager", e)
        }
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
                try {
                    startService()
                } catch (e: Exception) {
                    Log.e("SmartwatchApp", "Crash prevented in startService", e)
                    tvDebug.text = "Error: ${e.localizedMessage}"
                    switchConnect.isChecked = false
                }
            } else {
                stopService()
            }
        }
    }

    private fun startService() {
        tvStatus.text = "Service Running"
        tvStatus.setTextColor(Color.GREEN)
        
        // Ensure healthConnectManager was successfully initialized in onCreate
        if (!::healthConnectManager.isInitialized) {
            throw IllegalStateException("Health Connect Manager not initialized. Device might not support it.")
        }
        
        testFirestoreConnection() // 🔍 Cek koneksi ke Firestore segera
        checkPermissionsAndStart()
    }

    private fun testFirestoreConnection() {
        tvDebug.text = "Testing Firestore..."
        val testData = hashMapOf("status" to "online", "timestamp" to System.currentTimeMillis())
        db.collection("connectivity_test")
            .add(testData)
            .addOnSuccessListener {
                Log.d("FIREBASE", "Koneksi Firestore OK!")
                tvDebug.text = "Firestore: Connected"
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Koneksi Firestore GAGAL: ${e.message}", e)
                tvDebug.text = "Firestore Error: ${e.localizedMessage}"
            }
    }

    private fun stopService() {
        pollingJob?.cancel()
        tvStatus.text = "Disconnected"
        tvStatus.setTextColor(Color.parseColor("#B0B0B0"))
        tvDebug.text = "Service stopped"
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
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (true) {
                try {
                    // 🚀 Fetch Real Data from Health Connect
                    val hrLong = healthConnectManager.readHeartRate()
                    val stepsLong = healthConnectManager.readSteps()
                    val spo2Double = healthConnectManager.readSpO2()

                    val hr = hrLong?.toInt() ?: 0
                    val steps = stepsLong?.toInt() ?: 0
                    val spo2 = spo2Double ?: 0.0

                    val spo2Formatted = String.format("%.1f", spo2)

                    // UI update
                    runOnUiThread {
                        tvHR.text = hr.toString()
                        tvSteps.text = steps.toString()
                        tvSpo2.text = spo2Formatted
                    }

                    // 🔥 FIRESTORE CALL INI WAJIB
                    sendDataToFirestore(hr, steps, spo2Formatted)

                    Log.d("SmartwatchApp", "Sent to Firestore")

                } catch (e: Exception) {
                    Log.e("SmartwatchApp", "Error polling data", e)
                }

                delay(5000)
            }
        }
    }

    private fun sendDataToFirestore(hr: Int, steps: Int, spo2: String) {
        val data = hashMapOf(
            "hr" to hr,
            "steps" to steps,
            "spo2" to spo2,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("sensor_data")
            .add(data)
            .addOnSuccessListener {
                Log.d("FIREBASE", "Data terkirim")
                tvDebug.text = "Sync OK (${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())})"
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Gagal kirim", e)
                tvDebug.text = "Sync Fail: ${e.localizedMessage}"
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}