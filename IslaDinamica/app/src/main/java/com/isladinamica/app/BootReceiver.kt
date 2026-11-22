package com.isladinamica.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Al reiniciar el teléfono (o actualizar la app) vuelve a conectar la isla.
 * Android ya reinicia solo los servicios activados; esto cubre los casos en que no lo hace.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotifListener::class.java)
            )
        } catch (_: Exception) {
        }

        // Solo funciona si se concedió WRITE_SECURE_SETTINGS por ADB (ver README):
        // reactiva el servicio de accesibilidad si el teléfono lo dejó apagado.
        try {
            val me = ComponentName(context, IslandAccessibilityService::class.java).flattenToString()
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            if (!current.split(':').any { it.equals(me, ignoreCase = true) }) {
                val list = if (current.isEmpty()) me else "$current:$me"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        } catch (_: SecurityException) {
        }
    }
}
