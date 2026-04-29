package com.example.smartwatch_realtime

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectManager(
    private val context: Context,
    private var client: HealthConnectClient? = null
) {
    private val healthConnectClient by lazy { client ?: HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    suspend fun readHeartRate(startTime: Instant? = null): Long? {
        // Look back 1 hour (optimized for 5-minute polling)
        val start = startTime ?: Instant.now().minus(1, ChronoUnit.HOURS)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        // Return the latest sample based on exact time
        return response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?.beatsPerMinute
    }

    suspend fun readSteps(startTime: Instant? = null): Long? {
        val start = startTime ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error reading steps: ${e.message}")
            0L
        }
    }

    suspend fun readSpO2(startTime: Instant? = null): Double? {
        // Look back 1 hour
        val start = startTime ?: Instant.now().minus(1, ChronoUnit.HOURS)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        return response.records
            .lastOrNull()
            ?.percentage
            ?.value
    }

    suspend fun readHRV(startTime: Instant? = null): Double? {
        // Look back 1 hour
        val start = startTime ?: Instant.now().minus(1, ChronoUnit.HOURS)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        return response.records.lastOrNull()?.heartRateVariabilityMillis
    }

    suspend fun readCalories(startTime: Instant? = null): Double? {
        val start = startTime ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = Instant.now()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, endTime)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error reading calories: ${e.message}")
            0.0
        }
    }

    suspend fun readDeviceModel(startTime: Instant? = null): String? {
        // Cari dari data seminggu ke belakang agar lebih banyak peluang mendapat meta data
        val start = startTime ?: Instant.now().minus(7, ChronoUnit.DAYS)
        val endTime = Instant.now()
        
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, endTime)
                )
            )
            val device = response.records.lastOrNull()?.metadata?.device
            if (device != null) {
                val manufacturer = device.manufacturer ?: ""
                val model = device.model ?: ""
                val fullName = "$manufacturer $model".trim()
                if (fullName.isNotEmpty()) fullName else null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Gagal membaca model jam: ${e.message}")
            null
        }
    }

}
