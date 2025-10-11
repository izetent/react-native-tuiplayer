package com.tuiplayer.shortvideo

import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.uimanager.ThemedReactContext
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerLiveStrategy
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerVodStrategy
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoView

internal class TuiplayerShortVideoView(
  private val themedReactContext: ThemedReactContext
) : FrameLayout(themedReactContext), LifecycleEventListener {

  private val shortVideoView = TUIShortVideoView(themedReactContext)
  private var lifecycleOwner: LifecycleOwner? = null
  private val lifecycleObserver = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      if (autoPlay) {
        shortVideoView.resume()
      }
    }

    override fun onPause(owner: LifecycleOwner) {
      shortVideoView.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
      releaseInternal()
    }
  }
  private var autoPlay: Boolean = true
  private var isReleased = false

  init {
    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    addView(
      shortVideoView,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
    themedReactContext.addLifecycleEventListener(this)
    setupDefaultStrategies()
  }

  private fun setupDefaultStrategies() {
    val vodStrategy = TUIPlayerVodStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(TUIConstants.TUIRenderMode.FULL_FILL_SCREEN)
      .build()
    shortVideoView.setVodStrategy(vodStrategy)

    val liveStrategy = TUIPlayerLiveStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(TUIConstants.TUIRenderMode.ADJUST_RESOLUTION)
      .build()
    shortVideoView.setLiveStrategy(liveStrategy)
  }

  fun setAutoPlay(value: Boolean) {
    autoPlay = value
  }

  fun setSources(sources: List<TuiplayerShortVideoSource>) {
    isReleased = false
    val models = sources.mapNotNull { it.toPlaySource() }
    shortVideoView.setModels(models)
    if (autoPlay) {
      shortVideoView.resume()
    }
  }

  fun appendSources(sources: List<TuiplayerShortVideoSource>) {
    val models = sources.mapNotNull { it.toPlaySource() }
    if (models.isEmpty()) {
      return
    }
    shortVideoView.appendModels(models)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isReleased = false
    bindLifecycle()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    shortVideoView.pause()
  }

  private fun bindLifecycle() {
    val activity = themedReactContext.currentActivity
    if (activity is LifecycleOwner) {
      if (lifecycleOwner === activity) {
        return
      }
      lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      lifecycleOwner = activity
      activity.lifecycle.addObserver(lifecycleObserver)
      shortVideoView.setActivityLifecycle(activity.lifecycle)
    }
  }

  private fun releaseInternal() {
    if (isReleased) {
      return
    }
    isReleased = true
    lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
    lifecycleOwner = null
    shortVideoView.release()
    themedReactContext.removeLifecycleEventListener(this)
  }

  override fun onHostResume() {
    bindLifecycle()
    if (autoPlay) {
      shortVideoView.resume()
    }
  }

  override fun onHostPause() {
    shortVideoView.pause()
  }

  override fun onHostDestroy() {
    releaseInternal()
  }
}
