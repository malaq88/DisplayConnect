package com.example.displayconnect.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Utilitários para intents externos.
 */
object IntentUtils {

    /**
     * Abre o Google Maps para navegação. O usuário escolhe o destino normalmente.
     */
    fun openGoogleMaps(context: Context): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            val fallback = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallback)
            true
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
