package com.isladinamica.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Resorte amortiguado: la isla se pasa un poco del tamaño final (~4 %) y se asienta,
 * como la animación de la Dynamic Island de iPhone.
 */
private class SpringInterpolator : TimeInterpolator {
    override fun getInterpolation(t: Float): Float {
        if (t >= 1f) return 1f
        return (1.0 - exp(-7.0 * t) * cos(2.0 * PI * 1.1 * t)).toFloat()
    }
}

/** Botón redondo que se dibuja a la derecha de la isla expandida (contestar, pausa, etc.). */
data class IslandAction(val label: String, val color: Int, val onClick: () -> Unit)

/** Estado de la música que suena, para mostrar carátula + ecualizador en reposo. */
class MusicState(val art: Bitmap?, val title: String, val artist: String, val playing: Boolean)

/**
 * Píldora negra tipo "Dynamic Island" dibujada como overlay de accesibilidad.
 * En reposo es una cápsula pequeña (más ancha, con carátula y ecualizador, si suena música);
 * al recibir un evento se expande con animación.
 */
class IslandView(private val ctx: Context, private val wm: WindowManager) {

    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()

    private val handler = Handler(Looper.getMainLooper())
    private val root = FrameLayout(ctx)
    private val pill = FrameLayout(ctx)
    private val content = LinearLayout(ctx)
    private val icon = ImageView(ctx)
    private val texts = LinearLayout(ctx)
    private val title = TextView(ctx)
    private val body = TextView(ctx)
    private val actionsRow = LinearLayout(ctx)
    private val miniRow = FrameLayout(ctx)
    private val miniArt = ImageView(ctx)
    private val eq = EqualizerView(ctx)

    /** Se ejecuta al tocar la isla compacta (por ejemplo, para abrir los controles de música). */
    var compactTap: (() -> Unit)? = null

    private var onTap: (() -> Unit)? = null
    private var actionViews: List<TextView> = emptyList()
    private var ownerTag = ""
    private var stickyActive = false
    private var musicOn = false
    private var expanded = false
    private var attached = false
    private var animator: ValueAnimator? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun collapsedW() = dp(Prefs.pillWidth(ctx)) + if (musicOn) dp(70) else 0
    private fun collapsedH() = dp(34)

    private fun circleOutline() = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    init {
        pill.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(44).toFloat()
        }

        // Contenido compacto: carátula a la izquierda y ecualizador a la derecha.
        miniArt.scaleType = ImageView.ScaleType.CENTER_CROP
        miniArt.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF3A3A3C.toInt())
        }
        miniArt.clipToOutline = true
        miniArt.outlineProvider = circleOutline()
        miniRow.addView(
            miniArt,
            FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftMargin = dp(12)
            }
        )
        miniRow.addView(
            eq,
            FrameLayout.LayoutParams(dp(20), dp(16)).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = dp(14)
            }
        )
        miniRow.alpha = 0f
        pill.addView(
            miniRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Contenido expandido.
        content.orientation = LinearLayout.HORIZONTAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPadding(dp(16), 0, dp(16), 0)
        content.alpha = 0f

        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = true
        icon.outlineProvider = circleOutline()
        content.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))

        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END

        body.setTextColor(0xFFB0B0B5.toInt())
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        body.maxLines = 2
        body.ellipsize = TextUtils.TruncateAt.END

        texts.orientation = LinearLayout.VERTICAL
        texts.addView(title)
        texts.addView(body)
        content.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
            }
        )

        actionsRow.orientation = LinearLayout.HORIZONTAL
        actionsRow.gravity = Gravity.CENTER_VERTICAL
        actionsRow.visibility = View.GONE
        content.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        pill.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        pill.setOnClickListener {
            if (expanded) {
                val tap = onTap
                if (tap != null) {
                    tap()
                    collapse()
                }
            } else {
                compactTap?.invoke()
            }
        }
        root.addView(
            pill,
            FrameLayout.LayoutParams(collapsedW(), collapsedH()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
    }

    fun attach() {
        if (attached) return
        applyConfig()
        wm.addView(root, params)
        attached = true
        applyIdleVisibility()
    }

    fun detach() {
        if (!attached) return
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        attached = false
    }

    /** Relee las preferencias (posición, ancho, visibilidad). */
    fun applyConfig() {
        params.y = dp(Prefs.offsetY(ctx))
        if (!expanded) {
            val lp = pill.layoutParams
            lp.width = collapsedW()
            lp.height = collapsedH()
            pill.layoutParams = lp
        }
        if (attached) {
            wm.updateViewLayout(root, params)
            applyIdleVisibility()
        }
    }

    /**
     * Expande la isla.
     *  - [tap]: se ejecuta si la persona toca la isla expandida (y luego se cierra).
     *  - [actions]: botones redondos a la derecha (contestar, pausa...).
     *  - [sticky]: mientras esté activa, otras presentaciones no la reemplazan (llamadas).
     *  - [iconOnly]: solo un icono centrado en una cápsula chica (candado).
     *  - [owner]: etiqueta para poder cambiar el texto de un botón después ([setActionLabel]).
     */
    fun present(
        image: Drawable?,
        titleText: String,
        text: String?,
        tap: (() -> Unit)?,
        holdMs: Long = 5000L,
        wide: Boolean = true,
        actions: List<IslandAction> = emptyList(),
        sticky: Boolean = false,
        iconOnly: Boolean = false,
        owner: String = ""
    ) {
        if (stickyActive && !sticky) return
        handler.removeCallbacksAndMessages(null)
        stickyActive = sticky
        ownerTag = owner

        icon.setImageDrawable(image)
        icon.visibility = if (image == null) View.GONE else View.VISIBLE
        val iconSize = if (iconOnly) dp(26) else dp(38)
        icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        icon.clipToOutline = !iconOnly
        texts.visibility = if (iconOnly) View.GONE else View.VISIBLE
        content.gravity = if (iconOnly) Gravity.CENTER else Gravity.CENTER_VERTICAL
        title.text = titleText
        body.text = text ?: ""
        body.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        buildActions(actions)

        onTap = tap
        expanded = true
        root.visibility = View.VISIBLE
        setTouchable(tap != null || actions.isNotEmpty())

        miniRow.animate().cancel()
        miniRow.animate().alpha(0f).setDuration(100).start()

        val maxW = ctx.resources.displayMetrics.widthPixels - dp(24)
        val w = when {
            iconOnly -> dp(84)
            wide -> minOf(dp(370), maxW)
            else -> dp(200)
        }
        val h = when {
            iconOnly -> dp(44)
            text.isNullOrEmpty() && actions.isEmpty() -> dp(58)
            else -> dp(86)
        }
        animateTo(w, h)

        // El contenido entra desde un poco más chico y desenfocado, como en iOS.
        content.animate().cancel()
        content.scaleX = 0.9f
        content.scaleY = 0.9f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(120).setDuration(320).start()
        blurIn()
        handler.postDelayed({ collapse() }, holdMs)
    }

    fun collapse() {
        handler.removeCallbacksAndMessages(null)
        stickyActive = false
        ownerTag = ""
        content.animate().cancel()
        content.animate().alpha(0f).setStartDelay(0).setDuration(120).start()
        expanded = false
        onTap = null
        setTouchable(musicOn)
        animateTo(collapsedW(), collapsedH())
        handler.postDelayed({
            updateMini()
            applyIdleVisibility()
        }, 400)
    }

    /** Alarga el tiempo que la isla sigue abierta (por ejemplo, al tocar un botón de música). */
    fun extendHold(ms: Long) {
        if (!expanded) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ collapse() }, ms)
    }

    /** Cambia el texto de un botón de la isla abierta, si pertenece a [owner]. */
    fun setActionLabel(owner: String, index: Int, label: String) {
        if (ownerTag == owner && index in actionViews.indices) {
            actionViews[index].text = label
        }
    }

    /** Animación de "pop" del icono, usada cuando el candado se abre. */
    fun popIcon() {
        icon.animate().cancel()
        icon.scaleX = 0.6f
        icon.scaleY = 0.6f
        icon.animate().scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(2.5f)).start()
    }

    /** Música sonando: la isla en reposo se ensancha y muestra carátula + ecualizador. */
    fun setMusic(state: MusicState?) {
        val on = state != null && state.playing
        musicOn = on
        miniArt.setImageBitmap(state?.art)
        eq.setPlaying(on)
        if (!expanded) {
            setTouchable(on)
            animateTo(collapsedW(), collapsedH())
            updateMini()
            applyIdleVisibility()
        }
    }

    private fun buildActions(actions: List<IslandAction>) {
        actionsRow.removeAllViews()
        val size = if (actions.size > 2) dp(36) else dp(42)
        actionViews = actions.map { a ->
            val b = TextView(ctx)
            b.text = a.label
            b.setTextColor(Color.WHITE)
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            b.gravity = Gravity.CENTER
            b.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(a.color)
            }
            b.setOnClickListener { a.onClick() }
            actionsRow.addView(
                b,
                LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            )
            b
        }
        actionsRow.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateMini() {
        val show = musicOn && !expanded
        miniRow.animate().cancel()
        miniRow.animate().alpha(if (show) 1f else 0f).setDuration(200).start()
    }

    private fun applyIdleVisibility() {
        if (!expanded) {
            root.visibility =
                if (Prefs.alwaysVisible(ctx) || musicOn) View.VISIBLE else View.GONE
        }
    }

    /** En reposo la isla deja pasar los toques (por ejemplo, para bajar las notificaciones). */
    private fun setTouchable(touchable: Boolean) {
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (attached) wm.updateViewLayout(root, params)
    }

    /** Desenfoque que se aclara al aparecer el contenido (Android 12 o superior). */
    private fun blurIn() {
        if (Build.VERSION.SDK_INT < 31) return
        ValueAnimator.ofFloat(22f, 0.01f).apply {
            duration = 420
            addUpdateListener {
                val r = it.animatedValue as Float
                content.setRenderEffect(
                    RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    content.setRenderEffect(null)
                }
            })
            start()
        }
    }

    private fun animateTo(w: Int, h: Int) {
        val lp = pill.layoutParams
        if (lp.width == w && lp.height == h && animator?.isRunning != true) return
        animator?.cancel()
        val w0 = lp.width
        val h0 = lp.height
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = SpringInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                lp.width = (w0 + (w - w0) * t).roundToInt().coerceAtLeast(dp(40))
                lp.height = (h0 + (h - h0) * t).roundToInt().coerceAtLeast(dp(20))
                pill.layoutParams = lp
            }
            start()
        }
    }
}
