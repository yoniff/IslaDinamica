package com.isladinamica.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators

object Prefs {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun get(c: Context): SharedPreferences =
        c.getSharedPreferences("isla", Context.MODE_PRIVATE)

    fun lockedApps(c: Context): Set<String> {
        val p = get(c)
        val s = mutableSetOf<String>()
        if (p.getBoolean("lock_wa", true)) s.add(WHATSAPP)
        if (p.getBoolean("lock_wab", true)) s.add(WHATSAPP_BUSINESS)
        return s
    }

    /** Tiempo (ms) que WhatsApp sigue desbloqueado después de salir de él. */
    fun graceMs(c: Context): Long = get(c).getLong("grace", 0L)

    fun alwaysVisible(c: Context): Boolean = get(c).getBoolean("always", true)

    /** Si está activo, las notificaciones se borran de la barra del sistema y solo salen en la isla. */
    fun hideSystem(c: Context): Boolean = get(c).getBoolean("hide_system", true)

    fun showCalls(c: Context): Boolean = get(c).getBoolean("show_calls", true)

    fun showMusic(c: Context): Boolean = get(c).getBoolean("show_music", true)

    /** Candado que se abre al encender y desbloquear el teléfono. */
    fun unlockAnim(c: Context): Boolean = get(c).getBoolean("unlock_anim", true)

    fun soundOn(c: Context): Boolean = get(c).getBoolean("sound_on", true)

    /** Archivo de audio elegido por la persona; null = sonido por defecto de la app. */
    fun customSound(c: Context): String? = get(c).getString("sound_uri", null)

    /** Separación desde el borde superior, en dp. */
    fun offsetY(c: Context): Int = get(c).getInt("offy", 6)

    /** Ancho de la isla en reposo, en dp. */
    fun pillWidth(c: Context): Int = get(c).getInt("pillw", 120)

    /** Rostro/huella del teléfono; en Android 11+ también PIN/patrón como respaldo. */
    fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK
        }
}
