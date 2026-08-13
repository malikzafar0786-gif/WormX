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
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The orb hero.
 *
 * Idle state matches the very first HTML prototype: a thick, soft-edged
 * conic swirl — alternating blue/gold bands with gaps, blurred, slowly
 * spinning, like a small wormhole. This needs a software rendering layer
 * (set in init) because BlurMaskFilter isn't supported on the hardware-
 * accelerated layer Views normally use.
 *
 * Active state (fetching/downloading) switches to a crisp, thin percentage
 * ring — deliberately different from the idle swirl, matching the clean
 * progress-ring look of the reference app once real numbers are involved.
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

    init {
        // BlurMaskFilter (used for the idle swirl's soft edge) requires a
        // software layer — without this the blur is silently ignored.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val swirlBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = blue
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }
    private val swirlGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = gold
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }

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

    /** Switches back to the idle spinning swirl. */
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
        val ringRadius = outerRadius * 0.62f
        val strokeW = outerRadius * 0.62f // thick band, like the prototype's conic ring
        swirlBluePaint.strokeWidth = strokeW
        swirlGoldPaint.strokeWidth = strokeW

        // Ambient glow behind the swirl
        glowPaint.shader = RadialGradient(
            cx, cy, outerRadius * 1.05f,
            intArrayOf(colorWithAlpha(blue, 70), colorWithAlpha(blue, 18), colorWithAlpha(blue, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRadius * 1.05f, glowPaint)

        val rect = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        // Two soft bands with gaps between them, like the original
        // conic-gradient(blue, transparent, gold, transparent) pinwheel.
        canvas.drawArc(rect, 0f, 130f, false, swirlBluePaint)
        canvas.drawArc(rect, 180f, 130f, false, swirlGoldPaint)
        canvas.restore()

        // Dark core so the center reads as a hole, not a filled disc —
        // matches the prototype's `::after` inset dark circle.
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0D") }
        canvas.drawCircle(cx, cy, ringRadius - strokeW * 0.42f, corePaint)
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
