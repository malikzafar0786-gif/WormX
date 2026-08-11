package com.wormx.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The orb hero. Idle state matches the very first HTML prototype: two
 * blurred, differently-colored arcs (blue + gold — WormX's "download" and
 * "vault" tones) slowly spinning like a vortex/wormhole. Once a fetch or
 * download starts, the spin freezes and the same ring becomes a literal
 * percentage progress arc; it resumes spinning when idle again.
 */
class OrbProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f // 0..100
    private var showingProgress = false
    private var rotationDegrees = 0f

    private val blue = Color.parseColor("#6C8CFF")
    private val gold = Color.parseColor("#E3B24C")

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = Color.parseColor("#1AFFFFFF")
        strokeCap = Paint.Cap.ROUND
    }
    private val blueArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#6C8CFF")
    }
    private val goldArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E3B24C")
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#6C8CFF")
    }

    private var rotationAnimator: ValueAnimator? = null

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

    /** Switches into "fetching/downloading" mode: spin stops, percentage arc shows. */
    fun stopIdlePulse() {
        showingProgress = true
        rotationAnimator?.pause()
        invalidate()
    }

    /** Switches back to the idle spinning vortex. */
    fun startIdlePulse() {
        showingProgress = false
        progress = 0f
        if (rotationAnimator == null) {
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3200
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotationDegrees = it.animatedValue as Float
                    invalidate()
                }
            }
        }
        if (rotationAnimator?.isPaused == true) rotationAnimator?.resume() else rotationAnimator?.start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - trackPaint.strokeWidth
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Ambient glow behind everything
        glowPaint.shader = RadialGradient(
            cx, cy, radius * 1.15f,
            intArrayOf(colorWithAlpha(blue, 90), colorWithAlpha(blue, 24), colorWithAlpha(blue, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.15f, glowPaint)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        if (showingProgress) {
            val sweep = 360f * (progress / 100f)
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)
        } else {
            // Two-arc vortex, matching the original prototype's spinning swirl.
            canvas.save()
            canvas.rotate(rotationDegrees, cx, cy)
            canvas.drawArc(rect, 0f, 150f, false, blueArcPaint)
            canvas.drawArc(rect, 180f, 150f, false, goldArcPaint)
            canvas.restore()
        }
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
