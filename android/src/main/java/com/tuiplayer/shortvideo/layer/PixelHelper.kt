package com.tuiplayer.shortvideo.layer

import android.content.res.Resources
import kotlin.math.min
import kotlin.math.roundToInt

internal object PixelHelper {
  private const val DESIGN_WIDTH = 375f
  private const val DESIGN_HEIGHT = 667f

  private val displayMetrics
    get() = Resources.getSystem().displayMetrics

  private val density
    get() = displayMetrics.density

  private val widthDp
    get() = displayMetrics.widthPixels / density

  private val heightDp
    get() = displayMetrics.heightPixels / density

  private val scale
    get() = min(heightDp / DESIGN_HEIGHT, widthDp / DESIGN_WIDTH)

  fun px(size: Float): Int {
    return (size * scale * density).roundToInt()
  }

  fun pxF(size: Float): Float {
    return size * scale * density
  }

  fun screenWidthPx(): Int {
    return displayMetrics.widthPixels
  }
}
