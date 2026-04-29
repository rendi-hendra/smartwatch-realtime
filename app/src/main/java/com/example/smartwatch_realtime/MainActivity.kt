package com.example.smartwatch_realtime

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private val database = FirebaseDatabase.getInstance().reference
    private lateinit var preferenceManager: PreferenceManager

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

    private val syncUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HealthSyncService.ACTION_SYNC_UPDATE) {
                val hr = intent.getIntExtra("hr", 0)
                val steps = intent.getIntExtra("steps", 0)
                val spo2 = intent.getStringExtra("spo2") ?: "0.0"
                val hrv = intent.getStringExtra("hrv") ?: "0.0"
                val calories = intent.getStringExtra("calories") ?: "0.0"

                tvHR.text = hr.toString()
                tvSteps.text = steps.toString()
                tvSpo2.text = "$spo2%"
                tvHrv.text = "$hrv ms"
                tvCalories.text = "$calories kcal"

                updateDeviceNameLabel()
                updateLastSyncText()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.headerBg)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        try {
            healthConnectManager = HealthConnectManager(this)
        } catch (e: Exception) {
            Log.e("SmartwatchApp", "Failed to init HealthConnectManager", e)
        }
        preferenceManager = PreferenceManager(this)
        
        initUI()
        
        // Setup Background Sync (Fallback using WorkManager)
        setupPeriodicSync()
        
        // Auto-start tracking based on saved preference
        startServiceIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            syncUpdateReceiver,
            IntentFilter(HealthSyncService.ACTION_SYNC_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        fetchCurrentDataAndUpdateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(syncUpdateReceiver)
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
                val serviceIntent = Intent(this, HealthSyncService::class.java)
                stopService(serviceIntent)
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
                val deviceId = preferenceManager.getDeviceId()
                if (deviceId != null) {
                    database.child("devices").child(deviceId).child("deviceName").setValue(newName)
                }
                android.widget.Toast.makeText(this, "Nama diperbarui!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Batal") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun startServiceIfNeeded() {
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
        val deviceId = preferenceManager.getDeviceId() ?: return
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
            switchRealtime.isChecked = false
        }
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SmartwatchApp", "Permission Granted")
        } else {
            Log.w("SmartwatchApp", "Permission Denied")
        }
    }

    private fun checkPermissionsAndStart() {
        lifecycleScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                // Check for background reading permission on Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val bgPermission = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
                    if (checkSelfPermission(bgPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestBackgroundPermissionLauncher.launch(bgPermission)
                    }
                }
                // Check for Notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestBackgroundPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                startDataPolling()
            } else {
                requestPermissionLauncher.launch(healthConnectManager.permissions)
            }
        }
    }

    private fun startDataPolling() {
        Log.d("SmartwatchApp", "Starting Foreground HealthSyncService")
        val serviceIntent = Intent(this, HealthSyncService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun fetchCurrentDataAndUpdateUI() {
        lifecycleScope.launch {
            try {
                if (healthConnectManager.hasAllPermissions()) {
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

                    val spo2Formatted = String.format(java.util.Locale.US, "%.1f", spo2)
                    val hrvFormatted = String.format(java.util.Locale.US, "%.0f", hrv)
                    val caloriesFormatted = String.format(java.util.Locale.US, "%.1f", calories)

                    tvHR.text = hr.toString()
                    tvSteps.text = steps.toString()
                    tvSpo2.text = "$spo2Formatted%"
                    tvHrv.text = "$hrvFormatted ms"
                    tvCalories.text = "$caloriesFormatted kcal"

                    updateDeviceNameLabel()
                    updateLastSyncText()
                }
            } catch (e: Exception) {
                Log.e("SmartwatchApp", "Error fetching initial data", e)
            }
        }
    }
}