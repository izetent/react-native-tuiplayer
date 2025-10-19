@file:Suppress("DEPRECATION")

package com.tuiplayer.shortvideo

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearSmoothScroller
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.ViewUtil
import com.facebook.react.uimanager.events.EventDispatcher
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerLiveStrategy
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerVodStrategy
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants
import com.tencent.qcloud.tuiplayer.core.api.common.TUIErrorCode
import com.tencent.qcloud.tuiplayer.core.api.model.TUILiveSource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIFileVideoInfo
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerBitrateItem
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerVideoConfig
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource
import com.tencent.qcloud.tuiplayer.core.api.ui.player.ITUIVodPlayer
import com.tencent.qcloud.tuiplayer.core.api.ui.player.TUIVodObserver
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUICustomLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUILiveLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIVodLayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.custom.TUICustomLayerManager
import com.tencent.qcloud.tuiplayer.core.api.ui.view.live.TUILiveLayerManager
import com.tencent.qcloud.tuiplayer.core.api.ui.view.vod.TUIVodLayerManager
import com.tencent.qcloud.tuiplayer.shortvideo.api.data.TUIShortVideoDataManager
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoListener
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoView
import com.tencent.rtmp.TXTrackInfo
import com.tuiplayer.shortvideo.layer.TuiplayerCoverLayer
import com.tuiplayer.shortvideo.layer.TuiplayerHostAwareLayer
import com.tuiplayer.shortvideo.layer.TuiplayerInfoLayer
import com.tuiplayer.shortvideo.layer.TuiplayerLayerHost
import java.util.Collections
import java.util.WeakHashMap

internal class TuiplayerShortVideoView(
  private val themedReactContext: ThemedReactContext
  ) : FrameLayout(themedReactContext), LifecycleEventListener, TuiplayerLayerHost {

  private val shortVideoView = TUIShortVideoView(themedReactContext)
  private var currentVodPlayer: ITUIVodPlayer? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private val lifecycleObserver = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      if (appWasPlayingBeforePause && autoPlay && !isManuallyPaused) {
        shortVideoView.resume()
        notifyHostLayersPaused(false)
        this@TuiplayerShortVideoView.flushPendingStartCommand("lifecycle.onResume")
      } else {
        notifyHostLayersPaused(true)
      }
      appWasPlayingBeforePause = false
    }

    override fun onPause(owner: LifecycleOwner) {
      appWasPlayingBeforePause = shouldResumeAfterBackground()
      shortVideoView.pause()
      currentVodPlayer?.pause()
      notifyHostLayersPaused(true)
    }

    override fun onDestroy(owner: LifecycleOwner) {
      releaseInternal()
    }
  }
  private var autoPlay: Boolean = true
  private var isManuallyPaused = false
  private var appWasPlayingBeforePause = false
  private var isReleased = false
  private var currentSources: MutableList<TuiplayerShortVideoSource> = mutableListOf()
  private var lastEndReachedTotal = -1
  private var lastKnownIndex = -1
  private var pendingInitialIndex: Int? = null
  private var customVodStrategy: TUIPlayerVodStrategy? = null
  private var customLiveStrategy: TUIPlayerLiveStrategy? = null
  private var userInputEnabled: Boolean = true
  private var pageScrollMsPerInch: Float? = null
  private var layerConfig: LayerConfig? = null
  private val hostAwareLayers: MutableSet<TuiplayerHostAwareLayer> =
    Collections.newSetFromMap(WeakHashMap())
  private var isViewReady = false
  private var pendingResolvedSources: List<Pair<TuiplayerShortVideoSource, TUIPlaySource>>? = null
  private var pendingStartCommand: PendingStartCommand? = null
  private var pendingStartRetry: Runnable? = null

  private val vodObserver = object : TUIVodObserver {
    override fun onPlayPrepare() {
      emitVodEvent("onPlayPrepare", null)
    }

    override fun onPlayBegin() {
      emitVodEvent("onPlayBegin", null)
    }

    override fun onPlayPause() {
      emitVodEvent("onPlayPause", null)
    }

    override fun onPlayStop() {
      emitVodEvent("onPlayStop", null)
    }

    override fun onPlayLoading() {
      emitVodEvent("onPlayLoading", null)
    }

    override fun onPlayLoadingEnd() {
      emitVodEvent("onPlayLoadingEnd", null)
    }

    override fun onPlayProgress(current: Long, duration: Long, playable: Long) {
      val map = Arguments.createMap().apply {
        putDouble("current", current.toDouble())
        putDouble("duration", duration.toDouble())
        putDouble("playable", playable.toDouble())
      }
      emitVodEvent("onPlayProgress", map)
    }

    override fun onSeek(position: Float) {
      val map = Arguments.createMap().apply {
        putDouble("position", position.toDouble())
      }
      emitVodEvent("onSeek", map)
    }

    override fun onError(code: Int, message: String?, bundle: Bundle?) {
      val map = Arguments.createMap().apply {
        putInt("code", code)
        if (!message.isNullOrEmpty()) {
          putString("message", message)
        }
        bundle?.toWritableMap()?.let { putMap("extra", it) }
      }
      emitVodEvent("onError", map)
    }

    override fun onRcvFirstIframe() {
      emitVodEvent("onRcvFirstIframe", null)
    }

    override fun onRcvAudioTrackInformation(list: List<TXTrackInfo>) {
      emitVodEvent("onRcvAudioTrackInformation", list.toWritableArray())
    }

    override fun onRcvTrackInformation(list: List<TXTrackInfo>) {
      emitVodEvent("onRcvTrackInformation", list.toWritableArray())
    }

    override fun onRcvSubTitleTrackInformation(list: List<TXTrackInfo>) {
      emitVodEvent("onRcvSubtitleTrackInformation", list.toWritableArray())
    }

    override fun onRecFileVideoInfo(info: TUIFileVideoInfo) {
      emitVodEvent("onRecFileVideoInfo", info.toWritableMap())
    }

    override fun onResolutionChanged(width: Long, height: Long) {
      val map = Arguments.createMap().apply {
        putDouble("width", width.toDouble())
        putDouble("height", height.toDouble())
      }
      emitVodEvent("onResolutionChanged", map)
    }

    override fun onPlayEvent(player: ITUIVodPlayer, event: Int, bundle: Bundle?) {
      val map = Arguments.createMap().apply {
        putInt("event", event)
        bundle?.toWritableMap()?.let { putMap("extra", it) }
      }
      emitVodEvent("onPlayEvent", map)
    }

    override fun onFirstFrameRendered() {
      emitVodEvent("onFirstFrameRendered", null)
    }

    override fun onPlayEnd() {
      emitVodEvent("onPlayEnd", null)
    }

    override fun onRetryConnect(times: Int, bundle: Bundle?) {
      val map = Arguments.createMap().apply {
        putInt("count", times)
        bundle?.toWritableMap()?.let { putMap("extra", it) }
      }
      emitVodEvent("onRetryConnect", map)
    }
  }

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

    override fun onVodPlayerReady(player: ITUIVodPlayer, model: TUIVideoSource) {
      super.onVodPlayerReady(player, model)
      detachCurrentVodPlayer()
      currentVodPlayer = player
      player.addPlayerObserver(vodObserver)
      flushPendingStartCommand("onVodPlayerReady")
    }

    override fun onCreateCustomLayer(
      layerManager: com.tencent.qcloud.tuiplayer.core.api.ui.view.custom.TUICustomLayerManager,
      position: Int
    ) {
      applyCustomLayers(layerManager)
    }

    override fun onPageChanged(index: Int, model: TUIPlaySource) {
      handlePageChanged(index)
      flushPendingStartCommand("onPageChanged")
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
      shortVideoView.pause(true)
      notifyHostLayersPaused(true)
      if (pendingStartCommand?.forcePlay == false) {
        pendingStartCommand = null
        pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
        pendingStartRetry = null
      }
      appWasPlayingBeforePause = false
    } else if (!isManuallyPaused) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      flushPendingStartCommand("setAutoPlay")
    } else {
      notifyHostLayersPaused(true)
    }
  }

  fun setPaused(value: Boolean) {
    if (isManuallyPaused == value) {
      if (value) {
        notifyHostLayersPaused(true)
      } else if (autoPlay) {
        notifyHostLayersPaused(false)
        flushPendingStartCommand("setPaused.unchanged")
      }
      return
    }
    isManuallyPaused = value
    if (value) {
      shortVideoView.pause()
      currentVodPlayer?.pause()
      notifyHostLayersPaused(true)
      appWasPlayingBeforePause = false
      if (pendingStartCommand?.forcePlay == false) {
        pendingStartCommand = null
        pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
        pendingStartRetry = null
      }
    } else if (autoPlay) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      flushPendingStartCommand("setPaused")
      appWasPlayingBeforePause = false
    } else {
      notifyHostLayersPaused(true)
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
    Log.d(TAG, "setSources count=${sources.size} isViewReady=$isViewReady isReleased=$isReleased")
    isReleased = false
    val resolved = resolvePlayableSources(sources)
    Log.d(TAG, "setSources resolvedCount=${resolved.size}")
    val wasEmpty = currentSources.isEmpty()
    if (!isViewReady) {
      pendingResolvedSources = resolved
      currentSources = resolved.map { it.first }.toMutableList()
      lastKnownIndex = -1
      lastEndReachedTotal = -1
      if (wasEmpty && resolved.isNotEmpty() && autoPlay && pendingStartCommand == null) {
        pendingStartCommand = PendingStartCommand(0, false, false)
        pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
        pendingStartRetry = null
      }
      return
    }
    applyResolvedSources(resolved)
    if (wasEmpty && resolved.isNotEmpty() && autoPlay && pendingStartCommand == null) {
      setPendingStart(0, false, "setSources")
    } else {
      flushPendingStartCommand("setSources")
    }
  }

  fun appendSources(sources: List<TuiplayerShortVideoSource>) {
    Log.d(TAG, "appendSources incoming=${sources.size} isViewReady=$isViewReady")
    val resolved = resolvePlayableSources(sources)
    if (resolved.isEmpty()) {
      Log.w(TAG, "appendSources ignored; no playable entries (incoming=${sources.size})")
      return
    }
    val wasEmpty = currentSources.isEmpty()
    if (!isViewReady) {
      val pending = (pendingResolvedSources?.toMutableList() ?: mutableListOf())
      pending.addAll(resolved)
      pendingResolvedSources = pending
      currentSources = pending.map { it.first }.toMutableList()
      Log.d(
        TAG,
        "appendSources deferred until native ready (appended=${resolved.size})"
      )
      return
    }
    val models = resolved.map { it.second }
    val result = shortVideoView.appendModels(models)
    if (result != TUIErrorCode.TUI_ERROR_NONE) {
      Log.e(
        TAG,
        "appendModels failed (result=$result, appended=${models.size})"
      )
    }
    currentSources.addAll(resolved.map { it.first })
    if (wasEmpty && currentSources.isNotEmpty() && autoPlay && pendingStartCommand == null) {
      setPendingStart(0, false, "appendSources")
    } else {
      flushPendingStartCommand("appendSources")
    }
  }

  private fun applyResolvedSources(
    resolved: List<Pair<TuiplayerShortVideoSource, TUIPlaySource>>
  ) {
    val playableSources = resolved.map { it.first }
    val models = resolved.map { it.second }
    val previous = currentSources.toList()
    val shouldAppend = shouldAppend(previous, playableSources)

    if (!shouldAppend && previous.isNotEmpty() && previous == playableSources) {
      flushPendingStartCommand("applyResolvedSources.identity")
      return
    }

    if (!shouldAppend && previous.size == playableSources.size && previous.isNotEmpty()) {
      val manager = dataManagerOrNull()
      var allMatchPlayableId = true
      val changedIndices = mutableListOf<Int>()

      for (index in previous.indices) {
        val oldSource = previous[index]
        val newSource = playableSources[index]
        if (oldSource == newSource) {
          continue
        }
        if (oldSource.hasSamePlayableIdentity(newSource)) {
          currentSources[index] = newSource
          changedIndices.add(index)
        } else {
          allMatchPlayableId = false
          break
        }
      }

      if (allMatchPlayableId) {
        val currentIndex = getCurrentIndex()
        changedIndices.forEach { index ->
          val newSource = currentSources[index]
          if (index == currentIndex) {
            hostAwareLayers.forEach { layer ->
              layer.onMetadataUpdated(newSource)
            }
          } else if (manager != null) {
            newSource.toPlaySource()?.let { model ->
              manager.replaceData(model, index)
            }
          }
          notifyPlaylistItemChanged(index)
        }
        flushPendingStartCommand("applyResolvedSources.inPlace")
        return
      }
    }

    if (shouldAppend) {
      val appendedModels = models.subList(previous.size, models.size)
      if (appendedModels.isNotEmpty()) {
        val result = shortVideoView.appendModels(appendedModels)
        if (result != TUIErrorCode.TUI_ERROR_NONE) {
          Log.e(
            TAG,
            "appendModels (in setSources) failed (result=$result, appended=${appendedModels.size})"
          )
        } else {
          Log.d(TAG, "applyResolvedSources appended=${appendedModels.size}")
        }
      }
    } else {
      detachCurrentVodPlayer()
      val result = shortVideoView.setModels(models)
      if (result != TUIErrorCode.TUI_ERROR_NONE) {
        Log.e(
          TAG,
          "setModels failed (result=$result, count=${models.size})"
        )
      } else {
        Log.d(TAG, "applyResolvedSources replacedModels count=${models.size}")
      }
      lastEndReachedTotal = -1

      if (models.isNotEmpty()) {
        if (!maybeApplyInitialIndex()) {
          val targetIndex =
            if (lastKnownIndex in models.indices) lastKnownIndex else 0
          scheduleStartAtIndex(targetIndex)
          lastKnownIndex = targetIndex
          dispatchPageChanged(targetIndex, models.size)
          if (currentVodPlayer == null) {
            ensureInitialPlayback(targetIndex, 1)
          }
        }
      } else {
        Log.d(TAG, "applyResolvedSources -> resolved models empty")
        lastKnownIndex = -1
      }
    }

    currentSources = playableSources.toMutableList()
    maybeApplyInitialIndex()
    if (lastKnownIndex >= currentSources.size) {
      lastKnownIndex = currentSources.lastIndex
    }
    if (lastKnownIndex < 0 && currentSources.isNotEmpty()) {
      val currentIndex = shortVideoView.dataManager?.currentIndex ?: 0
      lastKnownIndex = currentIndex
      dispatchPageChanged(currentIndex, currentSources.size)
    }
    if (currentSources.isNotEmpty() && currentVodPlayer == null) {
      val target =
        if (lastKnownIndex in currentSources.indices) lastKnownIndex else 0
      ensureInitialPlayback(target, 1)
    }
    pendingResolvedSources = null
    if (currentSources.isNotEmpty() && previous.isEmpty() && autoPlay && pendingStartCommand == null) {
      setPendingStart(0, false, "applyResolvedSources")
    } else {
      flushPendingStartCommand("applyResolvedSources")
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isReleased = false
    bindLifecycle()
    shortVideoView.setUserInputEnabled(userInputEnabled)
    applyPageScroller()
    maybeMarkViewReady()
    flushPendingStartCommand("onAttachedToWindow")
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    shortVideoView.pause()
    notifyHostLayersPaused(true)
    pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
    pendingStartRetry = null
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

  private fun maybeMarkViewReady() {
    if (isViewReady) {
      return
    }
    shortVideoView.post {
      if (isViewReady || isReleased) {
        return@post
      }
      isViewReady = true
      Log.d(TAG, "Native view marked ready (tag=$id) pendingResolved=${pendingResolvedSources?.size ?: 0}")
      pendingResolvedSources?.let {
        applyResolvedSources(it)
      }
      pendingResolvedSources = null
      dispatchReadyEvent()
      flushPendingStartCommand("maybeMarkViewReady")
    }
  }

  private fun releaseInternal() {
    if (isReleased) {
      return
    }
    notifyHostLayersPaused(true)
    isReleased = true
    lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
    lifecycleOwner = null
    detachCurrentVodPlayer()
    shortVideoView.release()
    themedReactContext.removeLifecycleEventListener(this)
    hostAwareLayers.clear()
    pendingResolvedSources = null
    pendingStartCommand = null
    pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
    pendingStartRetry = null
  }

  override fun onHostResume() {
    bindLifecycle()
    if (appWasPlayingBeforePause && autoPlay && !isManuallyPaused) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      flushPendingStartCommand("onHostResume")
      appWasPlayingBeforePause = false
      return
    }
    shortVideoView.pause()
    currentVodPlayer?.pause()
    notifyHostLayersPaused(true)
    appWasPlayingBeforePause = false
  }

  override fun onHostPause() {
    appWasPlayingBeforePause = shouldResumeAfterBackground()
    shortVideoView.pause()
    currentVodPlayer?.pause()
    notifyHostLayersPaused(true)
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
      if (isManuallyPaused) {
        isManuallyPaused = false
      }
      lastKnownIndex = index
      dispatchPageChanged(index, total)
    }
    val remaining = total - index - 1
    if (remaining <= END_REACHED_THRESHOLD) {
      dispatchEndReached(index, total)
    }
  }

  private fun dispatchPageChanged(index: Int, total: Int) {
    dispatchPageChangedInternal(index, total, 0)
  }

  private fun dispatchEndReached(index: Int, total: Int) {
    if (total <= 0) {
      return
    }
    if (total == lastEndReachedTotal) {
      return
    }
    lastEndReachedTotal = total
    dispatchEndReachedInternal(index, total, 0)
  }

  companion object {
    private const val TAG = "TuiplayerShortVideoView"
    private const val END_REACHED_THRESHOLD = 2
    private const val MAX_EVENT_RETRY = 5
    private const val MAX_START_RETRY = 5
    private const val INITIAL_PLAYBACK_RETRY_DELAY_MS = 120L
  }

  fun commandStartPlayIndex(index: Int, smooth: Boolean) {
    if (tryStartAtIndex(index, smooth, forcePlayOverride = true)) {
      Log.d(TAG, "commandStartPlayIndex executed index=$index smooth=$smooth")
      return
    }
    setPendingStart(index, smooth, "commandStartPlayIndex", forcePlay = true)
  }

  fun commandSetPlayMode(mode: Int) {
    setPlayMode(mode)
  }

  fun commandRelease() {
    releaseInternal()
  }

  fun commandResume() {
    setPaused(false)
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

  fun commandSyncPlaybackState() {
    val currentIndex = getCurrentIndex()
    if (autoPlay && !isManuallyPaused) {
      if (currentIndex != null) {
        if (!tryStartAtIndex(currentIndex, false, forcePlayOverride = true)) {
          setPendingStart(currentIndex, false, "commandSyncPlaybackState", forcePlay = true)
        }
      }
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      flushPendingStartCommand("commandSyncPlaybackState")
      appWasPlayingBeforePause = false
    } else {
      shortVideoView.pause()
      notifyHostLayersPaused(true)
      if (currentIndex != null) {
        notifyPlaylistItemChanged(currentIndex)
      }
      appWasPlayingBeforePause = false
    }
  }

  fun handleVodPlayerCommand(command: String, options: ReadableMap?): Any? {
    val existingPlayer = currentVodPlayer
    when (command) {
      "isPlaying" -> return existingPlayer?.isPlaying() ?: false
      "isLoop" -> return existingPlayer?.isLoop() ?: false
      "getDuration" -> return existingPlayer?.duration?.toDouble() ?: 0.0
      "getCurrentPlaybackTime" -> return existingPlayer?.currentPlaybackTime?.toDouble() ?: 0.0
      "getPlayableDuration" -> return existingPlayer?.playableDuration?.toDouble() ?: 0.0
      "getBitrateIndex" -> return existingPlayer?.bitrateIndex ?: -1
      "getSupportResolution" -> {
        val resolutions = existingPlayer?.supportResolution?.toWritableArray()
        return resolutions ?: Arguments.createArray()
      }
      "getWidth" -> return existingPlayer?.width ?: 0
      "getHeight" -> return existingPlayer?.height ?: 0
    }

    val player = existingPlayer
      ?: throw IllegalStateException("VOD player is not ready yet")
    return when (command) {
      "startPlay" -> {
        val modelMap = options?.getMapOrNull("source")
        val source = modelMap?.toShortVideoSource()
        val model = source?.toPlaySource() as? TUIVideoSource
          ?: throw IllegalArgumentException("source is required for startPlay")
        player.startPlay(model)
        null
      }
      "resumePlay" -> {
        player.resumePlay()
        null
      }
      "pause" -> {
        player.pause()
        null
      }
      "stop" -> {
        val clearLast = options?.getBooleanOrNull("clearLastImage")
        if (clearLast != null) {
          player.stop(clearLast)
        } else {
          player.stop()
        }
        null
      }
      "seekTo" -> {
        val time = options?.getDoubleOrNull("time")?.toFloat()
          ?: throw IllegalArgumentException("time is required for seekTo")
        player.seekTo(time)
        null
      }
      "setLoop" -> {
        val loop = options?.getBooleanOrNull("loop")
          ?: throw IllegalArgumentException("loop is required for setLoop")
        player.setLoop(loop)
        null
      }
      "setRate" -> {
        val rate = options?.getDoubleOrNull("rate")?.toFloat()
          ?: throw IllegalArgumentException("rate is required for setRate")
        player.setRate(rate)
        null
      }
      "setMute" -> {
        val mute = options?.getBooleanOrNull("mute")
          ?: throw IllegalArgumentException("mute is required for setMute")
        player.setMute(mute)
        null
      }
      "setAudioPlayoutVolume" -> {
        val volume = options?.getIntOrNull("volume")
          ?: throw IllegalArgumentException("volume is required for setAudioPlayoutVolume")
        player.setAudioPlayoutVolume(volume)
        null
      }
      "setMirror" -> {
        val mirror = options?.getBooleanOrNull("mirror")
          ?: throw IllegalArgumentException("mirror is required for setMirror")
        player.setMirror(mirror)
        null
      }
      "setBitrateIndex" -> {
        val index = options?.getIntOrNull("index")
          ?: throw IllegalArgumentException("index is required for setBitrateIndex")
        player.setBitrateIndex(index)
        null
      }
      "setRenderRotation" -> {
        val rotation = options?.getIntOrNull("rotation")
          ?: throw IllegalArgumentException("rotation is required for setRenderRotation")
        player.setRenderRotation(rotation)
        null
      }
      "setRenderMode" -> {
        val mode = options?.getIntOrNull("mode")
          ?: throw IllegalArgumentException("mode is required for setRenderMode")
        player.setRenderMode(mode)
        null
      }
      "switchResolution" -> {
        val resolution = options?.getDoubleOrNull("resolution")
          ?: throw IllegalArgumentException("resolution is required for switchResolution")
        player.switchResolution(resolution.toLong())
        null
      }
      "setAudioNormalization" -> {
        val value = options?.getDoubleOrNull("value")?.toFloat()
          ?: throw IllegalArgumentException("value is required for setAudioNormalization")
        player.setAudioNormalization(value)
        null
      }
      "enableHardwareDecode" -> {
        val enable = options?.getBooleanOrNull("enable")
          ?: throw IllegalArgumentException("enable is required for enableHardwareDecode")
        player.enableHardwareDecode(enable)
      }
      else -> throw IllegalArgumentException("Unsupported VOD command: $command")
    }
  }

  fun addData(source: TuiplayerShortVideoSource, index: Int) {
    val manager = dataManagerOrNull() ?: return
    val model = source.toPlaySource() ?: return
    val target = index.coerceIn(0, currentSources.size)
    manager.addData(model, target)
    currentSources.add(target, source)
  }

  fun addRangeData(sources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    val manager = dataManagerOrNull() ?: return
    if (sources.isEmpty()) {
      return
    }
    val resolved = resolvePlayableSources(sources)
    if (resolved.isEmpty()) {
      return
    }
    if (resolved.size != sources.size) {
      Log.w(
        TAG,
        "addRangeData dropped ${sources.size - resolved.size} non-playable sources"
      )
    }
    val models = resolved.map { it.second }
    val target = startIndex.coerceIn(0, currentSources.size)
    manager.addRangeData(models, target)
    currentSources.addAll(target, resolved.map { it.first })
  }

  fun replaceData(source: TuiplayerShortVideoSource, index: Int) {
    val manager = dataManagerOrNull() ?: return
    if (index !in currentSources.indices) {
      return
    }
    val model = source.toPlaySource() ?: return
    manager.replaceData(model, index)
    currentSources[index] = source
    val currentIndex = getCurrentIndex()
    if (currentIndex != null && currentIndex == index) {
      hostAwareLayers.forEach { layer ->
        layer.onMetadataUpdated(source)
      }
    }
    notifyPlaylistItemChanged(index)
    flushPendingStartCommand("replaceData")
  }

  fun updateMetadata(index: Int, metadata: TuiplayerShortVideoSource.Metadata?) {
    if (index !in currentSources.indices || metadata == null) {
      return
    }
    val existing = currentSources[index]
    val merged = mergeMetadata(existing.metadata, metadata)
    val normalized = merged.takeIf { it.hasValue }
    if (existing.metadata == normalized) {
      return
    }
    currentSources[index] = existing.copy(metadata = normalized)
    val currentIndex = getCurrentIndex()
    if (currentIndex != null && currentIndex == index) {
      hostAwareLayers.forEach { layer ->
        layer.onMetadataUpdated(currentSources[index])
      }
    } else {
      notifyPlaylistItemChanged(index)
    }
    flushPendingStartCommand("updateMetadata")
  }

  fun replaceRangeData(sources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    val manager = dataManagerOrNull() ?: return
    if (sources.isEmpty()) {
      return
    }
    val resolved = resolvePlayableSources(sources)
    if (resolved.isEmpty()) {
      return
    }
    if (resolved.size != sources.size) {
      Log.w(
        TAG,
        "replaceRangeData aborted due to non-playable sources"
      )
      return
    }
    val models = resolved.map { it.second }
    val start = startIndex.coerceIn(0, currentSources.size)
    val end = (start + sources.size).coerceAtMost(currentSources.size)
    if (end - start != sources.size) {
      return
    }
    manager.replaceRangeData(models, start)
    for (offset in resolved.indices) {
      currentSources[start + offset] = resolved[offset].first
    }
    val currentIndex = getCurrentIndex()
    if (currentIndex != null && currentIndex in start until (start + resolved.size)) {
      val source = currentSources[currentIndex]
      hostAwareLayers.forEach { layer ->
        layer.onMetadataUpdated(source)
      }
      notifyPlaylistItemChanged(currentIndex)
    }
  }

  fun removeData(index: Int) {
    val manager = dataManagerOrNull() ?: return
    if (index !in currentSources.indices) {
      return
    }
    manager.removeData(index)
    currentSources.removeAt(index)
    if (lastKnownIndex >= currentSources.size) {
      lastKnownIndex = currentSources.lastIndex
    }
  }

  fun removeRangeData(index: Int, count: Int) {
    val manager = dataManagerOrNull() ?: return
    if (count <= 0 || currentSources.isEmpty()) {
      return
    }
    val start = index.coerceIn(0, currentSources.lastIndex)
    val endExclusive = (start + count).coerceAtMost(currentSources.size)
    if (endExclusive <= start) {
      return
    }
    val actualCount = endExclusive - start
    manager.removeRangeData(start, actualCount)
    currentSources.subList(start, endExclusive).clear()
    if (lastKnownIndex >= currentSources.size) {
      lastKnownIndex = currentSources.lastIndex
    }
  }

  fun removeDataByIndexes(indexes: List<Int>) {
    val manager = dataManagerOrNull() ?: return
    if (indexes.isEmpty()) {
      return
    }
    val normalized = indexes.filter { it in currentSources.indices }.sorted()
    if (normalized.isEmpty()) {
      return
    }
    manager.removeDataByIndex(normalized)
    normalized.asReversed().forEach { currentSources.removeAt(it) }
    if (lastKnownIndex >= currentSources.size) {
      lastKnownIndex = currentSources.lastIndex
    }
  }

  fun getSourceSnapshotAt(index: Int): WritableMap? {
    if (index !in currentSources.indices) {
      return null
    }
    return currentSources[index].toSnapshotMap()
  }

  fun getDataCount(): Int {
    return currentSources.size
  }

  private fun dataManagerOrNull(): TUIShortVideoDataManager? {
    return shortVideoView.dataManager
  }

  override fun resolveSource(model: TUIVideoSource): TuiplayerShortVideoSource? {
    return findSourceMatch(model)?.second
  }

  override fun resolveIndex(model: TUIVideoSource): Int? {
    return findSourceMatch(model)?.first
  }

  override fun emitOverlayAction(model: TUIVideoSource, action: String) {
    val payload = Arguments.createMap().apply {
      putString("action", action)
    }
    val match = findSourceMatch(model)
    if (match != null) {
      val (index, source) = match
      payload.putInt("index", index)
      payload.putMap("source", source.toSnapshotMap())
    } else {
      payload.putInt("index", -1)
    }
    emitVodEvent("overlayAction", payload)
  }

  override fun requestTogglePlay(): Boolean? {
    if (isReleased) {
      return null
    }
    val player = currentVodPlayer ?: return null
    return if (!isManuallyPaused) {
      setPaused(true)
      if (player.isPlaying()) {
        player.pause()
      }
      true
    } else {
      setPaused(false)
      if (!autoPlay && !player.isPlaying()) {
        player.resumePlay()
        notifyHostLayersPaused(false)
      }
      false
    }
  }

  private fun findSourceMatch(model: TUIVideoSource): Pair<Int, TuiplayerShortVideoSource>? {
    currentSources.forEachIndexed { index, source ->
      if (source.matchesModel(model)) {
        return index to source
      }
    }
    val currentIndex = shortVideoView.dataManager?.currentIndex
    if (currentIndex != null && currentIndex in currentSources.indices) {
      val fallback = currentSources[currentIndex]
      return currentIndex to fallback
    }
    return null
  }

  private fun registerHostLayer(layer: TuiplayerHostAwareLayer) {
    layer.attachHost(this)
    hostAwareLayers.add(layer)
    layer.onPlaybackStateChanged(isPlaybackPaused())
  }

  private fun notifyHostLayersPaused(paused: Boolean) {
    hostAwareLayers.forEach { hostLayer ->
      hostLayer.onPlaybackStateChanged(paused)
    }
  }

  private fun isPlaybackPaused(): Boolean {
    return isManuallyPaused || !autoPlay || isReleased
  }

  private fun detachCurrentVodPlayer() {
    currentVodPlayer?.removePlayerObserver(vodObserver)
    currentVodPlayer = null
  }

  private fun emitVodEvent(type: String, payload: WritableMap?) {
    if (isReleased) {
      return
    }
    if (id == View.NO_ID) {
      shortVideoView.post { emitVodEvent(type, payload) }
      return
    }
    val dispatcher = resolveEventDispatcher() ?: return
    dispatcher.dispatchEvent(
      TuiplayerShortVideoVodEvent(resolveSurfaceId(), id, type, payload)
    )
  }

  fun getCurrentIndex(): Int? {
    val dataManagerIndex = shortVideoView.dataManager?.currentIndex
    if (dataManagerIndex != null && dataManagerIndex >= 0) {
      return dataManagerIndex
    }
    if (lastKnownIndex in currentSources.indices) {
      return lastKnownIndex
    }
    return null
  }

  fun getCurrentSourceSnapshot(): WritableMap? {
    val index = getCurrentIndex() ?: return null
    if (index !in currentSources.indices) {
      val model = shortVideoView.currentModel ?: return null
      return model.toWritableMap()
    }
    return currentSources[index].toSnapshotMap()
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
      scheduleStartAtIndex(target)
      lastKnownIndex = target
      dispatchPageChanged(target, total)
      if (currentVodPlayer == null) {
        ensureInitialPlayback(target, 1)
      }
      pendingInitialIndex = null
      return true
    }
    return false
  }

  private fun scheduleStartAtIndex(index: Int) {
    if (tryStartAtIndex(index, false)) {
      Log.d(TAG, "scheduleStartAtIndex executed index=$index")
      return
    }
    val existing = pendingStartCommand
    if (existing != null && existing.forcePlay && existing.index != index) {
      Log.d(TAG, "scheduleStartAtIndex respecting manual pending index=${existing.index}")
      return
    }
    setPendingStart(index, false, "scheduleStartAtIndex", forcePlay = existing?.forcePlay == true)
  }

  private fun ensureInitialPlayback(index: Int, attempt: Int) {
    if (attempt > MAX_START_RETRY) {
      return
    }
    shortVideoView.postDelayed({
      if (isReleased) {
        return@postDelayed
      }
      if (currentVodPlayer != null) {
        return@postDelayed
      }
      if (currentSources.isEmpty() || index !in currentSources.indices) {
        return@postDelayed
      }
      if (!tryStartAtIndex(index, false)) {
        ensureInitialPlayback(index, attempt + 1)
      }
    }, INITIAL_PLAYBACK_RETRY_DELAY_MS)
  }

  private fun setPendingStart(index: Int, smooth: Boolean, reason: String, forcePlay: Boolean = false) {
    val latestSource = shortVideoView.currentModel
    Log.d(
      TAG,
      "setPendingStart index=$index smooth=$smooth forcePlay=$forcePlay reason=$reason pending=${pendingStartCommand?.index} currentModel=${latestSource?.javaClass?.simpleName}")
    pendingStartCommand = PendingStartCommand(index, smooth, forcePlay)
    flushPendingStartCommand(reason)
  }

  private fun tryStartAtIndex(index: Int, smooth: Boolean, forcePlayOverride: Boolean? = null): Boolean {
    if (isReleased || !isViewReady) {
      return false
    }
    if (index !in currentSources.indices) {
      Log.d(TAG, "tryStartAtIndex index=$index out_of_range size=${currentSources.size}")
      return false
    }
    if (isManuallyPaused && lastKnownIndex >= 0 && index != lastKnownIndex) {
      Log.d(TAG, "tryStartAtIndex reset manual pause for new index=$index")
      isManuallyPaused = false
    }
    val adapterTotal = resolveAdapterTotal()
    val forcePlay = forcePlayOverride ?: pendingStartCommand?.takeIf { it.index == index }?.forcePlay ?: false
    val currentModelIdentity = shortVideoView.currentModel?.let { model ->
      if (model is TUIVideoSource) {
        model.fileId?.takeIf { it.isNotBlank() } ?: model.videoURL
      } else {
        model.toString()
      }
    }
    if (adapterTotal >= 0 && index >= adapterTotal) {
      Log.d(TAG, "tryStartAtIndex waiting adapterTotal=$adapterTotal index=$index currentModel=$currentModelIdentity")
      return false
    }
    shortVideoView.startPlayIndex(index, smooth)
    Log.d(TAG, "tryStartAtIndex executed index=$index smooth=$smooth forcePlay=$forcePlay currentModel=$currentModelIdentity")
    if (forcePlay || (autoPlay && !isManuallyPaused)) {
      isManuallyPaused = false
      shortVideoView.resume()
      notifyHostLayersPaused(false)
    } else {
      notifyHostLayersPaused(true)
    }
    handlePageChanged(index)
    pendingStartCommand = null
    pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
    pendingStartRetry = null
    return true
  }

  private fun flushPendingStartCommand(reason: String) {
    val pending = pendingStartCommand ?: return
    if (tryStartAtIndex(pending.index, pending.smooth)) {
      Log.d(TAG, "flushPendingStartCommand executed reason=$reason index=${pending.index}")
    } else {
      Log.d(TAG, "flushPendingStartCommand deferred reason=$reason index=${pending.index}")
      schedulePendingStartRetry(reason)
    }
  }

  private fun schedulePendingStartRetry(reason: String) {
    val runnable = object : Runnable {
      override fun run() {
        flushPendingStartCommand("$reason.retry")
      }
    }
    pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
    pendingStartRetry = runnable
    shortVideoView.postDelayed(runnable, INITIAL_PLAYBACK_RETRY_DELAY_MS)
  }

  private fun shouldResumeAfterBackground(): Boolean {
    val player = currentVodPlayer
    return if (player != null) {
      try {
        player.isPlaying()
      } catch (_: Throwable) {
        autoPlay && !isManuallyPaused
      }
    } else {
      autoPlay && !isManuallyPaused
    }
  }

  private data class PendingStartCommand(
    val index: Int,
    val smooth: Boolean,
    val forcePlay: Boolean
  )

  private fun resolveAdapterTotal(): Int {
    val dataManager = shortVideoView.dataManager ?: return -1
    val candidates = arrayOf(
      "getTotal",
      "getTotalCount",
      "getCurrentDataCount",
      "getDataCount"
    )
    candidates.forEach { methodName ->
      try {
        val method = dataManager.javaClass.getMethod(methodName)
        val result = method.invoke(dataManager)
        val value = when (result) {
          is Number -> result.toInt()
          else -> null
        }
        if (value != null) {
          return value
        }
      } catch (_: Throwable) {
        // Ignore and try next candidate.
      }
    }
    return -1
  }

  private fun notifyPlaylistItemChanged(index: Int) {
    val dataManager = shortVideoView.dataManager
    if (dataManager != null) {
      val candidates = listOf(
        Pair("notifyItemDataChanged", true),
        Pair("notifyItemChanged", true),
        Pair("notifyDataChanged", false)
      )
      for ((methodName, needsIndex) in candidates) {
        try {
          val method = if (needsIndex) {
            dataManager.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
          } else {
            dataManager.javaClass.getMethod(methodName)
          }
          if (needsIndex) {
            method.invoke(dataManager, index)
          } else {
            method.invoke(dataManager)
          }
          return
        } catch (_: Throwable) {
          // Try next
        }
      }
    }
    // Fallback: request layout/invalidate to ensure UI refresh
    shortVideoView.post {
      shortVideoView.requestLayout()
      shortVideoView.invalidate()
    }
}

private fun TuiplayerShortVideoSource.hasSamePlayableIdentity(other: TuiplayerShortVideoSource): Boolean {
    if (type != other.type) return false
    return when (type ?: TuiplayerShortVideoSource.SourceType.FILE_ID) {
      TuiplayerShortVideoSource.SourceType.FILE_ID -> {
        appId == other.appId &&
          fileId.equals(other.fileId, ignoreCase = false) &&
          (url ?: "") == (other.url ?: "")
      }
      TuiplayerShortVideoSource.SourceType.URL -> {
        url == other.url
      }
    }
}

private fun mergeMetadata(
  current: TuiplayerShortVideoSource.Metadata?,
  update: TuiplayerShortVideoSource.Metadata
): TuiplayerShortVideoSource.Metadata {
  return TuiplayerShortVideoSource.Metadata(
    authorName = update.authorName ?: current?.authorName,
    authorAvatar = update.authorAvatar ?: current?.authorAvatar,
    title = update.title ?: current?.title,
    likeCount = update.likeCount ?: current?.likeCount,
    commentCount = update.commentCount ?: current?.commentCount,
    favoriteCount = update.favoriteCount ?: current?.favoriteCount,
    isLiked = update.isLiked ?: current?.isLiked,
    isBookmarked = update.isBookmarked ?: current?.isBookmarked,
    isFollowed = update.isFollowed ?: current?.isFollowed,
    watchMoreText = update.watchMoreText ?: current?.watchMoreText
  )
}

  private fun resolvePlayableSources(
    sources: List<TuiplayerShortVideoSource>
  ): List<Pair<TuiplayerShortVideoSource, TUIPlaySource>> {
    if (sources.isEmpty()) {
      return emptyList()
    }
    val resolved = ArrayList<Pair<TuiplayerShortVideoSource, TUIPlaySource>>(sources.size)
    sources.forEach { source ->
      val model = source.toPlaySource()
      if (model != null) {
        resolved.add(source to model)
      } else {
        Log.w(
          TAG,
          "Drop source without playable payload (type=${source.type}, fileId=${source.fileId}, url=${source.url})"
        )
      }
    }
    return resolved
  }

  private fun dispatchPageChangedInternal(index: Int, total: Int, retry: Int) {
    if (id == View.NO_ID) {
      if (retry >= MAX_EVENT_RETRY) {
        Log.w(TAG, "Abandon pageChanged event, view id not ready")
        return
      }
      shortVideoView.post { dispatchPageChangedInternal(index, total, retry + 1) }
      return
    }
    val dispatcher = resolveEventDispatcher()
    if (dispatcher != null) {
      emitPageChanged(dispatcher, index, total)
    } else if (retry < MAX_EVENT_RETRY) {
      shortVideoView.post { dispatchPageChangedInternal(index, total, retry + 1) }
    } else {
      Log.w(
        TAG,
        "Failed to dispatch pageChanged after retries (surfaceId=${resolveSurfaceId()}, tag=$id)"
      )
    }
  }

  private fun dispatchEndReachedInternal(index: Int, total: Int, retry: Int) {
    if (id == View.NO_ID) {
      if (retry >= MAX_EVENT_RETRY) {
        Log.w(TAG, "Abandon endReached event, view id not ready")
        return
      }
      shortVideoView.post { dispatchEndReachedInternal(index, total, retry + 1) }
      return
    }
    val dispatcher = resolveEventDispatcher()
    if (dispatcher != null) {
      emitEndReached(dispatcher, index, total)
    } else if (retry < MAX_EVENT_RETRY) {
      shortVideoView.post { dispatchEndReachedInternal(index, total, retry + 1) }
    } else {
      Log.w(
        TAG,
        "Failed to dispatch endReached after retries (surfaceId=${resolveSurfaceId()}, tag=$id)"
      )
    }
  }

  private fun dispatchReadyEvent(retry: Int = 0) {
    if (id == View.NO_ID) {
      if (retry >= MAX_EVENT_RETRY) {
        Log.w(TAG, "Abandon ready event, view id not ready")
        return
      }
      shortVideoView.post { dispatchReadyEvent(retry + 1) }
      return
    }
    val dispatcher = resolveEventDispatcher()
    if (dispatcher != null) {
      dispatcher.dispatchEvent(
        TuiplayerShortVideoReadyEvent(resolveSurfaceId(), id)
      )
    } else if (retry < MAX_EVENT_RETRY) {
      shortVideoView.post { dispatchReadyEvent(retry + 1) }
    } else {
      Log.w(
        TAG,
        "Failed to dispatch ready after retries (surfaceId=${resolveSurfaceId()}, tag=$id)"
      )
    }
  }

  private fun resolveEventDispatcher(): EventDispatcher? {
    val direct = UIManagerHelper.getEventDispatcherForReactTag(themedReactContext, id)
    if (direct != null) {
      return direct
    }
    val uiManagerType = ViewUtil.getUIManagerType(id)
    return UIManagerHelper.getEventDispatcher(themedReactContext, uiManagerType)
  }

  private fun emitPageChanged(dispatcher: EventDispatcher, index: Int, total: Int) {
    val surfaceId = resolveSurfaceId()
    dispatcher.dispatchEvent(
      TuiplayerShortVideoPageChangedEvent(surfaceId, id, index, total)
    )
  }

  private fun emitEndReached(dispatcher: EventDispatcher, index: Int, total: Int) {
    val surfaceId = resolveSurfaceId()
    dispatcher.dispatchEvent(
      TuiplayerShortVideoEndReachedEvent(surfaceId, id, index, total)
    )
  }

  private fun resolveSurfaceId(): Int {
    val fromView = UIManagerHelper.getSurfaceId(shortVideoView)
    if (fromView != -1) {
      return fromView
    }
    return UIManagerHelper.getSurfaceId(themedReactContext)
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
    var hasCoverLayer = false
    var hasInfoLayer = false

    val config = layerConfig
    if (config != null) {
      config.vodLayers.forEach { name ->
        val layer = instantiateLayer(name)
        if (layer is TUIVodLayer) {
          if (layer is TuiplayerHostAwareLayer) {
            registerHostLayer(layer)
          }
          val tag = runCatching { layer.tag() }.getOrNull()
          if (!hasCoverLayer && (layer.javaClass == TuiplayerCoverLayer::class.java ||
              tag == TuiplayerCoverLayer.TAG || tag == "CoverLayer")
          ) {
            hasCoverLayer = true
          }
          if (!hasInfoLayer && (layer.javaClass == TuiplayerInfoLayer::class.java ||
              tag == TuiplayerInfoLayer.TAG)
          ) {
            hasInfoLayer = true
          }
          manager.addLayer(layer)
        }
      }
    }
    if (!hasCoverLayer) {
      manager.addLayer(TuiplayerCoverLayer())
    }
    if (!hasInfoLayer) {
      manager.addLayer(
        TuiplayerInfoLayer().also { infoLayer ->
          registerHostLayer(infoLayer)
        }
      )
    }
  }

  private fun applyLiveLayers(manager: TUILiveLayerManager) {
    val config = layerConfig ?: return
    config.liveLayers.forEach { name ->
      val layer = instantiateLayer(name)
      if (layer is TUILiveLayer) {
        if (layer is TuiplayerHostAwareLayer) {
          registerHostLayer(layer)
        }
        manager.addLayer(layer)
      }
    }
  }

  private fun applyCustomLayers(manager: TUICustomLayerManager) {
    val config = layerConfig ?: return
    config.customLayers.forEach { name ->
      val layer = instantiateLayer(name)
      if (layer is TUICustomLayer) {
        if (layer is TuiplayerHostAwareLayer) {
          registerHostLayer(layer)
        }
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

private fun TUIPlaySource.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  map.putInt("extViewType", extViewType)
  when (this) {
    is TUIVideoSource -> {
      val fileId = getFileId()
      val url = getVideoURL()
      val appId = getAppId()
      val cover = getCoverPictureUrl()
      val pSign = getPSign()
      val type = when {
        !fileId.isNullOrBlank() -> "fileId"
        !url.isNullOrBlank() -> "url"
        else -> null
      }
      type?.let { map.putString("type", it) }
      if (appId > 0) {
        map.putInt("appId", appId)
      }
      map.putStringIfNotBlank("fileId", fileId)
      map.putStringIfNotBlank("url", url)
      map.putStringIfNotBlank("coverPictureUrl", cover)
      map.putStringIfNotBlank("pSign", pSign)
      map.putBoolean("autoPlay", isAutoPlay)
      if (duration > 0) {
        map.putInt("duration", duration)
      }
      val configMap = getVideoConfig()?.toWritableMapOrNull()
      if (configMap != null) {
        map.putMap("videoConfig", configMap)
      }
    }
    is TUILiveSource -> {
      map.putString("type", "url")
      map.putStringIfNotBlank("url", getUrl())
      map.putStringIfNotBlank("coverPictureUrl", getCoverPictureUrl())
      map.putBoolean("autoPlay", isAutoPlay)
    }
    else -> {
      // No-op for other TUIPlaySource types.
    }
  }
  return map
}

private fun TUIPlayerVideoConfig.toWritableMapOrNull(): WritableMap? {
  val map = Arguments.createMap()
  var hasValue = false
  val preloadBuffer = preloadBufferSizeInMB
  if (preloadBuffer > 0f) {
    map.putDouble("preloadBufferSizeInMB", preloadBuffer.toDouble())
    hasValue = true
  }
  val preDownloadBytes = preDownloadSize
  if (preDownloadBytes > 0L) {
    val mb = preDownloadBytes.toDouble() / (1024.0 * 1024.0)
    map.putDouble("preDownloadSize", mb)
    hasValue = true
  }
  return if (hasValue) map else null
}

private fun WritableMap.putStringIfNotBlank(key: String, value: String?) {
  if (!value.isNullOrBlank()) {
    putString(key, value)
  }
}

private fun Bundle.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  val iterator = keySet().iterator()
  while (iterator.hasNext()) {
    val key = iterator.next()
    when (val value = get(key)) {
      null -> map.putNull(key)
      is Boolean -> map.putBoolean(key, value)
      is Int -> map.putInt(key, value)
      is Long -> map.putDouble(key, value.toDouble())
      is Double -> map.putDouble(key, value)
      is Float -> map.putDouble(key, value.toDouble())
      is String -> map.putString(key, value)
      is Bundle -> map.putMap(key, value.toWritableMap())
      is IntArray -> {
        val array = Arguments.createArray()
        value.forEach { array.pushInt(it) }
        map.putArray(key, array)
      }
      is DoubleArray -> {
        val array = Arguments.createArray()
        value.forEach { array.pushDouble(it) }
        map.putArray(key, array)
      }
      is Array<*> -> {
        val array = Arguments.createArray()
        value.forEach { item ->
          item?.let { array.pushString(it.toString()) }
        }
        map.putArray(key, array)
      }
      else -> map.putString(key, value.toString())
    }
  }
  return map
}

private fun List<TXTrackInfo>?.toWritableArray(): WritableMap {
  val map = Arguments.createMap()
  val array = Arguments.createArray()
  this?.forEach { info ->
    val itemMap = Arguments.createMap()
    itemMap.putString("description", info.toString())
    array.pushMap(itemMap)
  }
  map.putArray("tracks", array)
  map.putInt("count", this?.size ?: 0)
  return map
}

private fun TUIFileVideoInfo.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  map.putStringIfNotBlank("url", url)
  map.putStringIfNotBlank("coverUrl", coverUrl)
  map.putStringIfNotBlank("playName", playName)
  map.putStringIfNotBlank("playDescription", playDescription)
  map.putInt("duration", duration)
  map.putStringIfNotBlank("drmType", drmType)
  return map
}

private fun ReadableMap.toShortVideoSource(): TuiplayerShortVideoSource? {
  val type = when (getStringOrNull("type")?.lowercase()) {
    "url" -> TuiplayerShortVideoSource.SourceType.URL
    else -> TuiplayerShortVideoSource.SourceType.FILE_ID
  }
  val appId = getDoubleOrNull("appId")?.toInt()
  val fileId = getStringOrNull("fileId")
  val url = getStringOrNull("url")
  val cover = getStringOrNull("coverPictureUrl")
  val pSign = getStringOrNull("pSign")
  val extViewType = getIntOrNull("extViewType")
  val autoPlay = getBooleanOrNull("autoPlay")
  val videoConfigMap = getMapOrNull("videoConfig")
  val videoConfig = if (videoConfigMap != null) {
    TuiplayerShortVideoSource.VideoConfig(
      preloadBufferSizeInMB = videoConfigMap.getDoubleOrNull("preloadBufferSizeInMB")?.toFloat(),
      preDownloadSizeInMB = videoConfigMap.getDoubleOrNull("preDownloadSize")
    )
  } else {
    null
  }
  val metadata = getMapOrNull("meta")?.toShortVideoMetadata()
  if (type == TuiplayerShortVideoSource.SourceType.FILE_ID && fileId.isNullOrBlank()) {
    return null
  }
  if (type == TuiplayerShortVideoSource.SourceType.URL && url.isNullOrBlank()) {
    return null
  }
  return TuiplayerShortVideoSource(
    type = type,
    appId = appId,
    fileId = fileId,
    url = url,
    coverPictureUrl = cover,
    pSign = pSign,
    extViewType = extViewType,
    autoPlay = autoPlay,
    videoConfig = videoConfig,
    metadata = metadata
  )
}

private fun List<TUIPlayerBitrateItem>.toWritableArray(): WritableArray {
  val array = Arguments.createArray()
  for (item in this) {
    val map = Arguments.createMap()
    map.putInt("index", item.index)
    map.putInt("width", item.width)
    map.putInt("height", item.height)
    map.putInt("bitrate", item.bitrate)
    array.pushMap(map)
  }
  return array
}

private fun TuiplayerShortVideoSource.toSnapshotMap(): WritableMap {
  val map = Arguments.createMap()
  when (type) {
    TuiplayerShortVideoSource.SourceType.FILE_ID -> map.putString("type", "fileId")
    TuiplayerShortVideoSource.SourceType.URL -> map.putString("type", "url")
  }
  appId?.let { map.putInt("appId", it) }
  map.putStringIfNotBlank("fileId", fileId)
  map.putStringIfNotBlank("url", url)
  map.putStringIfNotBlank("coverPictureUrl", coverPictureUrl)
  map.putStringIfNotBlank("pSign", pSign)
  extViewType?.let { map.putInt("extViewType", it) }
  autoPlay?.let { map.putBoolean("autoPlay", it) }
  videoConfig?.let { config ->
    val configMap = Arguments.createMap()
    var hasValue = false
    config.preloadBufferSizeInMB?.let {
      configMap.putDouble("preloadBufferSizeInMB", it.toDouble())
      hasValue = true
    }
    config.preDownloadSizeInMB?.let {
      configMap.putDouble("preDownloadSize", it)
      hasValue = true
    }
    if (hasValue) {
      map.putMap("videoConfig", configMap)
    }
  }
  metadata?.takeIf { it.hasValue }?.let { meta ->
    map.putMap("meta", meta.toWritableMap())
  }
  return map
}

private fun ReadableMap.toShortVideoMetadata(): TuiplayerShortVideoSource.Metadata? {
  val authorName = getStringOrNull("authorName")
  val authorAvatar = getStringOrNull("authorAvatar")
  val title = getStringOrNull("title")
  val likeCount = getDoubleOrNull("likeCount")?.toLong()
  val commentCount = getDoubleOrNull("commentCount")?.toLong()
  val favoriteCount = getDoubleOrNull("favoriteCount")?.toLong()
  val isLiked = getBooleanOrNull("isLiked")
  val isBookmarked = getBooleanOrNull("isBookmarked")
  val isFollowed = getBooleanOrNull("isFollowed")
  val watchMoreText = getStringOrNull("watchMoreText")

  val metadata = TuiplayerShortVideoSource.Metadata(
    authorName = authorName,
    authorAvatar = authorAvatar,
    title = title,
    likeCount = likeCount,
    commentCount = commentCount,
    favoriteCount = favoriteCount,
    isLiked = isLiked,
    isBookmarked = isBookmarked,
    isFollowed = isFollowed,
    watchMoreText = watchMoreText
  )
  return metadata.takeIf { it.hasValue }
}

private fun TuiplayerShortVideoSource.Metadata.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  authorName?.takeIf { it.isNotBlank() }?.let { map.putString("authorName", it) }
  authorAvatar?.takeIf { it.isNotBlank() }?.let { map.putString("authorAvatar", it) }
  title?.takeIf { it.isNotBlank() }?.let { map.putString("title", it) }
  likeCount?.let { map.putDouble("likeCount", it.toDouble()) }
  commentCount?.let { map.putDouble("commentCount", it.toDouble()) }
  favoriteCount?.let { map.putDouble("favoriteCount", it.toDouble()) }
  isLiked?.let { map.putBoolean("isLiked", it) }
  isBookmarked?.let { map.putBoolean("isBookmarked", it) }
  isFollowed?.let { map.putBoolean("isFollowed", it) }
  watchMoreText?.takeIf { it.isNotBlank() }?.let { map.putString("watchMoreText", it) }
  return map
}
