package com.wormx.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The glowing ring + percentage readout used for the "orb" hero button.
 * One tap: [setProgress] is driven from 0-100 during link resolution, then
 * again from 0-100 as the actual download progresses — same two-phase
 * behaviour as the HTML prototype's SVG ring, just as a real Android View.
 */
class OrbProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f // 0..100
    private var tintColor = Color.parseColor("#6C8CFF")

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#1AFFFFFF")
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private var pulseAnimator: ValueAnimator? = null
    private var idlePulse = 0f

    init {
        ringPaint.color = tintColor
    }

    fun setTintColor(color: Int) {
        tintColor = color
        ringPaint.color = color
        invalidate()
    }

    /** Animates the ring from its current value up to [target] (0-100) over [durationMs]. */
    fun animateProgress(target: Float, durationMs: Long = 250) {
        ValueAnimator.ofFloat(progress, target).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 100f)
        invalidate()
    }

    /** Slow ambient pulse used while idle (no active fetch/download). */
    fun startIdlePulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.55f, 0.85f).apply {
            duration = 1600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                idlePulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopIdlePulse() {
        pulseAnimator?.cancel()
        idlePulse = 0.7f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - trackPaint.strokeWidth

        // Radial glow behind everything, alpha breathes gently while idle.
        val alpha = (idlePulse.takeIf { it > 0f } ?: 0.7f)
        glowPaint.shader = RadialGradient(
            cx, cy, radius * 1.15f,
            intArrayOf(
                colorWithAlpha(tintColor, (140 * alpha).toInt()),
                colorWithAlpha(tintColor, (40 * alpha).toInt()),
                colorWithAlpha(tintColor, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.15f, glowPaint)

        // Background track
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Foreground progress arc, starting at 12 o'clock
        val sweep = 360f * (progress / 100f)
        val rect = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, -90f, sweep, false, ringPaint)
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
