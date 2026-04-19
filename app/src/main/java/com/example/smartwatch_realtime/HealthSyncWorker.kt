package com.example.smartwatch_realtime

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.time.Instant

class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val database = FirebaseDatabase.getInstance().reference
    private val preferenceManager = PreferenceManager(appContext)
    private val healthConnectManager = HealthConnectManager(appContext)

    override suspend fun doWork(): Result {
        Log.i("HealthSyncWorker", "Background sync started...")

        try {
            if (!healthConnectManager.hasAllPermissions()) {
                Log.w("HealthSyncWorker", "No permissions, skipping sync")
                return Result.failure()
            }

            val deviceId = preferenceManager.getDeviceId() ?: return Result.failure()
            
            // Fetch current data from Health Connect (gunakan default window)
            val hr = healthConnectManager.readHeartRate()
            val steps = healthConnectManager.readSteps()
            val spo2 = healthConnectManager.readSpO2()
            val hrv = healthConnectManager.readHRV()
            val calories = healthConnectManager.readCalories()

            val timestamp = System.currentTimeMillis()

            // Prepare data map matching the new structure
            val data = hashMapOf(
                "heartRate" to (hr?.toInt() ?: 0),
                "steps" to (steps?.toInt() ?: 0),
                "oxygenSaturation" to (spo2 ?: 0.0),
                "heartRateVariabilityRmssd" to (hrv ?: 0.0),
                "activeCaloriesBurned" to (calories ?: 0.0)
            )

            // Step 3: Save to health_data/{deviceId}/{timestamp}
            database.child("health_data").child(deviceId).child(timestamp.toString())
                .setValue(data).await()

            // Step 4: Update lastSync in devices/{deviceId}
            database.child("devices").child(deviceId).child("lastSync").setValue(timestamp).await()
            
            // Update local lastSyncTime
            preferenceManager.setLastSyncTime(timestamp)

            Log.i("HealthSyncWorker", "Background sync successful for device: $deviceId")
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("HealthSyncWorker", "Background sync failed", e)
            return Result.retry()
        }
    }

    private fun createDataMap(
        deviceId: String,
        deviceName: String,
        type: String,
        value: Any,
        timestamp: Long
    ): Map<String, Any> {
        return hashMapOf(
            "deviceId" to deviceId,
            "nameDevice" to deviceName,
            "type" to type,
            "value" to value,
            "timestamp" to timestamp
        )
    }
}
