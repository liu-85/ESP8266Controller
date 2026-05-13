package com.example.esp8266controller.joystick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var joystickRadius: Float = 0f
    private var innerRadius: Float = 0f

    private var innerCircleX: Float = 0f
    private var innerCircleY: Float = 0f

    private val outerPaint: Paint = Paint()
    private val innerPaint: Paint = Paint()
    private val borderPaint: Paint = Paint()

    private var onJoystickMoveListener: ((angle: Double, strength: Float) -> Unit)? = null

    var angle: Double = 0.0
    var strength: Float = 0.0f

    init {
        outerPaint.color = Color.parseColor("#E0E0E0")
        outerPaint.style = Paint.Style.FILL
        outerPaint.isAntiAlias = true

        innerPaint.color = Color.parseColor("#2196F3")
        innerPaint.style = Paint.Style.FILL
        innerPaint.isAntiAlias = true

        borderPaint.color = Color.parseColor("#FFFFFF")
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 4f
        borderPaint.isAntiAlias = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        joystickRadius = min(w, h) / 2f * 0.8f
        innerRadius = joystickRadius * 0.3f

        innerCircleX = centerX
        innerCircleY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw outer circle
        canvas.drawCircle(centerX, centerY, joystickRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, joystickRadius, borderPaint)

        // Draw inner circle (joystick)
        canvas.drawCircle(innerCircleX, innerCircleY, innerRadius, innerPaint)
        canvas.drawCircle(innerCircleX, innerCircleY, innerRadius, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY

                val distance = sqrt(dx.toDouble().pow(2) + dy.toDouble().pow(2))
                val maxDistance = joystickRadius - innerRadius

                if (distance <= maxDistance) {
                    innerCircleX = event.x
                    innerCircleY = event.y
                } else {
                    // Clamp to outer circle boundary
                    val ratio = maxDistance / distance
                    innerCircleX = centerX + dx.toFloat() * ratio.toFloat()
                    innerCircleY = centerY + dy.toFloat() * ratio.toFloat()
                }

                // Calculate angle (0° = top, 90° = right, 180° = bottom, 270° = left)
                angle = atan2(innerCircleY - centerY, innerCircleX - centerX) * (180 / PI) + 90
                if (angle < 0) angle += 360

                // Calculate strength (0 = center, 1 = full)
                strength = sqrt(
                    (innerCircleX - centerX).toDouble().pow(2) +
                    (innerCircleY - centerY).toDouble().pow(2)
                ) / maxDistance.toFloat()

                onJoystickMoveListener?.invoke(angle, strength)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                innerCircleX = centerX
                innerCircleY = centerY
                angle = 0.0
                strength = 0f
                onJoystickMoveListener?.invoke(angle, strength)
                invalidate()
            }
        }
        return true
    }

    fun setOnJoystickMoveListener(listener: (angle: Double, strength: Float) -> Unit) {
        this.onJoystickMoveListener = listener
    }

    fun reset() {
        innerCircleX = centerX
        innerCircleY = centerY
        angle = 0.0
        strength = 0f
        onJoystickMoveListener?.invoke(angle, strength)
        invalidate()
    }
}