package com.idlabs.trace

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Narrow JavaScript bridge for the IDRIS Studio Phone Finder screen.
 * Location starts only after the signed-in owner taps Enable and grants permissions.
 */
class PhoneFinderBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private var permissionRequestPending = false

    @JavascriptInterface
    fun prepareRecovery() {
        activity.runOnUiThread {
            if (hasRequiredPermissions()) {
                sendPermissionResult(true)
                return@runOnUiThread
            }
            if (permissionRequestPending) return@runOnUiThread
            permissionRequestPending = true
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            activity.requestPermissions(permissions.toTypedArray(), REQUEST_CODE)
        }
    }

    @JavascriptInterface
    fun startRecovery(deviceId: String, deviceToken: String): Boolean {
        if (!deviceId.matches(Regex("(?i)^[0-9a-f-]{36}$")) || deviceToken.length !in 32..200) return false
        if (!hasRequiredPermissions()) return false
        activity.runOnUiThread {
            val intent = Intent(activity, PhoneFinderService::class.java).apply {
                action = PhoneFinderService.ACTION_START
                putExtra(PhoneFinderService.EXTRA_DEVICE_ID, deviceId)
                putExtra(PhoneFinderService.EXTRA_DEVICE_TOKEN, deviceToken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.startForegroundService(intent)
            else activity.startService(intent)
        }
        return true
    }

    @JavascriptInterface
    fun stopRecovery(): Boolean {
        activity.runOnUiThread {
            activity.startService(Intent(activity, PhoneFinderService::class.java).setAction(PhoneFinderService.ACTION_STOP))
        }
        return true
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CODE) return
        permissionRequestPending = false
        sendPermissionResult(hasRequiredPermissions() && grantResults.isNotEmpty())
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationGranted =
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return locationGranted && notificationGranted
    }

    private fun sendPermissionResult(granted: Boolean) {
        webView.post {
            webView.evaluateJavascript(
                "if(window.IDRISPhoneFinderPermissionResult){window.IDRISPhoneFinderPermissionResult($granted)}",
                null
            )
        }
    }

    companion object { const val REQUEST_CODE = 4814 }
}