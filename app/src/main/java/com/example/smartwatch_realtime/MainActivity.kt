package com.example.smartwatch_realtime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private val database = FirebaseDatabase.getInstance().reference
    private lateinit var preferenceManager: PreferenceManager
    private var pollingJob: Job? = null

    // UI Elements
    private lateinit var tvDeviceNameLabel: TextView
    private lateinit var tvHR: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvSpo2: TextView
    private lateinit var tvHrv: TextView
    private lateinit var tvCalories: TextView
    private lateinit var btnEditDevice: android.widget.ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var switchRealtime: SwitchMaterial
    private lateinit var btnUpdateNow: Button
    private lateinit var tvLastSync: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.headerContent)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        try {
            healthConnectManager = HealthConnectManager(this)
        } catch (e: Exception) {
            Log.e("SmartwatchApp", "Failed to init HealthConnectManager", e)
        }
        preferenceManager = PreferenceManager(this)
        
        initUI()
        
        // Setup Background Sync
        setupPeriodicSync()
        
        // Auto-start tracking based on saved preference
        startService()
    }

    private fun initUI() {
        tvDeviceNameLabel = findViewById(R.id.tvDeviceNameLabel)
        tvHR = findViewById(R.id.tvHR)
        tvSteps = findViewById(R.id.tvSteps)
        tvSpo2 = findViewById(R.id.tvSpo2)
        tvHrv = findViewById(R.id.tvHrv)
        tvCalories = findViewById(R.id.tvCalories)
        
        // Sync Section
        tvStatus = findViewById(R.id.tvStatus)
        switchRealtime = findViewById(R.id.switchRealtime)
        btnUpdateNow = findViewById(R.id.btnUpdateNow)
        tvLastSync = findViewById(R.id.tvLastSync)
        btnEditDevice = findViewById(R.id.btnEditDevice)

        // Set Device Name
        updateDeviceNameLabel()

        // Sync Controls
        switchRealtime.isChecked = preferenceManager.isRealtimeSyncEnabled()
        updateStatusUI(switchRealtime.isChecked)

        switchRealtime.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setRealtimeSync(isChecked)
            updateStatusUI(isChecked)
            if (isChecked) {
                checkPermissionsAndStart()
            } else {
                pollingJob?.cancel()
            }
        }

        btnUpdateNow.setOnClickListener {
            triggerManualSync()
        }

        // Edit Device Logic
        btnEditDevice.setOnClickListener {
            showEditDeviceDialog()
        }
        
        // Initial Last Sync Text
        updateLastSyncText()
    }

    private fun updateStatusUI(connected: Boolean) {
        if (connected) {
            tvStatus.text = "Connected"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981")) // Green
        } else {
            tvStatus.text = "Disconnected"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#6B7280")) // Gray
        }
    }

    private fun updateLastSyncText() {
        val lastSync = preferenceManager.getLastSyncTime()
        if (lastSync > 0) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvLastSync.text = "Terakhir diperbarui: ${sdf.format(Date(lastSync))}"
        }
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "health_sync_worker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun triggerManualSync() {
        val manualRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>().build()
        WorkManager.getInstance(this).enqueue(manualRequest)
        Toast.makeText(this, "Sinkronisasi dimulai...", Toast.LENGTH_SHORT).show()
        
        // Observe status if needed
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(manualRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    updateLastSyncText()
                }
            }
    }

    private fun updateDeviceNameLabel() {
        val deviceName = preferenceManager.getDeviceName() ?: "Unknown Device"
        tvDeviceNameLabel.text = "Perangkat: $deviceName"
    }

    private fun showEditDeviceDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Edit Nama Perangkat")

        val input = android.widget.EditText(this)
        input.setText(preferenceManager.getDeviceName() ?: "")
        input.setPadding(48, 48, 48, 48)
        builder.setView(input)

        builder.setPositiveButton("Simpan") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                preferenceManager.setDeviceName(newName)
                updateDeviceNameLabel()
                android.widget.Toast.makeText(this, "Nama diperbarui!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Batal") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun startService() {
        if (!::healthConnectManager.isInitialized) {
            Toast.makeText(this, "Health Connect Manager not initialized.", Toast.LENGTH_LONG).show()
            return
        }
        testDatabaseConnection()
        syncDeviceDetails()
        if (preferenceManager.isRealtimeSyncEnabled()) {
            checkPermissionsAndStart()
        }
    }

    private fun syncDeviceDetails() {
        val deviceId = preferenceManager.getDeviceId()
        val deviceName = preferenceManager.getDeviceName() ?: "Unknown Device"
        val timestamp = System.currentTimeMillis()

        val deviceData = hashMapOf(
            "deviceName" to deviceName,
            "lastSync" to timestamp
        )

        database.child("devices").child(deviceId).setValue(deviceData)
            .addOnSuccessListener {
                Log.d("SmartwatchApp", "Device details synced")
            }
    }

    private fun testDatabaseConnection() {
        val testData = hashMapOf("status" to "online", "timestamp" to System.currentTimeMillis())
        FirebaseDatabase.getInstance().getReference("connectivity_test")
            .push()
            .setValue(testData)
            .addOnSuccessListener {
                Log.d("FIREBASE", "Koneksi Realtime Database OK!")
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Koneksi Realtime Database GAGAL: ${e.message}", e)
            }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthConnectManager.permissions)) {
            startDataPolling()
        } else {
            Toast.makeText(this, "Permissions denied for Health Connect", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SmartwatchApp", "Background Health Read Permission Granted")
        } else {
            Log.w("SmartwatchApp", "Background Health Read Permission Denied")
        }
    }

    private fun checkPermissionsAndStart() {
        lifecycleScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                // Also check for background reading permission on Android 14+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val bgPermission = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
                    if (checkSelfPermission(bgPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestBackgroundPermissionLauncher.launch(bgPermission)
                    }
                }
                startDataPolling()
            } else {
                requestPermissionLauncher.launch(healthConnectManager.permissions)
            }
        }
    }

    private fun startDataPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            // Use repeatOnLifecycle to only poll while the activity is at least STARTED
            // This prevents reading Health Connect while in background which causes crashes
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        // Fetch Real Data from Health Connect (Using default windows for UI display)
                        val hrLong = healthConnectManager.readHeartRate()
                        val stepsLong = healthConnectManager.readSteps()
                        val spo2Double = healthConnectManager.readSpO2()
                        val hrvDouble = healthConnectManager.readHRV()
                        val caloriesDouble = healthConnectManager.readCalories()
                        
                        val hr = hrLong?.toInt() ?: 0
                        val steps = stepsLong?.toInt() ?: 0
                        val spo2 = spo2Double ?: 0.0
                        val hrv = hrvDouble ?: 0.0
                        val calories = caloriesDouble ?: 0.0

                        val spo2Formatted = String.format("%.1f", spo2)
                        val hrvFormatted = String.format("%.0f", hrv)
                        val caloriesFormatted = String.format("%.1f", calories)

                        // Update UI
                        tvHR.text = hr.toString()
                        tvSteps.text = steps.toString()
                        tvSpo2.text = spo2Formatted + "%"
                        tvHrv.text = hrvFormatted + " ms"
                        tvCalories.text = caloriesFormatted + " kcal"

                        // Send all data to Realtime Database
                        sendDataToRealtimeDatabase(hr, steps, spo2Formatted, hrvFormatted, caloriesFormatted)

                        Log.d("SmartwatchApp", "Data updated (1-min poll) and sent to RTDB")

                    } catch (e: Exception) {
                        Log.e("SmartwatchApp", "Error polling data: ${e.message}")
                    }

                    delay(60000) // Change to 1 minute (Step 1)
                }
            }
        }
    }

    private fun sendDataToRealtimeDatabase(hr: Int, steps: Int, spo2: String, hrv: String, calories: String) {
        val deviceId = preferenceManager.getDeviceId()
        val timestamp = System.currentTimeMillis()
        
        val data = hashMapOf(
            "heartRate" to hr,
            "steps" to steps,
            "oxygenSaturation" to spo2.replace("%", "").toDoubleOrNull(),
            "heartRateVariabilityRmssd" to hrv.replace(" ms", "").toDoubleOrNull(),
            "activeCaloriesBurned" to calories.replace(" kcal", "").toDoubleOrNull()
        )

        // Path: health_data/{deviceId}/{timestamp}
        database.child("health_data").child(deviceId).child(timestamp.toString())
            .setValue(data)
            .addOnSuccessListener {
                Log.d("FIREBASE", "Health data synced to RTDB")
                // Update lastSync in devices/{deviceId}
                database.child("devices").child(deviceId).child("lastSync").setValue(timestamp)
                // Update local lastSyncTime
                preferenceManager.setLastSyncTime(timestamp)
                updateLastSyncText()
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Gagal kirim ke Realtime Database", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}