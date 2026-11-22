package com.isladinamica.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Barritas animadas del ecualizador que se ven a la derecha de la isla mientras suena música. */
class EqualizerView(ctx: Context) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34C759.toInt() }
    private val rect = RectF()
    private var phase = 0f
    private var playing = false

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setPlaying(p: Boolean) {
        playing = p
        if (p) {
            if (!anim.isStarted) anim.start()
        } else {
            anim.cancel()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val n = 4
        val bw = width / (n * 2f - 1f)
        for (i in 0 until n) {
            val f = if (playing) {
                0.25f + 0.75f * abs(sin(2.0 * PI * (phase + i * 0.22))).toFloat()
            } else {
                0.25f
            }
            val h = height * f
            val top = (height - h) / 2f
            val left = i * 2 * bw
            rect.set(left, top, left + bw, top + h)
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}
