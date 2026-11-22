package com.isladinamica.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** Pantalla negra con candado que pide rostro/huella (o PIN) antes de dejar usar WhatsApp. */
class LockActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var showing = false
    }

    private var prompt: BiometricPrompt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true

        val density = resources.displayMetrics.density
        val lock = TextView(this).apply {
            text = "🔒"
            textSize = 72f
            gravity = Gravity.CENTER
        }
        val label = TextView(this).apply {
            text = "WhatsApp bloqueado"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                addView(lock)
                addView(label)
            }
        )

        ObjectAnimator.ofPropertyValuesHolder(
            lock,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f)
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        authenticate()
    }

    override fun onResume() {
        super.onResume()
        IslandAccessibilityService.instance?.hideCover()
    }

    private fun authenticate() {
        val can = BiometricManager.from(this).canAuthenticate(Prefs.authenticators())
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                this,
                "Configura el desbloqueo facial, la huella o un PIN en el teléfono para usar el bloqueo.",
                Toast.LENGTH_LONG
            ).show()
            goHome()
            return
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear WhatsApp")
            .setSubtitle("Usa tu rostro, huella o PIN")
            .setAllowedAuthenticators(Prefs.authenticators())
            .apply {
                if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancelar")
            }
            .build()

        prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    IslandAccessibilityService.instance?.onUnlocked()
                    finish()
                    overridePendingTransition(0, 0)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    goHome()
                }
            }
        )
        prompt?.authenticate(info)
    }

    private fun goHome() {
        IslandAccessibilityService.instance?.onLockCancelled()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }
}
