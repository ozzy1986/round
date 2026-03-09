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
    val device_model: String,
    val os_version: String,
    val sdk_int: Int,
    val app_version: String,
    val app_build: String,
    val build_fingerprint: String?
)

data class BugReportResponse(
    val id: String,
    val created_at: String
)

object BugReportPayloadFactory {
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

        val fingerprint = Build.FINGERPRINT?.trim()?.take(256)?.takeIf { it.isNotBlank() }
        return BugReportRequest(
            message = message.trim(),
            screen = screen.trim(),
            device_manufacturer = Build.MANUFACTURER.trim(),
            device_model = Build.MODEL.trim(),
            os_version = Build.VERSION.RELEASE.trim(),
            sdk_int = Build.VERSION.SDK_INT,
            app_version = BuildConfig.VERSION_NAME,
            app_build = PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
            build_fingerprint = fingerprint
        )
    }
}
