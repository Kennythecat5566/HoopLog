package com.hooplog.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

class LocationTrainingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var store: TrainingStore

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        store = TrainingStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission(this)) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        requestUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLocationChanged(location: Location) {
        handleLocation(location)
    }

    @Deprecated("Deprecated platform callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun handleLocation(location: Location) {
        val activeLocations = store.locations().filter { it.active }
        val inside = activeLocations.any { spot ->
            distanceMeters(location.latitude, location.longitude, spot.latitude, spot.longitude) <= spot.radiusMeters
        }
        val prefs = prefs()
        val wasInside = prefs.getBoolean(KEY_INSIDE, false)
        if (inside && !wasInside) {
            val date = LocalDate.now().toString()
            store.startSession(date)
            prefs.edit()
                .putBoolean(KEY_INSIDE, true)
                .putString(KEY_SESSION_DATE, date)
                .apply()
        } else if (!inside && wasInside) {
            val date = prefs.getString(KEY_SESSION_DATE, null) ?: LocalDate.now().toString()
            store.endSession(date)
            prefs.edit()
                .putBoolean(KEY_INSIDE, false)
                .remove(KEY_SESSION_DATE)
                .apply()
        }
    }

    @Suppress("MissingPermission")
    private fun requestUpdates() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(provider, 30_000L, 20f, this, Looper.getMainLooper())
            locationManager.getLastKnownLocation(provider)?.let { handleLocation(it) }
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Trainly GPS training")
        .setContentText("Detecting training locations")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GPS training", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val CHANNEL_ID = "gps_training"
        private const val NOTIFICATION_ID = 1207
        private const val PREFS_NAME = "gps_training_service"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INSIDE = "inside"
        private const val KEY_SESSION_DATE = "session_date"

        fun start(context: Context) {
            setEnabled(context, true)
            val intent = Intent(context, LocationTrainingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val date = prefs.getString(KEY_SESSION_DATE, null)
            if (prefs.getBoolean(KEY_INSIDE, false) && date != null) {
                TrainingStore(context).endSession(date)
            }
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_INSIDE, false)
                .remove(KEY_SESSION_DATE)
                .apply()
            context.stopService(Intent(context, LocationTrainingService::class.java))
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        private fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }
    }
}

private fun distanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double
): Float {
    val results = FloatArray(1)
    Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, results)
    return results[0]
}
