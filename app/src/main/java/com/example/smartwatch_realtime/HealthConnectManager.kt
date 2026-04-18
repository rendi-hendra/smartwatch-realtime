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
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
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
        val start = startTime ?: Instant.now().minus(5, ChronoUnit.MINUTES)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        // Return latest HR
        return response.records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute
    }

    suspend fun readSteps(startTime: Instant? = null): Long? {
        val start = startTime ?: Instant.now().truncatedTo(ChronoUnit.DAYS)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        return response.records.sumOf { it.count }
    }

    suspend fun readSpO2(startTime: Instant? = null): Double? {
        val start = startTime ?: Instant.now().minus(1, ChronoUnit.DAYS)
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
        val start = startTime ?: Instant.now().minus(1, ChronoUnit.DAYS)
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
        val start = startTime ?: Instant.now().truncatedTo(ChronoUnit.DAYS)
        val endTime = Instant.now()
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, endTime)
            )
        )
        return response.records.sumOf { it.energy.inKilocalories }
    }


}
