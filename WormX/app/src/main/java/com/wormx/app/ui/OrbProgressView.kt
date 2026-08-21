package com.wormx.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The orb hero.
 *
 * Idle state matches the very first HTML prototype exactly: a single-color
 * (blue) conic gradient — blue -> transparent -> blue -> transparent -> blue
 * around the circle, soft-blurred, slowly spinning — with a dark inset
 * circle in the middle so it reads as a glowing ring rather than a filled
 * disc. That original look used CSS `conic-gradient`, which Android's
 * SweepGradient shader replicates directly (smooth, continuous fade between
 * stops — not hard-edged arcs).
 *
 * Active state (fetching/downloading) switches to a crisp, thin percentage
 * ring — deliberately different from the idle swirl.
 */
class OrbProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f // 0..100
    private var showingProgress = false
    private var rotationDegrees = 0f

    private val blue = Color.parseColor("#6C8CFF")
    private val bgColor = Color.parseColor("#0A0A0D")

    init {
        // BlurMaskFilter (the idle swirl's soft edge) requires a software
        // layer — without this the blur is silently ignored.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
        color = blue
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#1AFFFFFF")
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
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

    /** Switches into "fetching/downloading" mode: swirl stops, crisp percentage ring shows. */
    fun stopIdlePulse() {
        showingProgress = true
        rotationAnimator?.pause()
        invalidate()
    }

    /** Switches back to the idle spinning swirl — matches the original prototype's 5s rotation. */
    fun startIdlePulse() {
        showingProgress = false
        progress = 0f
        if (rotationAnimator == null) {
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 5000
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
        val outerRadius = minOf(width, height) / 2f

        if (showingProgress) {
            drawProgressRing(canvas, cx, cy, outerRadius)
        } else {
            drawIdleSwirl(canvas, cx, cy, outerRadius)
        }
    }

    private fun drawIdleSwirl(canvas: Canvas, cx: Float, cy: Float, outerRadius: Float) {
        // Same 4-stop conic gradient as the original CSS:
        // blue(0°) -> transparent(90°) -> blue(180°) -> transparent(270°) -> blue(360°)
        val opaque = blue
        val transparent = colorWithAlpha(blue, 0)
        swirlPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(opaque, transparent, opaque, transparent, opaque),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        )

        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        canvas.drawCircle(cx, cy, outerRadius * 0.98f, swirlPaint)
        canvas.restore()

        // Dark inset core (CSS `::after`) so it reads as a ring, not a disc,
        // plus a soft inner glow to approximate the original's inset shadow.
        val coreRadius = outerRadius * 0.71f
        innerGlowPaint.strokeWidth = outerRadius * 0.3f
        canvas.drawCircle(cx, cy, coreRadius, innerGlowPaint)
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }

    private fun drawProgressRing(canvas: Canvas, cx: Float, cy: Float, outerRadius: Float) {
        val radius = outerRadius - trackPaint.strokeWidth
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        glowPaint.shader = RadialGradient(
            cx, cy, radius * 1.15f,
            intArrayOf(colorWithAlpha(blue, 90), colorWithAlpha(blue, 24), colorWithAlpha(blue, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.15f, glowPaint)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        val sweep = 360f * (progress / 100f)
        canvas.drawArc(rect, -90f, sweep, false, progressPaint)
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
