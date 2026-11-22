package com.isladinamica.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Hace tres cosas:
 *  1. Dibuja la isla dinámica (overlay de accesibilidad, no necesita el permiso de "superponer").
 *  2. Detecta cuándo pasa al frente WhatsApp y abre la pantalla de bloqueo.
 *  3. Anima un candado en la isla al encender y al desbloquear el teléfono.
 */
class IslandAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: IslandAccessibilityService? = null
    }

    var island: IslandView? = null
        private set

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null

    private var unlocked = false
    private var leftAt = 0L
    private var lastLaunch = 0L

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> island?.applyConfig() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> unlocked = false
                Intent.ACTION_SCREEN_ON -> showLockIcon()
                Intent.ACTION_USER_PRESENT -> showUnlockAnimation()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        island = IslandView(this, wm).also { it.attach() }
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ---- Candado al encender / desbloquear el teléfono ----------------------------------------

    /** Pantalla encendida y todavía bloqueada: candado cerrado en la isla. */
    private fun showLockIcon() {
        if (!Prefs.unlockAnim(this)) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) return
        island?.present(
            ContextCompat.getDrawable(this, R.drawable.ic_lock_closed),
            "",
            null,
            null,
            8000L,
            false,
            iconOnly = true
        )
    }

    /** Teléfono desbloqueado (rostro, huella o PIN): el candado se abre con un "pop". */
    private fun showUnlockAnimation() {
        if (!Prefs.unlockAnim(this)) return
        island?.present(
            ContextCompat.getDrawable(this, R.drawable.ic_lock_open),
            "",
            null,
            null,
            1100L,
            false,
            iconOnly = true
        )
        island?.popIcon()
    }

    // ---- Bloqueo de WhatsApp -----------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isIgnored(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (pkg in Prefs.lockedApps(this)) {
            if (unlocked) {
                if (leftAt != 0L && now - leftAt > Prefs.graceMs(this)) {
                    unlocked = false
                } else {
                    leftAt = 0L
                    return
                }
            }
            if (LockActivity.showing || now - lastLaunch < 1500) return
            lastLaunch = now
            showCover()
            startActivity(
                Intent(this, LockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        } else if (unlocked && leftAt == 0L) {
            leftAt = now
        }
    }

    private fun isIgnored(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui") return true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    /** Pantalla negra inmediata para que no se vea WhatsApp mientras abre el bloqueo. */
    private fun showCover() {
        if (cover != null) return
        val v = View(this).apply { setBackgroundColor(Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        cover = v
        // Si la pantalla de bloqueo no llegó a abrirse, se cierra WhatsApp por seguridad.
        handler.postDelayed({
            if (!LockActivity.showing) performGlobalAction(GLOBAL_ACTION_HOME)
            hideCover()
        }, 3000)
    }

    fun hideCover() {
        handler.removeCallbacksAndMessages(null)
        cover?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        cover = null
    }

    fun onUnlocked() {
        unlocked = true
        leftAt = 0L
        hideCover()
        island?.present(
            ContextCompat.getDrawable(this, R.drawable.ic_check),
            "Desbloqueado",
            null,
            null,
            1400L,
            false
        )
    }

    fun onLockCancelled() {
        unlocked = false
        hideCover()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        hideCover()
        island?.detach()
        island = null
        instance = null
        super.onDestroy()
    }
}
