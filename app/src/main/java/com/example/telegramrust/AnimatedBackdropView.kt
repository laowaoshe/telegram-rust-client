package com.example.telegramrust

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
 * Лёгкий фон без внешних библиотек.
 *
 * На Android 6–10 системного blur-эффекта нет, поэтому стекло сделано честно:
 * полупрозрачные панели + медленные градиентные пятна под ними. Это сохраняет
 * совместимость с minSdk 23 и всё равно создаёт ощущение глубины и движения.
 */
class AnimatedBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var progress = 0f
    private var themeMode = "midnight"
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setThemeMode(mode: String) {
        themeMode = mode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val light = themeMode == "light"
        val amoled = themeMode == "amoled"
        canvas.drawColor(
            when {
                light -> Color.rgb(236, 240, 248)
                amoled -> Color.BLACK
                else -> Color.rgb(5, 5, 10)
            }
        )

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val phase = progress * (Math.PI * 2.0).toFloat()
        val radius = width.coerceAtLeast(height) * 0.72f

        drawGlow(
            canvas,
            width * (0.16f + 0.05f * kotlin.math.sin(phase).toFloat()),
            height * (0.18f + 0.08f * kotlin.math.cos(phase * 0.8f).toFloat()),
            radius,
            if (light) Color.argb(66, 80, 96, 220) else Color.argb(105, 75, 55, 205)
        )
        drawGlow(
            canvas,
            width * (0.86f + 0.07f * kotlin.math.cos(phase * 0.7f).toFloat()),
            height * (0.42f + 0.10f * kotlin.math.sin(phase * 0.9f).toFloat()),
            radius * 0.82f,
            if (light) Color.argb(48, 35, 128, 190) else Color.argb(78, 25, 110, 185)
        )
        drawGlow(
            canvas,
            width * (0.48f + 0.10f * kotlin.math.sin(phase * 0.55f).toFloat()),
            height * (0.92f + 0.06f * kotlin.math.cos(phase * 0.6f).toFloat()),
            radius * 0.68f,
            if (light) Color.argb(40, 150, 70, 180) else Color.argb(62, 140, 55, 190)
        )
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(color, Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }
}