package com.idlabs.trace

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore

/**
 * Visible, owner-enabled Phone Finder location service.
 * It only reports while the owner has started recovery from the Studio app.
 */
class PhoneFinderService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.idlabs.trace.phonefinder.START"
        const val ACTION_STOP = "com.idlabs.trace.phonefinder.STOP"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_TOKEN = "device_token"
        private const val CHANNEL_ID = "phone_finder_location"
        private const val NOTIFICATION_ID = 4812
        private const val PREFS = "phone_finder_secure"
        private const val API_URL = "https://fgkylbebokqcviqcbmoi.supabase.co/functions/v1/phone-finder"
        private const val INTERVAL_MS = 5 * 60 * 1000L
        private const val MIN_DISTANCE_M = 100f
    }

    private lateinit var locationManager: LocationManager
    private var deviceId: String = ""
    private var deviceToken: String = ""
    private var started = false

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecovery()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
            deviceToken = intent.getStringExtra(EXTRA_DEVICE_TOKEN).orEmpty()
            if (!deviceId.matches(Regex("(?i)^[0-9a-f-]{36}$")) || deviceToken.length < 32) {
                stopSelf()
                return START_NOT_STICKY
            }
            saveCredentials(deviceId, deviceToken)
        } else if (deviceId.isBlank() || deviceToken.isBlank()) {
            val saved = loadCredentials()
            deviceId = saved.first
            deviceToken = saved.second
        }

        if (deviceId.isBlank() || deviceToken.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startVisibleNotification()
            beginLocationUpdates()
            started = true
        } catch (security: SecurityException) {
            Log.w("PhoneFinder", "Location permission was removed", security)
            stopSelf()
            return START_NOT_STICKY
        } catch (error: Exception) {
            Log.e("PhoneFinder", "Could not start location updates", error)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun beginLocationUpdates() {
        var requested = false
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper())
                requested = true
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper())
            requested = true
        }
        if (!requested) throw IllegalStateException("Turn on Location and allow location access for IDRIS AI STUDIO.")
        val last = try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            else locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }
        last?.let { reportLocation(it) }
    }

    override fun onLocationChanged(location: Location) {
        reportLocation(location)
    }

    private fun reportLocation(location: Location) {
        val currentToken = deviceToken
        if (currentToken.isBlank()) return
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val payload = JSONObject().apply {
                    put("action", "report")
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracy_m", if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL)
                }
                connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $currentToken")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.w("PhoneFinder", "Location report rejected: HTTP $code ${errorText.take(180)}")
                } else {
                    Log.i("PhoneFinder", "Location report accepted")
                }
            } catch (error: Exception) {
                Log.w("PhoneFinder", "Location report failed; next update will retry", error)
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun startVisibleNotification() {
        val stopIntent = Intent(this, PhoneFinderService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 4813, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("IDRIS Phone Finder is active")
            .setContentText("Your phone is sharing its location with your IDRIS account.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop recovery", stopPending).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phone Finder location service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when your own phone is sharing its recovery location."
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun stopRecovery() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        started = false
        deviceToken = ""
        deviceId = ""
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveCredentials(id: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("device_id", id)
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun loadCredentials(): Pair<String, String> {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val id = prefs.getString("device_id", "").orEmpty()
        val iv = prefs.getString("token_iv", null) ?: return "" to ""
        val encrypted = prefs.getString("token_cipher", null) ?: return "" to ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            id to String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (error: Exception) {
            Log.e("PhoneFinder", "Could not unlock saved device credential", error)
            "" to ""
        }
    }

    private fun getOrCreateKey(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "idris_phone_finder_device_key"
        val existing = store.getKey(alias, null)
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        started = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}