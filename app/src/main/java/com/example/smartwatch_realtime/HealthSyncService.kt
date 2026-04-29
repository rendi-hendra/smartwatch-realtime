package com.example.smartwatch_realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
// import android.os.PowerManager removed
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HealthSyncService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var preferenceManager: PreferenceManager
    private val database = FirebaseDatabase.getInstance().reference

    private var hrPollingJob: Job? = null
    private var generalPollingJob: Job? = null

    private var lastHr: Int = 0
    private var lastSteps: Int = 0
    private var lastSpo2: String = "0.0"
    private var lastHrv: String = "0.0"
    private var lastCalories: String = "0.0"

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "HealthSyncChannel"
        const val ACTION_SYNC_UPDATE = "com.example.smartwatch_realtime.SYNC_UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        healthConnectManager = HealthConnectManager(this)
        preferenceManager = PreferenceManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smartwatch Realtime")
            .setContentText("Sinkronisasi latar belakang sedang berjalan...")
            .setSmallIcon(R.mipmap.ic_launcher_new) // Ensure this icon exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startDataPolling()

        // Restart service if killed by system
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Sync Service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Notifikasi untuk sinkronisasi data kesehatan di latar belakang"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startDataPolling() {
        if (hrPollingJob?.isActive == true || generalPollingJob?.isActive == true) return
        
        // Polling HR tiap 10 detik
        hrPollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (healthConnectManager.hasAllPermissions()) {
                        val hrLong = healthConnectManager.readHeartRate()
                        val hr = hrLong?.toInt() ?: 0
                        if (hr > 0) lastHr = hr

                        sendDataToRealtimeDatabase(hr = hr, steps = null, spo2 = null, hrv = null, calories = null)
                        broadcastUpdate()
                        Log.d("HealthSyncService", "HR polled successfully (10s interval).")
                    }
                } catch (e: Exception) {
                    Log.e("HealthSyncService", "Error polling HR: ${e.message}", e)
                }
                delay(10000)
            }
        }

        // Polling data lain tiap 5 menit
        generalPollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (healthConnectManager.hasAllPermissions()) {
                        val stepsLong = healthConnectManager.readSteps()
                        val spo2Double = healthConnectManager.readSpO2()
                        val hrvDouble = healthConnectManager.readHRV()
                        val caloriesDouble = healthConnectManager.readCalories()

                        val steps = stepsLong?.toInt() ?: 0
                        val spo2 = spo2Double ?: 0.0
                        val hrv = hrvDouble ?: 0.0
                        val calories = caloriesDouble ?: 0.0

                        val spo2Formatted = String.format(java.util.Locale.US, "%.1f", spo2)
                        val hrvFormatted = String.format(java.util.Locale.US, "%.0f", hrv)
                        val caloriesFormatted = String.format(java.util.Locale.US, "%.1f", calories)

                        if (steps > 0) lastSteps = steps
                        if (spo2 > 0.0) lastSpo2 = spo2Formatted
                        if (hrv > 0.0) lastHrv = hrvFormatted
                        if (calories > 0.0) lastCalories = caloriesFormatted

                        // Update device model if not set
                        val currentName = preferenceManager.getDeviceName() ?: "Unknown Device"
                        if (!currentName.contains("(") && !currentName.contains(")")) {
                            val detectedModel = healthConnectManager.readDeviceModel()
                            if (detectedModel != null) {
                                val newName = "$currentName ($detectedModel)"
                                preferenceManager.setDeviceName(newName)
                                val deviceId = preferenceManager.getDeviceId()
                                if (deviceId != null) {
                                    database.child("devices").child(deviceId).child("deviceName").setValue(newName)
                                }
                            }
                        }

                        sendDataToRealtimeDatabase(hr = null, steps = steps, spo2 = spo2Formatted, hrv = hrvFormatted, calories = caloriesFormatted)
                        broadcastUpdate()

                        Log.d("HealthSyncService", "General data polled successfully (5m interval).")
                    } else {
                        Log.w("HealthSyncService", "Health Connect permissions missing.")
                    }
                } catch (e: Exception) {
                    Log.e("HealthSyncService", "Error polling general data: ${e.message}", e)
                }
                delay(300000)
            }
        }
    }

    private fun broadcastUpdate() {
        val broadcastIntent = Intent(ACTION_SYNC_UPDATE).apply {
            setPackage(packageName)
            putExtra("hr", lastHr)
            putExtra("steps", lastSteps)
            putExtra("spo2", lastSpo2)
            putExtra("hrv", lastHrv)
            putExtra("calories", lastCalories)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun sendDataToRealtimeDatabase(hr: Int?, steps: Int?, spo2: String?, hrv: String?, calories: String?) {
        val deviceId = preferenceManager.getDeviceId() ?: return
        val timestamp = System.currentTimeMillis()

        val data = hashMapOf<String, Any>()
        
        if (hr != null && hr > 0) data["heartRate"] = hr
        if (steps != null && steps > 0) data["steps"] = steps
        
        if (spo2 != null) {
            val spo2Value = spo2.replace("%", "").toDoubleOrNull()
            if (spo2Value != null && spo2Value > 0.0) data["oxygenSaturation"] = spo2Value
        }
        
        if (hrv != null) {
            val hrvValue = hrv.replace(" ms", "").toDoubleOrNull()
            if (hrvValue != null && hrvValue > 0.0) data["heartRateVariabilityRmssd"] = hrvValue
        }
        
        if (calories != null) {
            val caloriesValue = calories.replace(" kcal", "").toDoubleOrNull()
            if (caloriesValue != null && caloriesValue > 0.0) data["activeCaloriesBurned"] = caloriesValue
        }

        if (data.isEmpty()) {
            Log.d("HealthSyncService", "No valid data to send to Firebase")
            return
        }

        database.child("health_data").child(deviceId).child(timestamp.toString())
            .setValue(data)
            .addOnSuccessListener {
                database.child("devices").child(deviceId).child("lastSync").setValue(timestamp)
                preferenceManager.setLastSyncTime(timestamp)
            }
            .addOnFailureListener { e ->
                Log.e("HealthSyncService", "Failed to send data to Firebase", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
