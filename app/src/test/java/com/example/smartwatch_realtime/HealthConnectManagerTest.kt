package com.example.smartwatch_realtime

import android.content.Context
import androidx.health.connect.client.records.*
import androidx.health.connect.client.testing.ExperimentalTestingApi
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalTestingApi::class)
class HealthConnectManagerTest {

    private lateinit var fakeClient: FakeHealthConnectClient
    private lateinit var manager: HealthConnectManager
    private lateinit var mockContext: Context
    private lateinit var fakePermissionController: FakePermissionController

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        fakePermissionController = FakePermissionController(grantAll = true)
        fakeClient = FakeHealthConnectClient(permissionController = fakePermissionController)
        manager = HealthConnectManager(mockContext, fakeClient)
    }

    @Test
    fun hasAllPermissions_granted() = runTest {
        assertTrue(manager.hasAllPermissions())
    }

    @Test
    fun hasAllPermissions_denied() = runTest {
        fakePermissionController.grantAll = false
        assertTrue(!manager.hasAllPermissions())
    }

    @Test
    fun readHeartRate_returnsLatest() = runTest {
        val now = Instant.now()
        val record1 = HeartRateRecord(
            startTime = now.minus(4, ChronoUnit.MINUTES),
            endTime = now.minus(3, ChronoUnit.MINUTES),
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            samples = listOf(HeartRateRecord.Sample(time = now.minus(3, ChronoUnit.MINUTES), beatsPerMinute = 75))
        )
        val record2 = HeartRateRecord(
            startTime = now.minus(2, ChronoUnit.MINUTES),
            endTime = now.minus(1, ChronoUnit.MINUTES),
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            samples = listOf(HeartRateRecord.Sample(time = now.minus(1, ChronoUnit.MINUTES), beatsPerMinute = 80))
        )
        
        fakeClient.insertRecords(listOf(record1, record2))

        val hr = manager.readHeartRate()
        assertNotNull(hr)
        assertEquals(80L, hr)
    }

    @Test
    fun readSteps_returnsTotal() = runTest {
        val now = Instant.now()
        val record1 = StepsRecord(
            startTime = now.minus(2, ChronoUnit.HOURS),
            endTime = now.minus(1, ChronoUnit.HOURS),
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            count = 500
        )
        val record2 = StepsRecord(
            startTime = now.minus(1, ChronoUnit.HOURS),
            endTime = now,
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            count = 1200
        )
        
        fakeClient.insertRecords(listOf(record1, record2))

        val totalSteps = manager.readSteps()
        assertNotNull(totalSteps)
        assertEquals(1700L, totalSteps)
    }

    @Test
    fun readSpO2_returnsLatest() = runTest {
        val now = Instant.now()
        val record1 = OxygenSaturationRecord(
            time = now.minus(2, ChronoUnit.HOURS),
            zoneOffset = ZoneOffset.UTC,
            percentage = androidx.health.connect.client.units.Percentage(96.0)
        )
        val record2 = OxygenSaturationRecord(
            time = now.minus(1, ChronoUnit.HOURS),
            zoneOffset = ZoneOffset.UTC,
            percentage = androidx.health.connect.client.units.Percentage(98.0)
        )
        
        fakeClient.insertRecords(listOf(record1, record2))

        val spo2 = manager.readSpO2()
        assertNotNull(spo2)
        assertEquals(98.0, spo2!!, 0.01)
    }

    @Test
    fun readHRV_returnsLatest() = runTest {
        val now = Instant.now()
        val record1 = HeartRateVariabilityRmssdRecord(
            time = now.minus(2, ChronoUnit.HOURS),
            zoneOffset = ZoneOffset.UTC,
            heartRateVariabilityMillis = 40.5
        )
        val record2 = HeartRateVariabilityRmssdRecord(
            time = now.minus(1, ChronoUnit.HOURS),
            zoneOffset = ZoneOffset.UTC,
            heartRateVariabilityMillis = 45.2
        )
        
        fakeClient.insertRecords(listOf(record1, record2))

        val hrv = manager.readHRV()
        assertNotNull(hrv)
        assertEquals(45.2, hrv!!, 0.01)
    }

    @Test
    fun readCalories_returnsTotal() = runTest {
        val now = Instant.now()
        val record1 = ActiveCaloriesBurnedRecord(
            startTime = now.minus(2, ChronoUnit.HOURS),
            endTime = now.minus(1, ChronoUnit.HOURS),
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            energy = androidx.health.connect.client.units.Energy.kilocalories(150.0)
        )
        val record2 = ActiveCaloriesBurnedRecord(
            startTime = now.minus(1, ChronoUnit.HOURS),
            endTime = now,
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            energy = androidx.health.connect.client.units.Energy.kilocalories(200.0)
        )
        
        fakeClient.insertRecords(listOf(record1, record2))

        val cals = manager.readCalories()
        assertNotNull(cals)
        assertEquals(350.0, cals!!, 0.01)
    }
}
