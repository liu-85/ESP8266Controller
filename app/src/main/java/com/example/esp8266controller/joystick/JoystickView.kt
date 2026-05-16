package com.example.esp8266controller.joystick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
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
    private val ringPaint: Paint = Paint()

    private var activePointerId: Int = -1

    private var onJoystickMoveListener: ((angle: Double, strength: Float) -> Unit)? = null
    private var onSwipeOutListener: (() -> Unit)? = null

    var angle: Double = 0.0
    var strength: Float = 0.0f

    init {
        // Style matching UI.txt
        outerPaint.color = Color.parseColor("#33FFFFFF")
        outerPaint.style = Paint.Style.FILL
        outerPaint.isAntiAlias = true

        innerPaint.color = Color.parseColor("#FFD700") // v7rc_yellow
        innerPaint.style = Paint.Style.FILL
        innerPaint.isAntiAlias = true

        ringPaint.color = Color.WHITE
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 2f
        ringPaint.isAntiAlias = true
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
        joystickRadius = min(w, h) / 2f * 0.9f
        innerRadius = joystickRadius * 0.4f

        innerCircleX = centerX
        innerCircleY = centerY

        // Add System Gesture Exclusion for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rect = Rect(0, 0, w, h)
            systemGestureExclusionRects = listOf(rect)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw ring
        canvas.drawCircle(centerX, centerY, joystickRadius, ringPaint)
        
        // Draw background
        canvas.drawCircle(centerX, centerY, joystickRadius, outerPaint)

        // Draw inner knob
        canvas.drawCircle(innerCircleX, innerCircleY, innerRadius, innerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Forcefully request parent to not intercept as soon as any touch happens.
        // This is crucial to prevent system gestures (like back) from triggering 
        // while the user is interacting with the joystick.
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                
                // If we don't have an active pointer, take this one
                if (activePointerId == -1) {
                    activePointerId = pointerId
                    updateJoystickPosition(event.getX(pointerIndex), event.getY(pointerIndex))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    updateJoystickPosition(event.getX(pointerIndex), event.getY(pointerIndex))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    activePointerId = -1
                    reset()
                }
            }
        }
        // Always return true to consume the event and prevent it from leaking to 
        // system gesture handlers or other views.
        return true
    }

    private fun updateJoystickPosition(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (dist <= joystickRadius) {
            innerCircleX = touchX
            innerCircleY = touchY
        } else {
            val ratio = joystickRadius / dist
            innerCircleX = centerX + dx * ratio
            innerCircleY = centerY + dy * ratio
            
            // Check for swipe out (e.g., if touch is 2x the radius away)
            if (dist > joystickRadius * 2.5f) {
                onSwipeOutListener?.invoke()
            }
        }

        // Calculate angle (0° = top, 90° = right, 180° = bottom, 270° = left)
        angle = atan2((innerCircleY - centerY).toDouble(), (innerCircleX - centerX).toDouble()) * (180 / PI) + 90
        if (angle < 0) angle += 360.0

        // Calculate strength (0.0 to 1.0)
        val currentDist = sqrt(
            (innerCircleX - centerX).toDouble().pow(2.0) +
                (innerCircleY - centerY).toDouble().pow(2.0)
        ).toFloat()
        strength = if (joystickRadius > 0) (currentDist / joystickRadius).coerceIn(0f, 1f) else 0f

        onJoystickMoveListener?.invoke(angle, strength)
        postInvalidate()
    }

    fun setColors(outerColor: Int, innerColor: Int) {
        outerPaint.color = outerColor
        innerPaint.color = innerColor
        invalidate()
    }

    fun setOnJoystickMoveListener(listener: (angle: Double, strength: Float) -> Unit) {
        this.onJoystickMoveListener = listener
    }

    fun setOnSwipeOutListener(listener: () -> Unit) {
        this.onSwipeOutListener = listener
    }

    fun reset() {
        innerCircleX = centerX
        innerCircleY = centerY
        angle = 0.0
        strength = 0f
        onJoystickMoveListener?.invoke(angle, strength)
        invalidate()
    }

    fun setKnobPosition(angle: Double, strength: Float) {
        this.angle = angle
        this.strength = strength.coerceIn(0f, 1f)
        
        val rad = Math.toRadians(angle - 90)
        val dist = strength * joystickRadius
        innerCircleX = centerX + (dist * cos(rad)).toFloat()
        innerCircleY = centerY + (dist * sin(rad)).toFloat()
        
        invalidate()
    }
}
