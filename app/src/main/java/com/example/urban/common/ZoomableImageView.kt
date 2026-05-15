package com.example.urban.common

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imageMatrixValues = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private var currentScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val minScale = 1f
    private val maxScale = 4f

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = imageMatrixValues
    }

    // Resets the image back to the original centered state.
    fun resetZoom() {
        currentScale = 1f
        imageMatrixValues.reset()
        imageMatrix = imageMatrixValues
        invalidate()
    }

    // Handles drag, pinch, and double tap gestures on the image preview.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && currentScale > minScale) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (!isDragging) {
                        isDragging = true
                    }
                    imageMatrixValues.postTranslate(dx, dy)
                    imageMatrix = imageMatrixValues
                    invalidate()
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }

        return true
    }

    // Handles pinch zoom changes.
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val nextScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
            val appliedScale = nextScale / currentScale
            currentScale = nextScale

            imageMatrixValues.postScale(
                appliedScale,
                appliedScale,
                detector.focusX,
                detector.focusY
            )
            imageMatrix = imageMatrixValues
            invalidate()
            return true
        }
    }

    // Handles double tap to zoom in quickly or reset back.
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale) {
                resetZoom()
            } else {
                val targetScale = min(maxScale, 2f)
                val appliedScale = max(minScale, targetScale / currentScale)
                currentScale = targetScale
                imageMatrixValues.postScale(appliedScale, appliedScale, e.x, e.y)
                imageMatrix = imageMatrixValues
                invalidate()
            }
            return true
        }
    }
}
