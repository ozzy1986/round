package com.raund.app.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.raund.app.BuildConfig

data class BugReportRequest(
    val message: String,
    val screen: String,
    val device_manufacturer: String,
    val device_brand: String?,
    val device_model: String,
    val os_version: String,
    val os_incremental: String?,
    val sdk_int: Int,
    val app_version: String,
    val app_build: String,
    val build_display: String?,
    val build_fingerprint: String?,
    val security_patch: String?
)

data class BugReportResponse(
    val id: String,
    val created_at: String
)

object BugReportPayloadFactory {
    private fun sanitizeRequired(value: String, maxLength: Int): String =
        value.trim().take(maxLength)

    private fun sanitizeOptional(value: String?, maxLength: Int): String? =
        value
            ?.trim()
            ?.take(maxLength)
            ?.takeIf { it.isNotBlank() }

    fun create(context: Context, message: String, screen: String): BugReportRequest {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sanitizeOptional(Build.VERSION.SECURITY_PATCH, 32)
        } else {
            null
        }
        return BugReportRequest(
            message = message.trim(),
            screen = screen.trim(),
            device_manufacturer = sanitizeRequired(Build.MANUFACTURER, 120),
            device_brand = sanitizeOptional(Build.BRAND, 120),
            device_model = sanitizeRequired(Build.MODEL, 120),
            os_version = sanitizeRequired(Build.VERSION.RELEASE, 120),
            os_incremental = sanitizeOptional(Build.VERSION.INCREMENTAL, 160),
            sdk_int = Build.VERSION.SDK_INT,
            app_version = BuildConfig.VERSION_NAME,
            app_build = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
            build_display = sanitizeOptional(Build.DISPLAY, 160),
            build_fingerprint = sanitizeOptional(Build.FINGERPRINT, 256),
            security_patch = securityPatch
        )
    }
}
