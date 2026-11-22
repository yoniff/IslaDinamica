package com.isladinamica.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            Prefs.get(this).edit().putString("sound_uri", uri.toString()).apply()
            NotifSound.play(this)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        fun text(t: String, sp: Float, bold: Boolean = false): TextView = TextView(this).apply {
            text = t
            textSize = sp
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(4))
        }

        fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

        fun toggle(label: String, key: String, def: Boolean) = SwitchCompat(this).apply {
            text = label
            isChecked = prefs.getBoolean(key, def)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }

        fun slider(label: String, key: String, def: Int, min: Int, max: Int, unit: String): View {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val tv = text("$label: ${prefs.getInt(key, def)} $unit", 14f)
            val sb = SeekBar(this).apply {
                this.min = min
                this.max = max
                progress = prefs.getInt(key, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                        tv.text = "$label: $p $unit"
                        if (fromUser) prefs.edit().putInt(key, p).apply()
                    }

                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            }
            box.addView(tv)
            box.addView(sb)
            return box
        }

        col.addView(text("Isla Dinámica", 28f, bold = true))
        status = text("", 15f)
        col.addView(status)

        col.addView(text("Paso 1 y 2: permisos", 16f, bold = true))
        col.addView(button("1. Activar servicio de accesibilidad") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("2. Dar acceso a las notificaciones") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(
            text(
                "Si Android deja el interruptor en gris (Android 13 o superior): Ajustes → Apps → " +
                    "Isla Dinámica → ⋮ → Permitir configuración restringida, y vuelve a intentarlo.",
                12f
            ).apply { alpha = 0.7f }
        )

        col.addView(text("Bloqueo de aplicaciones", 16f, bold = true))
        col.addView(toggle("Bloquear WhatsApp", "lock_wa", true))
        col.addView(toggle("Bloquear WhatsApp Business", "lock_wab", true))

        col.addView(text("Volver a pedir el desbloqueo", 14f))
        val grace = RadioGroup(this)
        val current = prefs.getLong("grace", 0L)
        listOf("Siempre que abra la app" to 0L, "Después de 15 segundos" to 15_000L, "Después de 1 minuto" to 60_000L)
            .forEach { (label, ms) ->
                val rb = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = label
                    tag = ms
                }
                grace.addView(rb)
                if (ms == current) rb.isChecked = true
            }
        grace.setOnCheckedChangeListener { g, id ->
            val ms = g.findViewById<View>(id).tag as Long
            prefs.edit().putLong("grace", ms).apply()
        }
        col.addView(grace)

        col.addView(text("Isla dinámica", 16f, bold = true))
        col.addView(toggle("Mostrar la isla siempre (como en iPhone)", "always", true))
        col.addView(toggle("Ocultar las notificaciones del sistema (solo se ven en la isla)", "hide_system", true))
        col.addView(toggle("Llamadas en la isla (contestar / rechazar)", "show_calls", true))
        col.addView(toggle("Música en la isla (anterior / pausa / siguiente)", "show_music", true))
        col.addView(toggle("Candado al encender y desbloquear el teléfono", "unlock_anim", true))

        col.addView(text("Sonido y funcionamiento en segundo plano", 16f, bold = true))
        col.addView(toggle("Sonido al llegar un mensaje", "sound_on", true))
        col.addView(button("Elegir mi propio sonido (archivo de audio)") {
            pickSound.launch(arrayOf("audio/*"))
        })
        col.addView(button("Volver al sonido por defecto") {
            prefs.edit().remove("sound_uri").apply()
            NotifSound.play(this)
        })
        col.addView(button("Probar sonido") { NotifSound.play(this) })
        col.addView(button("Evitar que el sistema cierre la isla (batería)") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Ya está permitido", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        })
        col.addView(slider("Posición vertical", "offy", 6, 0, 40, "dp"))
        col.addView(slider("Ancho en reposo", "pillw", 120, 80, 200, "dp"))
        col.addView(button("Probar la isla") {
            val island = IslandAccessibilityService.instance?.island
            if (island == null) {
                Toast.makeText(this, "Primero activa el servicio de accesibilidad", Toast.LENGTH_SHORT).show()
            } else {
                island.present(null, "Isla Dinámica", "Así se ve una notificación", null, 4000L, true)
            }
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val acc = isAccessibilityOn()
        val notif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val bio = BiometricManager.from(this).canAuthenticate(Prefs.authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
        status.text = "Accesibilidad: ${mark(acc)}\n" +
            "Notificaciones: ${mark(notif)}\n" +
            "Desbloqueo del teléfono (rostro/huella/PIN): ${mark(bio)}"
    }

    private fun mark(ok: Boolean) = if (ok) "✅ listo" else "❌ pendiente"

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }
}
