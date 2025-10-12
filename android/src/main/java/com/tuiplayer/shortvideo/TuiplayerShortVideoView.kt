package com.tuiplayer.shortvideo

import android.util.DisplayMetrics
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearSmoothScroller
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerLiveStrategy
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerVodStrategy
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUICustomLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUILiveLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIVodLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.custom.TUICustomLayerManager
import com.tencent.qcloud.tuiplayer.core.api.ui.view.live.TUILiveLayerManager
import com.tencent.qcloud.tuiplayer.core.api.ui.view.vod.TUIVodLayerManager
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoListener
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
  private var isManuallyPaused = false
  private var isReleased = false
  private var currentSources: List<TuiplayerShortVideoSource> = emptyList()
  private var lastEndReachedTotal = -1
  private var lastKnownIndex = -1
  private var pendingInitialIndex: Int? = null
  private var customVodStrategy: TUIPlayerVodStrategy? = null
  private var customLiveStrategy: TUIPlayerLiveStrategy? = null
  private var userInputEnabled: Boolean = true
  private var pageScrollMsPerInch: Float? = null
  private var layerConfig: LayerConfig? = null

  private val shortVideoListener = object : TUIShortVideoListener() {
    override fun onCreateVodLayer(
      layerManager: com.tencent.qcloud.tuiplayer.core.api.ui.view.vod.TUIVodLayerManager,
      position: Int
    ) {
      applyVodLayers(layerManager)
    }

    override fun onCreateLiveLayer(
      layerManager: com.tencent.qcloud.tuiplayer.core.api.ui.view.live.TUILiveLayerManager,
      position: Int
    ) {
      applyLiveLayers(layerManager)
    }

    override fun onCreateCustomLayer(
      layerManager: com.tencent.qcloud.tuiplayer.core.api.ui.view.custom.TUICustomLayerManager,
      position: Int
    ) {
      applyCustomLayers(layerManager)
    }

    override fun onPageChanged(index: Int, model: TUIPlaySource) {
      handlePageChanged(index)
    }
  }

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
    applyStrategies()
    shortVideoView.setListener(shortVideoListener)
  }

  private fun applyStrategies() {
    shortVideoView.setVodStrategy(customVodStrategy ?: createDefaultVodStrategyBuilder().build())
    shortVideoView.setLiveStrategy(customLiveStrategy ?: createDefaultLiveStrategyBuilder().build())
  }

  private fun createDefaultVodStrategyBuilder(): TUIPlayerVodStrategy.Builder {
    return TUIPlayerVodStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(TUIConstants.TUIRenderMode.FULL_FILL_SCREEN)
  }

  private fun createDefaultLiveStrategyBuilder(): TUIPlayerLiveStrategy.Builder {
    return TUIPlayerLiveStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(TUIConstants.TUIRenderMode.ADJUST_RESOLUTION)
  }

  fun setAutoPlay(value: Boolean) {
    autoPlay = value
    if (!autoPlay) {
      shortVideoView.pause()
    } else if (!isManuallyPaused) {
      shortVideoView.resume()
    }
  }

  fun setPaused(value: Boolean) {
    isManuallyPaused = value
    if (value) {
      shortVideoView.pause()
    } else if (autoPlay) {
      shortVideoView.resume()
    }
  }

  fun setInitialIndex(value: Int) {
    pendingInitialIndex = if (value >= 0) value else null
    maybeApplyInitialIndex()
  }

  fun setPlayMode(mode: Int) {
    if (mode >= 0) {
      shortVideoView.setPlayMode(mode)
    }
  }

  fun setUserInputEnabled(value: Boolean) {
    userInputEnabled = value
    shortVideoView.setUserInputEnabled(value)
  }

  fun setPageScrollMsPerInch(value: Double?) {
    pageScrollMsPerInch = value?.toFloat()
    applyPageScroller()
  }

  fun setLayerConfig(config: LayerConfig?) {
    layerConfig = config
  }

  fun setVodStrategy(config: ReadableMap?) {
    customVodStrategy = config?.let { buildVodStrategy(it) }
    applyStrategies()
  }

  fun setLiveStrategy(config: ReadableMap?) {
    customLiveStrategy = config?.let { buildLiveStrategy(it) }
    applyStrategies()
  }

  fun setSources(sources: List<TuiplayerShortVideoSource>) {
    isReleased = false
    val previous = currentSources
    val shouldAppend = shouldAppend(previous, sources)
    if (shouldAppend) {
      val appended = sources.subList(previous.size, sources.size)
      val appendedModels = appended.mapNotNull { it.toPlaySource() }
      if (appendedModels.isNotEmpty()) {
        shortVideoView.appendModels(appendedModels)
      }
    } else {
      val models = sources.mapNotNull { it.toPlaySource() }
      shortVideoView.setModels(models)
      lastEndReachedTotal = -1

      if (models.isNotEmpty()) {
        if (!maybeApplyInitialIndex()) {
          val targetIndex =
            if (lastKnownIndex in models.indices) lastKnownIndex else 0
          shortVideoView.startPlayIndex(targetIndex)
          lastKnownIndex = targetIndex
          if (autoPlay && !isManuallyPaused) {
            shortVideoView.resume()
          }
          dispatchPageChanged(targetIndex, models.size)
        }
      } else {
        lastKnownIndex = -1
      }
    }
    currentSources = sources
    maybeApplyInitialIndex()
    if (lastKnownIndex >= currentSources.size) {
      lastKnownIndex = currentSources.lastIndex
    }
    if (lastKnownIndex < 0 && currentSources.isNotEmpty()) {
      val currentIndex = shortVideoView.dataManager?.currentIndex ?: 0
      lastKnownIndex = currentIndex
      dispatchPageChanged(currentIndex, currentSources.size)
    }
  }

  fun appendSources(sources: List<TuiplayerShortVideoSource>) {
    val models = sources.mapNotNull { it.toPlaySource() }
    if (models.isEmpty()) {
      return
    }
    shortVideoView.appendModels(models)
    currentSources = currentSources + sources
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isReleased = false
    bindLifecycle()
    shortVideoView.setUserInputEnabled(userInputEnabled)
    applyPageScroller()
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
    if (autoPlay && !isManuallyPaused) {
      shortVideoView.resume()
    }
  }

  override fun onHostPause() {
    shortVideoView.pause()
  }

  override fun onHostDestroy() {
    releaseInternal()
  }

  private fun shouldAppend(
    previous: List<TuiplayerShortVideoSource>,
    current: List<TuiplayerShortVideoSource>
  ): Boolean {
    if (previous.isEmpty()) {
      return false
    }
    if (current.size < previous.size) {
      return false
    }
    val lastIndex = previous.lastIndex
    for (index in 0..lastIndex) {
      if (previous[index] != current[index]) {
        return false
      }
    }
    return current.size > previous.size
  }

  private fun handlePageChanged(index: Int) {
    val total = currentSources.size
    if (total <= 0) {
      return
    }
    if (index != lastKnownIndex) {
      lastKnownIndex = index
      dispatchPageChanged(index, total)
    }
    val remaining = total - index - 1
    if (remaining <= END_REACHED_THRESHOLD) {
      dispatchEndReached(index, total)
    }
  }

  private fun dispatchPageChanged(index: Int, total: Int) {
    val eventDispatcher = UIManagerHelper.getEventDispatcher(themedReactContext, id)
    if (eventDispatcher != null) {
      val surfaceId = UIManagerHelper.getSurfaceId(themedReactContext)
      eventDispatcher.dispatchEvent(
        TuiplayerShortVideoPageChangedEvent(surfaceId, id, index, total)
      )
    } else {
      val params = com.facebook.react.bridge.Arguments.createMap().apply {
        putInt("index", index)
        putInt("total", total)
      }
      themedReactContext
        .getJSModule(RCTEventEmitter::class.java)
        .receiveEvent(id, TuiplayerShortVideoPageChangedEvent.EVENT_NAME, params)
    }
  }

  private fun dispatchEndReached(index: Int, total: Int) {
    if (total <= 0) {
      return
    }
    if (total == lastEndReachedTotal) {
      return
    }
    lastEndReachedTotal = total
    val eventDispatcher = UIManagerHelper.getEventDispatcher(themedReactContext, id)
    if (eventDispatcher != null) {
      val surfaceId = UIManagerHelper.getSurfaceId(themedReactContext)
      eventDispatcher.dispatchEvent(
        TuiplayerShortVideoEndReachedEvent(surfaceId, id, index, total)
      )
    } else {
      val params = com.facebook.react.bridge.Arguments.createMap().apply {
        putInt("index", index)
        putInt("total", total)
      }
      themedReactContext
        .getJSModule(RCTEventEmitter::class.java)
        .receiveEvent(id, TuiplayerShortVideoEndReachedEvent.EVENT_NAME, params)
    }
  }

  companion object {
    private const val END_REACHED_THRESHOLD = 2
  }

  fun commandStartPlayIndex(index: Int, smooth: Boolean) {
    shortVideoView.startPlayIndex(index, smooth)
    lastKnownIndex = index
  }

  fun commandSetPlayMode(mode: Int) {
    setPlayMode(mode)
  }

  fun commandRelease() {
    releaseInternal()
  }

  fun commandResume() {
    isManuallyPaused = false
    shortVideoView.resume()
  }

  fun commandSwitchResolution(resolution: Double, target: Int) {
    shortVideoView.switchResolution(resolution.toLong(), target)
  }

  fun commandPausePreload() {
    shortVideoView.pausePreload()
  }

  fun commandResumePreload() {
    shortVideoView.resumePreload()
  }

  fun commandSetUserInputEnabled(enabled: Boolean) {
    setUserInputEnabled(enabled)
  }

  private fun applyPageScroller() {
    val ms = pageScrollMsPerInch
    if (ms == null || ms <= 0f) {
      return
    }
    val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
      override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return ms / displayMetrics.densityDpi
      }
    }
    shortVideoView.setPageScroller(scroller)
  }

  private fun maybeApplyInitialIndex(): Boolean {
    val total = currentSources.size
    val target = pendingInitialIndex?.takeIf { total > 0 }?.coerceIn(0, total - 1)
    if (target != null) {
      shortVideoView.startPlayIndex(target)
      lastKnownIndex = target
      if (autoPlay && !isManuallyPaused) {
        shortVideoView.resume()
      }
      dispatchPageChanged(target, total)
      pendingInitialIndex = null
      return true
    }
    return false
  }

  private fun buildVodStrategy(config: ReadableMap): TUIPlayerVodStrategy {
    val builder = createDefaultVodStrategyBuilder()

    config.getIntOrNull("preloadCount")?.let { builder.setPreloadCount(it) }
    config.getDoubleOrNull("preDownloadSize")?.let { builder.setPreDownloadSize(it.toFloat()) }
    config.getDoubleOrNull("preLoadBufferSize")?.let { builder.setPreLoadBufferSize(it.toFloat()) }
    config.getDoubleOrNull("maxBufferSize")?.let { builder.setMaxBufferSize(it.toFloat()) }

    config.getMapOrNull("preferredResolution")?.let { resolutionMap ->
      val width = resolutionMap.getIntOrNull("width")
      val height = resolutionMap.getIntOrNull("height")
      if (width != null && height != null && width > 0 && height > 0) {
        builder.setPreferredResolution(width.toLong() * height.toLong())
      }
    }

    config.getIntOrNull("progressInterval")?.let { builder.setProgressInterval(it) }
    config.getIntOrNull("renderMode")?.let { builder.setRenderMode(it) }
    config.getIntOrNull("mediaType")?.let { builder.setMediaType(it) }

    config.getStringOrNull("resumeMode")?.let { modeValue ->
      val resumeMode = mapResumeMode(modeValue)
      resumeMode?.let { builder.setResumeMode(it) }
    }

    config.getBooleanOrNull("enableAutoBitrate")?.let { builder.enableAutoBitrate(it) }
    config.getBooleanOrNull("enableAccurateSeek")?.let { builder.setEnableAccurateSeek(it) }
    config.getDoubleOrNull("audioNormalization")?.let { builder.setAudioNormalization(it.toFloat()) }
    config.getBooleanOrNull("retainPreVod")?.let { builder.setIsRetainPreVod(it) }

    config.getStringOrNull("superResolutionMode")?.let { mode ->
      mapSuperResolutionMode(mode)?.let { builder.setSuperResolutionMode(it) }
    }

    config.getIntOrNull("retryCount")?.let { builder.setRetryCount(it) }

    config.getStringOrNull("prePlayStrategy")?.let { strategyValue ->
      mapPrePlayStrategy(strategyValue)?.let { builder.setPrePlayStrategy(it) }
    }

    return builder.build()
  }

  private fun buildLiveStrategy(config: ReadableMap): TUIPlayerLiveStrategy {
    val builder = createDefaultLiveStrategyBuilder()

    config.getIntOrNull("renderMode")?.let { builder.setRenderMode(it) }
    config.getBooleanOrNull("retainPreLive")?.let { builder.setIsRetainPreLive(it) }
    config.getStringOrNull("prePlayStrategy")?.let { strategyValue ->
      mapPrePlayStrategy(strategyValue)?.let { builder.setPrePlayStrategy(it) }
    }
    return builder.build()
  }

  private fun mapResumeMode(value: String): Int? {
    return when (value.uppercase()) {
      "NONE", "0" -> TUIConstants.TUIResumeMode.NONE
      "RESUME_LAST", "1" -> TUIConstants.TUIResumeMode.RESUME_LAST
      "RESUME_PLAYED", "2" -> TUIConstants.TUIResumeMode.RESUME_PLAYED
      else -> value.toIntOrNull()
    }
  }

  private fun mapSuperResolutionMode(value: String): Int? {
    return when (value.uppercase()) {
      "SUPER_RESOLUTION_NONE", "0" -> TUIConstants.TUISuperResolution.SUPER_RESOLUTION_NONE
      "SUPER_RESOLUTION_ASR", "1" -> TUIConstants.TUISuperResolution.SUPER_RESOLUTION_ASR
      else -> value.toIntOrNull()
    }
  }

  private fun mapPrePlayStrategy(value: String): TUIConstants.TUIPrePlayStrategy? {
    return try {
      TUIConstants.TUIPrePlayStrategy.valueOf(value)
    } catch (_: IllegalArgumentException) {
      when (value.uppercase()) {
        "NEXT" -> TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext
        "PREVIOUS" -> TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyPrevious
        "ADJACENT" -> TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyAdjacent
        "NONE" -> TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNone
        else -> null
      }
    }
  }

  private fun applyVodLayers(manager: TUIVodLayerManager) {
    val config = layerConfig ?: return
    config.vodLayers.forEach { name ->
      val layer = instantiateLayer(name)
      if (layer is TUIVodLayer) {
        manager.addLayer(layer)
      }
    }
  }

  private fun applyLiveLayers(manager: TUILiveLayerManager) {
    val config = layerConfig ?: return
    config.liveLayers.forEach { name ->
      val layer = instantiateLayer(name)
      if (layer is TUILiveLayer) {
        manager.addLayer(layer)
      }
    }
  }

  private fun applyCustomLayers(manager: TUICustomLayerManager) {
    val config = layerConfig ?: return
    config.customLayers.forEach { name ->
      val layer = instantiateLayer(name)
      if (layer is TUICustomLayer) {
        manager.addLayer(layer)
      }
    }
  }

  private fun instantiateLayer(className: String): TUIBaseLayer? {
    return try {
      val clazz = Class.forName(className)
      val instance = clazz.getDeclaredConstructor().newInstance()
      instance as? TUIBaseLayer
    } catch (_: Throwable) {
      null
    }
  }
}

data class LayerConfig(
  val vodLayers: List<String>,
  val liveLayers: List<String>,
  val customLayers: List<String>
)

private fun ReadableMap.getIntOrNull(key: String): Int? {
  return if (hasKey(key) && !isNull(key)) getInt(key) else null
}

private fun ReadableMap.getDoubleOrNull(key: String): Double? {
  return if (hasKey(key) && !isNull(key)) getDouble(key) else null
}

private fun ReadableMap.getBooleanOrNull(key: String): Boolean? {
  return if (hasKey(key) && !isNull(key)) getBoolean(key) else null
}

private fun ReadableMap.getStringOrNull(key: String): String? {
  return if (hasKey(key) && !isNull(key)) getString(key) else null
}

private fun ReadableMap.getMapOrNull(key: String): ReadableMap? {
  return if (hasKey(key) && !isNull(key)) getMap(key) else null
}
