package com.tuiplayer.shortvideo.layer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class TuiplayerProgressBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  interface OnSeekListener {
    fun onSeekStart()
    fun onSeekChanged(ratio: Float)
    fun onSeekFinished(ratio: Float, cancelled: Boolean)
  }

  private val collapsedHeightPx = PixelHelper.pxF(6f)
  private val expandedHeightPx = PixelHelper.pxF(12f)

  private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#2DFFFFFF")
  }
  private val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#60FFFFFF")
  }
  private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#FF782E")
  }
  private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#45FFFFFF")
  }

  private val trackRect = RectF()
  private val fillRect = RectF()

  private var trackHeightPx = collapsedHeightPx
  // Allow taps within (and slightly beyond) the view bounds to activate seeking.
  private val activationSlopPx = PixelHelper.pxF(32f)
  private var progressRatio = 0f
  private var bufferRatio = 0f
  private var dragRatio: Float? = null
  private var loadingProgress = 0f
  private var showLoading = false
  private var isTracking = false

  private var trackHeightAnimator: ValueAnimator? = null
  private var loadingAnimator: ValueAnimator? = null
  private var seekListener: OnSeekListener? = null
  private val touchExtensionPx = PixelHelper.pxF(24f)

  init {
    isClickable = true
    isFocusable = true
    setLoadingVisibleInternal(true, force = true)
  }

  fun reset() {
    progressRatio = 0f
    bufferRatio = 0f
    dragRatio = null
    isTracking = false
    loadingProgress = 0f
    setLoadingVisibleInternal(true, force = true)
    invalidate()
  }

  fun setProgressRatio(ratio: Float) {
    progressRatio = ratio.coerceIn(0f, 1f)
    if (!isTracking) {
      invalidate()
    }
  }

  fun setBufferedRatio(ratio: Float) {
    bufferRatio = ratio.coerceIn(0f, 1f)
    invalidate()
  }

  fun setLoadingVisible(visible: Boolean) {
    setLoadingVisibleInternal(visible)
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    alpha = if (enabled) 1f else 0.5f
  }

  fun setOnSeekListener(listener: OnSeekListener?) {
    seekListener = listener
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    trackHeightAnimator?.cancel()
    loadingAnimator?.cancel()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val desiredHeight = (expandedHeightPx + paddingTop + paddingBottom).roundToInt().coerceAtLeast(suggestedMinimumHeight)
    val measuredWidth = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
    val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
    setMeasuredDimension(measuredWidth, measuredHeight)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
    if (availableWidth <= 0) {
      return
    }
    val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
    val halfHeight = trackHeightPx / 2f
    val left = paddingLeft.toFloat()
    val right = left + availableWidth
    val top = centerY - halfHeight
    val bottom = centerY + halfHeight

    trackRect.set(left, top, right, bottom)
    val radius = halfHeight
    canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

    if (showLoading) {
      val loadingWidth = (availableWidth * loadingProgress).coerceIn(0f, availableWidth.toFloat())
      val loadingLeft = left + (availableWidth - loadingWidth) / 2f
      fillRect.set(loadingLeft, top, loadingLeft + loadingWidth, bottom)
      canvas.drawRoundRect(fillRect, radius, radius, loadingPaint)
      return
    }

    val bufferWidth = (availableWidth * bufferRatio).coerceIn(0f, availableWidth.toFloat())
    if (bufferWidth > 0f) {
      fillRect.set(left, top, left + bufferWidth, bottom)
      canvas.drawRoundRect(fillRect, radius, radius, bufferPaint)
    }

    val activeRatio = (dragRatio ?: progressRatio).coerceIn(0f, 1f)
    val progressWidth = (availableWidth * activeRatio).coerceIn(0f, availableWidth.toFloat())
    if (progressWidth > 0f) {
      fillRect.set(left, top, left + progressWidth, bottom)
      canvas.drawRoundRect(fillRect, radius, radius, progressPaint)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isEnabled) {
      return false
    }
    val ratio = computeRatio(event.x)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        if (!isWithinActiveBand(event.y)) {
          return false
        }
        parent.requestDisallowInterceptTouchEvent(true)
        isTracking = true
        dragRatio = ratio
        animateTrackHeight(expandedHeightPx)
        seekListener?.onSeekStart()
        seekListener?.onSeekChanged(ratio)
        invalidate()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (!isTracking) {
          return false
        }
        dragRatio = ratio
        seekListener?.onSeekChanged(ratio)
        invalidate()
        return true
      }
      MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
        if (!isTracking) {
          return false
        }
        parent.requestDisallowInterceptTouchEvent(false)
        isTracking = false
        dragRatio = null
        animateTrackHeight(collapsedHeightPx)
        seekListener?.onSeekFinished(ratio, event.actionMasked == MotionEvent.ACTION_CANCEL)
        invalidate()
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  private fun animateTrackHeight(target: Float) {
    val current = trackHeightPx
    if (current == target) {
      return
    }
    trackHeightAnimator?.cancel()
    trackHeightAnimator = ValueAnimator.ofFloat(current, target).apply {
      duration = 120L
      interpolator = DecelerateInterpolator()
      addUpdateListener { animator ->
        trackHeightPx = (animator.animatedValue as Float)
        invalidate()
      }
      start()
    }
  }

  private fun isWithinActiveBand(y: Float): Boolean {
    // Accept the full view height plus some slop so taps/drags aren't rejected,
    // and let ratio calculation clamp horizontally.
    val upper = -activationSlopPx
    val lower = height + activationSlopPx
    return y in upper..lower
  }

  private fun setLoadingVisibleInternal(visible: Boolean, force: Boolean = false) {
    if (!force && showLoading == visible) {
      return
    }
    showLoading = visible
    if (visible) {
      startLoadingAnimator()
    } else {
      stopLoadingAnimator()
    }
    invalidate()
  }

  private fun startLoadingAnimator() {
    if (loadingAnimator?.isRunning == true) {
      return
    }
    loadingProgress = 0f
    loadingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 700L
      repeatMode = ValueAnimator.REVERSE
      repeatCount = ValueAnimator.INFINITE
      addUpdateListener { animator ->
        loadingProgress = (animator.animatedValue as Float).coerceIn(0f, 1f)
        invalidate()
      }
      start()
    }
  }

  private fun stopLoadingAnimator() {
    loadingAnimator?.cancel()
    loadingAnimator = null
    loadingProgress = 0f
  }

  private fun computeRatio(x: Float): Float {
    val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
    val clamped = (x - paddingLeft).coerceIn(0f, contentWidth.toFloat())
    return clamped / contentWidth.toFloat()
  }
}
