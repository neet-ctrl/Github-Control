package com.githubcontrol.utils

import android.Manifest
import android.os.Build

/**
 * Single source of truth for runtime permissions and "special" settings the app
 * benefits from. The Permissions screen iterates this list, asks for each one,
 * and shows the current status.
 */
object PermissionsCatalog {

    enum class Kind {
        /** Requested via the standard runtime permission flow. */
        Runtime,
        /** "Special" permission — must be granted from system Settings via deep-link. */
        Special
    }

    data class Item(
        val id: String,
        val title: String,
        val rationale: String,
        val kind: Kind,
        /** Android permission ids to request. Empty for `Special` items. */
        val permissions: List<String> = emptyList(),
        /** Minimum Android SDK_INT this item is relevant for. */
        val minSdk: Int = Build.VERSION_CODES.O,
        /** Maximum Android SDK_INT this item still applies to (otherwise hidden). */
        val maxSdk: Int = Build.VERSION_CODES.CUR_DEVELOPMENT,
        val essential: Boolean = false
    ) {
        val applies: Boolean get() = Build.VERSION.SDK_INT in minSdk..maxSdk
    }

    val items: List<Item> = listOf(
        Item(
            id = "notifications",
            title = "Notifications",
            rationale = "Required to show upload, sync and alert notifications.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            minSdk = Build.VERSION_CODES.TIRAMISU,
            essential = true
        ),
        Item(
            id = "media_images",
            title = "Photos & images",
            rationale = "Pick images from your gallery for upload.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.READ_MEDIA_IMAGES),
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        Item(
            id = "media_video",
            title = "Videos",
            rationale = "Pick video files from your gallery for upload.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.READ_MEDIA_VIDEO),
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        Item(
            id = "media_audio",
            title = "Audio files",
            rationale = "Pick audio files from your library for upload.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.READ_MEDIA_AUDIO),
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        Item(
            id = "legacy_storage",
            title = "Device storage (legacy)",
            rationale = "Read files from device storage on Android 12 and below.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            maxSdk = Build.VERSION_CODES.S_V2
        ),
        Item(
            id = "biometric",
            title = "Biometric unlock",
            rationale = "Unlock the app with your fingerprint or face.",
            kind = Kind.Runtime,
            permissions = listOf(Manifest.permission.USE_BIOMETRIC)
        ),
        Item(
            id = "exact_alarm",
            title = "Exact alarms",
            rationale = "Run scheduled sync jobs precisely on time.",
            kind = Kind.Runtime,
            permissions = listOf("android.permission.SCHEDULE_EXACT_ALARM"),
            minSdk = Build.VERSION_CODES.S
        ),
        Item(
            id = "battery",
            title = "Ignore battery optimizations",
            rationale = "Keeps long uploads and background sync from being killed when the screen is off.",
            kind = Kind.Special
        ),
        Item(
            id = "all_files",
            title = "All files access",
            rationale = "Optional. Required only for syncing folders that are outside scoped storage.",
            kind = Kind.Special,
            minSdk = Build.VERSION_CODES.R
        )
    )
}
