package com.example.smartwatch_realtime

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("smartwatch_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_REALTIME_SYNC = "realtime_sync"
    }

    fun getDeviceId(): String {
        var id = sharedPreferences.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            setDeviceId(id)
        }
        return id
    }

    fun setDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceName(): String? {
        return sharedPreferences.getString(KEY_DEVICE_NAME, null)
    }

    fun setDeviceName(deviceName: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
    }

    fun getLastSyncTime(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun setLastSyncTime(time: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }

    fun isRealtimeSyncEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REALTIME_SYNC, false)
    }

    fun setRealtimeSync(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_REALTIME_SYNC, enabled).apply()
    }
}
