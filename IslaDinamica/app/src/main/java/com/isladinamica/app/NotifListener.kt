package com.isladinamica.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Color
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Envía a la isla dinámica:
 *  - notificaciones nuevas (y las borra de la barra del sistema si así se configuró),
 *  - llamadas entrantes con botones de contestar / rechazar,
 *  - la música que suena (vía [MusicWatcher]).
 */
class NotifListener : NotificationListenerService() {

    private var music: MusicWatcher? = null
    private var callKey: String? = null

    override fun onListenerConnected() {
        music = MusicWatcher(this, ComponentName(this, NotifListener::class.java)).also { it.start() }
    }

    override fun onListenerDisconnected() {
        music?.stop()
        music = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        // Sin la isla activa no se toca nada, para no perder la notificación.
        val island = IslandAccessibilityService.instance?.island ?: return

        if (n.category == Notification.CATEGORY_CALL) {
            showCall(sbn, island)
            return
        }

        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.isOngoing) return
        if (n.category == Notification.CATEGORY_TRANSPORT ||
            n.category == Notification.CATEGORY_PROGRESS ||
            n.category == Notification.CATEGORY_SERVICE ||
            n.category == Notification.CATEGORY_ALARM ||
            n.category == Notification.CATEGORY_NAVIGATION
        ) return

        val extras = n.extras
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Si la app está bloqueada, la isla no muestra quién escribe ni qué dice.
        val locked = sbn.packageName in Prefs.lockedApps(this)
        val image = if (locked) {
            title = "WhatsApp"
            text = "Mensaje nuevo (contenido oculto)"
            n.smallIcon?.loadDrawable(this)?.apply { setTint(Color.WHITE) }
        } else {
            n.getLargeIcon()?.loadDrawable(this)
                ?: n.smallIcon?.loadDrawable(this)?.apply { setTint(Color.WHITE) }
        }

        val tap: () -> Unit = {
            try {
                n.contentIntent?.send()
            } catch (_: Exception) {
            }
        }
        island.present(image, title, text, tap)

        // Sonido de la isla, solo si la notificación original habría sonado.
        if (isAudible(sbn)) NotifSound.onMessage(this, inDoNotDisturb())

        // Quita la notificación de la barra del sistema: solo se ve en la isla.
        if (Prefs.hideSystem(this)) {
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Llamada entrante: usa los botones que la propia notificación de la llamada trae
     * (Contestar / Rechazar). La notificación del sistema no se borra.
     */
    private fun showCall(sbn: StatusBarNotification, island: IslandView) {
        if (!Prefs.showCalls(this)) return
        if (sbn.key == callKey) return
        val n = sbn.notification
        val extras = n.extras

        // 1 = entrante, 2 = en curso, 3 = filtrando. En versiones sin este dato vale 0.
        val callType = extras.getInt("android.callType", 0)
        if (callType == 2) return
        val actions = n.actions ?: return
        if (callType == 0 && actions.size < 2) return

        fun find(words: List<String>) = actions.firstOrNull { a ->
            val t = a.title?.toString() ?: ""
            words.any { t.contains(it, ignoreCase = true) }
        }

        val answer = find(listOf("contest", "acept", "atend", "answer", "accept")) ?: actions.last()
        val decline = find(listOf("rechaz", "decline", "reject", "colg", "hang", "ignor")) ?: actions.first()
        if (answer === decline) return

        callKey = sbn.key
        val name = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Llamada entrante"
        val image = n.getLargeIcon()?.loadDrawable(this)
            ?: n.smallIcon?.loadDrawable(this)?.apply { setTint(Color.WHITE) }

        island.present(
            image,
            name,
            "Llamada entrante",
            null,
            45_000L,
            true,
            listOf(
                IslandAction("✕", 0xFFFF3B30.toInt()) {
                    fire(decline)
                    island.collapse()
                },
                IslandAction("✓", 0xFF34C759.toInt()) {
                    fire(answer)
                    island.collapse()
                }
            ),
            true,
            false,
            "call"
        )
    }

    private fun fire(a: Notification.Action) {
        try {
            a.actionIntent?.send()
        } catch (_: Exception) {
        }
    }

    private fun isAudible(sbn: StatusBarNotification): Boolean = try {
        val rank = Ranking()
        currentRanking.getRanking(sbn.key, rank) &&
            rank.importance >= NotificationManager.IMPORTANCE_DEFAULT
    } catch (_: Exception) {
        true
    }

    private fun inDoNotDisturb(): Boolean = try {
        currentInterruptionFilter != INTERRUPTION_FILTER_ALL
    } catch (_: Exception) {
        false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == callKey) {
            callKey = null
            IslandAccessibilityService.instance?.island?.collapse()
        }
    }
}
