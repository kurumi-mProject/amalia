package com.my.amali.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Единый каталог всех разрешений Амалии: константы, человекочитаемые
 * группы и проверка статуса. Используется PermissionsScreen, онбордингом
 * и системными контроллерами.
 */
enum class AmaliaPermission(
    val manifestPermission: String?,
    val minSdk: Int = 24,
) {
    MICROPHONE(Manifest.permission.RECORD_AUDIO),
    NOTIFICATIONS(
        if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.POST_NOTIFICATIONS" else null,
        minSdk = 33,
    ),
    LOCATION_FINE(Manifest.permission.ACCESS_FINE_LOCATION),
    LOCATION_COARSE(Manifest.permission.ACCESS_COARSE_LOCATION),
    CONTACTS(Manifest.permission.READ_CONTACTS),
    BLUETOOTH(
        if (android.os.Build.VERSION.SDK_INT >= 31) "android.permission.BLUETOOTH_CONNECT" else null,
        minSdk = 31,
    ),
    CAMERA(Manifest.permission.CAMERA),
    PHONE(Manifest.permission.CALL_PHONE),
    MEDIA_AUDIO(
        if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else null,
        minSdk = 33,
    ),
    MEDIA_IMAGES(
        if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES" else null,
        minSdk = 33,
    ),
    MEDIA_VIDEO(
        if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_VIDEO" else null,
        minSdk = 33,
    );

    /** Проверяет, выдано ли разрешение прямо сейчас. */
    fun isGranted(context: Context): Boolean {
        val permission = manifestPermission ?: return true // не требуется на этой версии
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Все разрешения, запрашиваемые на текущей версии Android. */
        fun requiredForCurrentDevice(): List<AmaliaPermission> =
            entries.filter { it.manifestPermission != null }
    }
}
