@file:Suppress("DEPRECATION")

package com.tuiplayer.shortvideo

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearSmoothScroller
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
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
import com.tencent.liteav.txcplayer.model.TXSubtitleRenderModel
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
import com.tencent.rtmp.ui.TXSubtitleView
import com.tuiplayer.shortvideo.layer.PixelHelper
import com.tuiplayer.shortvideo.layer.TuiplayerCoverLayer
import com.tuiplayer.shortvideo.layer.TuiplayerHostAwareLayer
import com.tuiplayer.shortvideo.layer.TuiplayerInfoLayer
import com.tuiplayer.shortvideo.layer.TuiplayerLayerHost
import com.tuiplayer.shortvideo.playlist.PlaylistController
import com.tuiplayer.shortvideo.playlist.PlaylistHost
import com.tuiplayer.shortvideo.playlist.PlaylistPlaybackCoordinator
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.HashMap
import java.util.WeakHashMap

internal class TuiplayerShortVideoView(
  private val themedReactContext: ThemedReactContext
  ) : FrameLayout(themedReactContext), LifecycleEventListener, TuiplayerLayerHost, PlaylistHost {

  override val shortVideoView: TUIShortVideoView = TUIShortVideoView(themedReactContext)
  private val subtitleView: TXSubtitleView = TXSubtitleView(themedReactContext).apply {
    isClickable = false
    isFocusable = false

    alpha = 1f
    visibility = View.VISIBLE
  }
  private val topLoadingIndicator: ProgressBar = createLoadingIndicator()
  private val bottomLoadingIndicator: ProgressBar = createLoadingIndicator()
  private val subtitleLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
    override fun onGlobalLayout() {
      if (subtitleView.width > 0 && subtitleView.height > 0) {
        val displayMetrics = context.resources.displayMetrics

        currentVodPlayer?.let { bindSubtitleView(it, "onGlobalLayout") }
        subtitleView.viewTreeObserver.removeOnGlobalLayoutListener(this)
      }
    }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val playback = PlaybackCoordinator()
  private lateinit var playlist: PlaylistController
  init {
    playlist = PlaylistController(this, playback)
    playback.bindDataCountProvider { playlist.dataCount() }
  }
  private var currentVodPlayer: ITUIVodPlayer? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private val lifecycleObserver = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      if (appWasPlayingBeforePause && autoPlay && !isManuallyPaused) {
        shortVideoView.resume()
        notifyHostLayersPaused(false)
        playback.flushPendingStartCommand("lifecycle.onResume")
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
  override val autoPlayEnabled: Boolean
    get() = autoPlay
  private var isManuallyPaused = false
  private var appWasPlayingBeforePause = false
  override var isReleased: Boolean = false
    private set
  override var isViewReady: Boolean = false
    private set
  override var lastKnownIndex: Int = -1
  override var lastEndReachedTotal: Int = -1
  private var customVodStrategy: TUIPlayerVodStrategy? = null
  private var customLiveStrategy: TUIPlayerLiveStrategy? = null
  private var userInputEnabled: Boolean = true
  private var pageScrollMsPerInch: Float? = null
  private var layerConfig: LayerConfig? = null
  private val hostAwareLayers: MutableSet<TuiplayerHostAwareLayer> =
    Collections.newSetFromMap(WeakHashMap())
  private var currentVisibleIndex: Int? = null
  private val playersByIndex: MutableMap<Int, WeakReference<ITUIVodPlayer>> = HashMap()
  private val playerIndexLookup: WeakHashMap<ITUIVodPlayer, Int> = WeakHashMap()
  private val subtitleAttachmentSources:
    WeakHashMap<ITUIVodPlayer, WeakReference<TuiplayerShortVideoSource>> = WeakHashMap()
  private val playersRequiringSubtitles =
    Collections.newSetFromMap(WeakHashMap<ITUIVodPlayer, Boolean>())
  private val subtitleSelectionAttempts: MutableMap<ITUIVodPlayer, Int> = WeakHashMap()
  private var requestedRenderMode: Int = RENDER_MODE_FILL
  private var subtitleStyle: SubtitleStyleConfig? = null
  private var topLoadingVisible = false
  private var bottomLoadingVisible = false
  private var topLoadingPreview = false
  private var bottomLoadingPreview = false
  private var overlayVisible = true
  private var overDragOffset = 0f
  private var overDragAnimator: ValueAnimator? = null
  private val topTouchSlop: Int = ViewConfiguration.get(themedReactContext).scaledTouchSlop
  private val scrollBoundaryTracker = ScrollBoundaryTracker(
    shortVideoView,
    topTouchSlop,
    onTopReached = { offset -> dispatchTopReachedInternal(offset, 0) }
  )

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
      requestSubtitleSelectionForAllPlayers()
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
      val match = playlist.findSourceMatch(model)
      val matchIndex = match?.first
      val matchSource = match?.second
      if (matchIndex != null) {
        registerPlayerForIndex(player, matchIndex, matchSource)
      }
      attachSubtitles(player, matchSource)
      applySubtitleStyleToPlayer(player)
      val shouldPromote =
        currentVodPlayer == null ||
          currentVisibleIndex == null ||
          matchIndex == currentVisibleIndex
      if (shouldPromote) {
        attachCurrentVodPlayer(player)
        playback.flushPendingStartCommand("onVodPlayerReady")
      }
      if (matchSource?.subtitles?.isNotEmpty() == true) {
        maybeSelectSubtitleTrack(player)
      }
    }

    override fun onCreateCustomLayer(
      layerManager: com.tencent.qcloud.tuiplayer.core.api.ui.view.custom.TUICustomLayerManager,
      position: Int
    ) {
      applyCustomLayers(layerManager)
    }

    override fun onPageChanged(index: Int, model: TUIPlaySource) {
      handlePageChanged(index)
      playback.flushPendingStartCommand("onPageChanged")
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
    addView(
      subtitleView,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
    addView(
      topLoadingIndicator,
      LayoutParams(PixelHelper.px(64f), PixelHelper.px(64f)).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        topMargin = PixelHelper.px(40f)
      }
    )
    addView(
      bottomLoadingIndicator,
      LayoutParams(PixelHelper.px(64f), PixelHelper.px(64f)).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        bottomMargin = PixelHelper.px(40f)
      }
    )
    
    // 强制字幕视图置顶，使用极高的elevation确保在所有视图之上
    subtitleView.bringToFront()
    ViewCompat.setElevation(subtitleView, 999f)  // 极高的elevation
    subtitleView.z = 999f  // 同时设置z值
    
    // 监听布局变化，确保字幕视图始终在最顶层
    subtitleView.viewTreeObserver.addOnGlobalLayoutListener(subtitleLayoutListener)
    
    // 强制刷新视图层级
    post {
      subtitleView.bringToFront()
      invalidate()
      requestLayout()
    }
    updateTopLoadingVisibility()
    updateBottomLoadingVisibility()
    themedReactContext.addLifecycleEventListener(this)
    applyStrategies()
    shortVideoView.setListener(shortVideoListener)
    scrollBoundaryTracker.attach()
    scrollBoundaryTracker.reset()
  }

  private fun applyStrategies() {
    shortVideoView.setVodStrategy(customVodStrategy ?: createDefaultVodStrategyBuilder().build())
    shortVideoView.setLiveStrategy(customLiveStrategy ?: createDefaultLiveStrategyBuilder().build())
    applyRenderModeToCurrentPlayer()
  }

  fun setOverlayVisible(visible: Boolean) {
    if (overlayVisible == visible) {
      return
    }
    overlayVisible = visible
    hostAwareLayers.forEach { layer ->
      layer.onOverlayVisibilityChanged(visible)
    }
    updateTopLoadingVisibility()
    updateBottomLoadingVisibility()
  }

  fun setTopLoadingVisible(visible: Boolean) {
    if (topLoadingVisible == visible) {
      return
    }
    topLoadingVisible = visible
    if (!visible) {
      setTopLoadingPreviewVisible(false)
    }
    updateTopLoadingVisibility()
  }

  fun setBottomLoadingVisible(visible: Boolean) {
    if (bottomLoadingVisible == visible) {
      return
    }
    bottomLoadingVisible = visible
    if (!visible) {
      setBottomLoadingPreviewVisible(false)
    }
    updateBottomLoadingVisibility()
  }

  private fun createDefaultVodStrategyBuilder(): TUIPlayerVodStrategy.Builder {
    return TUIPlayerVodStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(mapToNativeRenderMode(requestedRenderMode))
  }

  private fun createDefaultLiveStrategyBuilder(): TUIPlayerLiveStrategy.Builder {
    return TUIPlayerLiveStrategy.Builder()
      .setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyNext)
      .setRenderMode(TUIConstants.TUIRenderMode.ADJUST_RESOLUTION)
  }

  private fun updateTopLoadingVisibility() {
    val shouldShow = overlayVisible && (topLoadingVisible || topLoadingPreview)
    topLoadingIndicator.visibility = if (shouldShow) View.VISIBLE else View.GONE
  }

  private fun updateBottomLoadingVisibility() {
    val shouldShow = overlayVisible && (bottomLoadingVisible || bottomLoadingPreview)
    bottomLoadingIndicator.visibility = if (shouldShow) View.VISIBLE else View.GONE
  }

  private fun setTopLoadingPreviewVisible(visible: Boolean) {
    if (topLoadingVisible) {
      return
    }
    if (topLoadingPreview == visible) {
      return
    }
    topLoadingPreview = visible
    updateTopLoadingVisibility()
  }

  private fun setBottomLoadingPreviewVisible(visible: Boolean) {
    if (bottomLoadingVisible) {
      return
    }
    if (bottomLoadingPreview == visible) {
      return
    }
    bottomLoadingPreview = visible
    updateBottomLoadingVisibility()
  }

  private fun createLoadingIndicator(): ProgressBar {
    val color = Color.parseColor("#FF782E")
    return ProgressBar(context).apply {
      isIndeterminate = true
      isClickable = false
      isFocusable = false
      visibility = View.GONE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
      indeterminateDrawable?.let { drawable ->
        DrawableCompat.setTint(drawable, color)
      }
      ViewCompat.setElevation(this, 4f)
    }
  }

  private fun setOverDragOffset(offset: Float, animate: Boolean) {
    val target = offset.coerceAtLeast(0f)
    if (!animate) {
      overDragAnimator?.cancel()
      updateOverDragTranslation(target)
      return
    }
    if (overDragOffset == target) {
      return
    }
    overDragAnimator?.cancel()
    overDragAnimator = ValueAnimator.ofFloat(overDragOffset, target).apply {
      duration = 200L
      addUpdateListener { animator ->
        val value = animator.animatedValue as Float
        updateOverDragTranslation(value)
      }
      start()
    }
  }

  private fun updateOverDragTranslation(value: Float) {
    overDragOffset = value
    shortVideoView.translationY = value
    subtitleView.translationY = value
    topLoadingIndicator.translationY = value
    bottomLoadingIndicator.translationY = value
  }

  fun setAutoPlay(value: Boolean) {
    autoPlay = value
    if (!autoPlay) {
      shortVideoView.pause(true)
      notifyHostLayersPaused(true)
      playback.discardNonForcedPendingStart()
      appWasPlayingBeforePause = false
    } else if (!isManuallyPaused) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      playback.flushPendingStartCommand("setAutoPlay")
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
        playback.flushPendingStartCommand("setPaused.unchanged")
      }
      return
    }
    isManuallyPaused = value
    if (value) {
      shortVideoView.pause()
      currentVodPlayer?.pause()
      notifyHostLayersPaused(true)
      appWasPlayingBeforePause = false
      playback.discardNonForcedPendingStart()
    } else if (autoPlay) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      playback.flushPendingStartCommand("setPaused")
      appWasPlayingBeforePause = false
    } else {
      notifyHostLayersPaused(true)
    }
  }

  fun setInitialIndex(value: Int) {
    playback.setInitialIndex(value)
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
    if (config == null) {
      updateRequestedRenderMode(RENDER_MODE_FILL)
    }
    customVodStrategy = config?.let { buildVodStrategy(it) }
    applyStrategies()
  }

  fun setLiveStrategy(config: ReadableMap?) {
    customLiveStrategy = config?.let { buildLiveStrategy(it) }
    applyStrategies()
  }

  fun setSubtitleStyle(config: ReadableMap?) {
    subtitleStyle = config?.toSubtitleStyleConfig()
    applySubtitleStyleToPlayers()
  }

  fun setSources(sources: List<TuiplayerShortVideoSource>) {
    isReleased = false
    resetScrollListener()
    playlist.setSources(sources)
  }

  fun appendSources(sources: List<TuiplayerShortVideoSource>) {
    resetScrollListener()
    playlist.appendSources(sources)
  }

  private fun resetScrollListener() {
    scrollBoundaryTracker.attach()
    scrollBoundaryTracker.reset()
  }

  override fun onPlaylistStateReset() {
    playback.reset()
    playersByIndex.clear()
    playerIndexLookup.clear()
    subtitleAttachmentSources.clear()
    playersRequiringSubtitles.clear()
    subtitleSelectionAttempts.clear()
    currentVisibleIndex = null
    detachCurrentVodPlayer()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isReleased = false
    bindLifecycle()
    shortVideoView.setUserInputEnabled(userInputEnabled)
    applyPageScroller()
    scrollBoundaryTracker.attach()
    currentVodPlayer?.let { bindSubtitleView(it, "onAttachedToWindow") }
    maybeMarkViewReady()
    playback.flushPendingStartCommand("onAttachedToWindow")
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    shortVideoView.pause()
    notifyHostLayersPaused(true)
    playback.cancelPendingRetry()
    scrollBoundaryTracker.detach()
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

      playlist.onNativeReady()
      dispatchReadyEvent()
      playback.flushPendingStartCommand("maybeMarkViewReady")
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
    playlist.reset()
    playersByIndex.clear()
    playerIndexLookup.clear()
    subtitleAttachmentSources.clear()
    currentVisibleIndex = null
    requestedRenderMode = RENDER_MODE_FILL
    lastKnownIndex = -1
    lastEndReachedTotal = -1
    overlayVisible = true
    topLoadingVisible = false
    bottomLoadingVisible = false
    topLoadingPreview = false
    bottomLoadingPreview = false
    setOverDragOffset(0f, animate = false)
    overDragAnimator?.cancel()
    overDragAnimator = null
    updateTopLoadingVisibility()
    updateBottomLoadingVisibility()
    scrollBoundaryTracker.detach()
    scrollBoundaryTracker.reset()
  }

  override fun onHostResume() {
    bindLifecycle()
    if (appWasPlayingBeforePause && autoPlay && !isManuallyPaused) {
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      currentVodPlayer?.let { bindSubtitleView(it, "onHostResume") }
      playback.flushPendingStartCommand("onHostResume")
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

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    scrollBoundaryTracker.onTouchEvent(ev)
    return super.dispatchTouchEvent(ev)
  }

  private fun handlePageChanged(index: Int) {
    val total = playlist.dataCount()
    if (total <= 0) {
      return
    }
    currentVisibleIndex = index
    if (index != lastKnownIndex) {
      if (isManuallyPaused) {
        isManuallyPaused = false
      }
      lastKnownIndex = index
      dispatchPageChanged(index, total)
    }
    ensureActivePlayerForCurrentIndex()
    val remaining = total - index - 1
    if (remaining <= END_REACHED_THRESHOLD) {
      dispatchEndReached(index, total)
    }
  }

  override fun dispatchPageChanged(index: Int, total: Int) {
    dispatchPageChangedInternal(index, total, 0)
  }

  override fun dispatchEndReachedIfNeeded(index: Int, total: Int) {
    dispatchEndReached(index, total)
  }

  private fun dispatchEndReached(index: Int, total: Int) {
    if (total <= 0) {
      return
    }
    if (total == lastEndReachedTotal) {
      return
    }
    lastEndReachedTotal = total
    if (!bottomLoadingVisible) {
      setBottomLoadingPreviewVisible(true)
    }
    dispatchEndReachedInternal(index, total, 0)
  }

  private fun ensureActivePlayerForCurrentIndex() {
    val targetIndex = currentVisibleIndex ?: return
    val activePlayerIndex = currentVodPlayer?.let { playerIndexLookup[it] }
    if (currentVodPlayer != null) {
      if (activePlayerIndex == targetIndex) {
        return
      }
      if (activePlayerIndex != null && activePlayerIndex != targetIndex) {
        detachCurrentVodPlayer()
      } else {
        return
      }
    }
    val candidate = resolvePlayerForIndex(targetIndex)
    if (candidate != null) {
      attachCurrentVodPlayer(candidate)
    }
  }

  companion object {
    private const val TAG = "TuiplayerShortVideoView"
    private const val SUBTITLE_SELECTION_DELAY_MS = 300L
    private const val MAX_SUBTITLE_SELECTION_ATTEMPTS = 5
    private const val END_REACHED_THRESHOLD = 2
    private const val TOP_REACHED_THRESHOLD = 2
    private const val MAX_EVENT_RETRY = 5
    private const val MAX_START_RETRY = 5
    private const val INITIAL_PLAYBACK_RETRY_DELAY_MS = 120L
    const val RENDER_MODE_FILL = 0
    const val RENDER_MODE_FIT = 1
  }

  fun commandStartPlayIndex(index: Int, smooth: Boolean) {
    if (playback.tryStartAtIndex(index, smooth, forcePlayOverride = true)) {

      return
    }
    playback.setPendingStart(index, smooth, "commandStartPlayIndex", force = true)
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
    if (isManuallyPaused) {
        isManuallyPaused = false
    }
    if (autoPlay && !isManuallyPaused) {
      if (currentIndex != null) {
        if (!playback.tryStartAtIndex(currentIndex, false, forcePlayOverride = true)) {
          playback.setPendingStart(currentIndex, false, "commandSyncPlaybackState", force = true)
        }
      }
      shortVideoView.resume()
      notifyHostLayersPaused(false)
      playback.flushPendingStartCommand("commandSyncPlaybackState")
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

  fun commandSetOverlayVisible(visible: Boolean) {
    setOverlayVisible(visible)
  }

  fun commandSetTopLoadingVisible(visible: Boolean) {
    setTopLoadingVisible(visible)
  }

  fun commandSetBottomLoadingVisible(visible: Boolean) {
    setBottomLoadingVisible(visible)
  }

  fun handleVodPlayerCommand(command: String, options: ReadableMap?): Any? {
    val existingPlayer = resolvePlayerForCommand(options)
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
        val normalized = normalizeRenderMode(mode)
        updateRequestedRenderMode(normalized)
        player.setRenderMode(mapToNativeRenderMode(normalized))
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
    playlist.addData(source, index)
  }

  fun addRangeData(sources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    playlist.addRangeData(sources, startIndex)
  }

  fun replaceData(source: TuiplayerShortVideoSource, index: Int) {
    playlist.replaceData(source, index)
  }

  fun updateMetadata(index: Int, metadata: TuiplayerShortVideoSource.Metadata?) {
    playlist.updateMetadata(index, metadata)
  }

  fun replaceRangeData(sources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    playlist.replaceRangeData(sources, startIndex)
  }

  fun removeData(index: Int) {
    playlist.removeData(index)
  }

  fun removeRangeData(index: Int, count: Int) {
    playlist.removeRangeData(index, count)
  }

  fun removeDataByIndexes(indexes: List<Int>) {
    playlist.removeDataByIndexes(indexes)
  }

  fun getSourceSnapshotAt(index: Int): WritableMap? {
    return playlist.snapshotAt(index)
  }

  fun getDataCount(): Int {
    return playlist.dataCount()
  }

  override fun dataManagerOrNull(): TUIShortVideoDataManager? {
    return shortVideoView.dataManager
  }

  override fun resolveSource(model: TUIVideoSource): TuiplayerShortVideoSource? {
    return playlist.findSourceMatch(model)?.second
  }

  override fun resolveIndex(model: TUIVideoSource): Int? {
    return playlist.findSourceMatch(model)?.first
  }

  override fun emitOverlayAction(model: TUIVideoSource, action: String) {
    val payload = Arguments.createMap().apply { putString("action", action) }

    var resolvedIndex = -1
    var resolvedSnapshot: WritableMap? = null

    playlist.findSourceMatch(model)?.let { (index, source) ->
      resolvedIndex = index
      resolvedSnapshot = source.toSnapshotMap()
    }

    if (resolvedIndex < 0) {
      val fallbackIndex = playlist.currentIndex() ?: lastKnownIndex
      val snapshot = fallbackIndex?.let { playlist.snapshotAt(it) }
      if (fallbackIndex != null && snapshot != null) {
        resolvedIndex = fallbackIndex
        resolvedSnapshot = snapshot
      }
    }

    payload.putInt("index", resolvedIndex)
    resolvedSnapshot?.let { payload.putMap("source", it) }

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

  private fun registerHostLayer(layer: TuiplayerHostAwareLayer) {
    layer.attachHost(this)
    hostAwareLayers.add(layer)
    layer.onPlaybackStateChanged(isPlaybackPaused())
    layer.onOverlayVisibilityChanged(overlayVisible)
  }

  private fun notifyHostLayersPaused(paused: Boolean) {
    hostAwareLayers.forEach { hostLayer ->
      hostLayer.onPlaybackStateChanged(paused)
    }
  }

  private fun isPlaybackPaused(): Boolean {
    return isManuallyPaused || !autoPlay || isReleased
  }

  private fun attachCurrentVodPlayer(player: ITUIVodPlayer) {
    if (currentVodPlayer === player) {
      return
    }
    detachCurrentVodPlayer()
    currentVodPlayer = player
    player.addPlayerObserver(vodObserver)
    player.setRenderMode(mapToNativeRenderMode(requestedRenderMode))
    bindSubtitleView(player, "attachCurrentVodPlayer")
    maybeSelectSubtitleTrack(player)
  }

  private fun detachCurrentVodPlayer() {
    currentVodPlayer?.removePlayerObserver(vodObserver)
    currentVodPlayer?.let {
      playersRequiringSubtitles.remove(it)
      subtitleSelectionAttempts.remove(it)
      subtitleAttachmentSources.remove(it)
    }
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
    return playlist.currentIndex()
  }

  fun getCurrentSourceSnapshot(): WritableMap? {
    return playlist.currentSourceSnapshot()
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

  private fun normalizeRenderMode(value: Int?): Int {
    return when (value) {
      RENDER_MODE_FILL, RENDER_MODE_FIT -> value
      else -> RENDER_MODE_FILL
    } ?: RENDER_MODE_FILL
  }

  private fun mapToNativeRenderMode(mode: Int): Int {
    return if (mode == RENDER_MODE_FIT) {
      TUIConstants.TUIRenderMode.ADJUST_RESOLUTION
    } else {
      TUIConstants.TUIRenderMode.FULL_FILL_SCREEN
    }
  }

  private fun updateRequestedRenderMode(mode: Int) {
    if (requestedRenderMode == mode) {
      return
    }
    requestedRenderMode = mode
    applyRenderModeToCurrentPlayer()
  }

  private fun applyRenderModeToCurrentPlayer() {
    currentVodPlayer?.setRenderMode(mapToNativeRenderMode(requestedRenderMode))
  }

  private inner class PlaybackCoordinator : PlaylistPlaybackCoordinator {
    private var pendingInitialIndex: Int? = null
    private var pendingStartCommand: PendingStartCommand? = null
    private var pendingStartRetry: Runnable? = null
    private var dataCountProvider: (() -> Int)? = null

    fun bindDataCountProvider(provider: () -> Int) {
      dataCountProvider = provider
    }

    fun setInitialIndex(value: Int) {
      pendingInitialIndex = value.takeIf { it >= 0 }
      maybeApplyInitialIndex()
    }

    fun discardNonForcedPendingStart() {
      val pending = pendingStartCommand
      if (pending != null && !pending.forcePlay) {
        pendingStartCommand = null
        cancelPendingRetry()
      }
    }

    fun cancelPendingRetry() {
      pendingStartRetry?.let { shortVideoView.removeCallbacks(it) }
      pendingStartRetry = null
    }

    fun reset() {
      pendingInitialIndex = null
      pendingStartCommand = null
      cancelPendingRetry()
    }

    fun tryStartAtIndex(index: Int, smooth: Boolean, forcePlayOverride: Boolean? = null): Boolean {
      if (isReleased || !isViewReady) {
        return false
      }
      val total = dataCount()
      if (index !in 0 until total) {
        return false
      }
      if (isManuallyPaused && lastKnownIndex >= 0 && index != lastKnownIndex) {
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
        return false
      }
      shortVideoView.startPlayIndex(index, smooth)
      if (forcePlay || (autoPlay && !isManuallyPaused)) {
        isManuallyPaused = false
        shortVideoView.resume()
        notifyHostLayersPaused(false)
      } else {
        notifyHostLayersPaused(true)
      }
      handlePageChanged(index)
      pendingStartCommand = null
      cancelPendingRetry()
      return true
    }

    override fun maybeApplyInitialIndex(): Boolean {
      val total = dataCount()
    val target = pendingInitialIndex?.takeIf { total > 0 }?.coerceIn(0, total - 1)
    if (target != null) {
      scheduleStartAtIndex(target, smooth = false, force = false)
      currentVisibleIndex = target
      lastKnownIndex = target
      dispatchPageChanged(target, total)
        if (!hasActiveVodPlayer()) {
          ensureInitialPlayback(target, 1)
        }
        pendingInitialIndex = null
        return true
      }
      return false
    }

    override fun ensureInitialPlayback(index: Int, attempt: Int) {
      if (attempt > MAX_START_RETRY) {
        return
      }
      shortVideoView.postDelayed({
        if (isReleased || currentVodPlayer != null) {
          return@postDelayed
        }
        val total = dataCount()
        if (total == 0 || index !in 0 until total) {
          return@postDelayed
        }
        if (!tryStartAtIndex(index, false)) {
          ensureInitialPlayback(index, attempt + 1)
        }
      }, INITIAL_PLAYBACK_RETRY_DELAY_MS)
    }

    override fun scheduleStartAtIndex(index: Int, smooth: Boolean, force: Boolean) {
      if (tryStartAtIndex(index, smooth, forcePlayOverride = if (force) true else null)) {

        return
      }
      val existing = pendingStartCommand
      if (!force && existing != null && existing.forcePlay && existing.index != index) {

        return
      }
      val shouldForce = force || existing?.forcePlay == true
      setPendingStart(index, smooth, "scheduleStartAtIndex", force = shouldForce)
    }

    override fun setPendingStart(
      index: Int,
      smooth: Boolean,
      reason: String,
      force: Boolean,
      autoFlush: Boolean
    ) {
      val latestSource = shortVideoView.currentModel
      pendingStartCommand = PendingStartCommand(index, smooth, force)
      if (autoFlush) {
        flushPendingStartCommand(reason)
      }
    }

    override fun flushPendingStartCommand(reason: String) {
      val pending = pendingStartCommand ?: return
      if (tryStartAtIndex(pending.index, pending.smooth)) {

      } else {

        schedulePendingStartRetry(reason)
      }
    }

    override fun hasPendingStartCommand(): Boolean {
      return pendingStartCommand != null
    }

    override fun hasActiveVodPlayer(): Boolean {
      return currentVodPlayer != null
    }

    override fun detachCurrentVodPlayer() {
      this@TuiplayerShortVideoView.detachCurrentVodPlayer()
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

    private fun dataCount(): Int {
      return dataCountProvider?.invoke() ?: 0
    }

  }

  override fun notifyOverlayUpdated(index: Int, source: TuiplayerShortVideoSource) {
    hostAwareLayers.forEach { layer ->
      layer.onMetadataUpdated(source)
    }
  }

  override fun notifyPlaylistItemChanged(index: Int) {
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

  private inner class ScrollBoundaryTracker(
    private val view: TUIShortVideoView,
    private val touchSlop: Int,
    private val onTopReached: (Int) -> Unit
  ) {
    private var hasDispatchedTopReached = false
    private var lastKnownScrollOffset = 0
    private var topDragPointerId = MotionEvent.INVALID_POINTER_ID
    private var topDragInitialY = 0f
    private var topDragTriggered = false
    private var downStartedAtTop = false
    private val pullPreviewThresholdPx = PixelHelper.pxF(40f)
    private val pullTriggerThresholdPx = PixelHelper.pxF(440f)
    private val maxOverDragOffsetPx = PixelHelper.pxF(200f)
    private val bottomPreviewThresholdPx = PixelHelper.px(80f)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
      val offset = view.computeVerticalScrollOffset()
      val delta = offset - lastKnownScrollOffset
      lastKnownScrollOffset = offset
      handleScrollBoundaries(offset, delta)
    }

    fun attach() {
      view.viewTreeObserver.apply {
        if (isAlive) {
          removeOnScrollChangedListener(scrollListener)
          addOnScrollChangedListener(scrollListener)
        }
      }
      lastKnownScrollOffset = view.computeVerticalScrollOffset()
    }

    fun detach() {
      val observer = view.viewTreeObserver
      if (observer.isAlive) {
        observer.removeOnScrollChangedListener(scrollListener)
      }
    }

    fun reset() {
      hasDispatchedTopReached = false
      lastKnownScrollOffset = view.computeVerticalScrollOffset()
      topDragPointerId = MotionEvent.INVALID_POINTER_ID
      topDragTriggered = false
      downStartedAtTop = false
      if (!topLoadingVisible) {
        setTopLoadingPreviewVisible(false)
      }
      if (!bottomLoadingVisible) {
        setBottomLoadingPreviewVisible(false)
      }
      setOverDragOffset(0f, animate = true)
    }

    fun onTouchEvent(ev: MotionEvent) {
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          topDragPointerId = ev.getPointerId(0)
          topDragInitialY = ev.getY(0)
          topDragTriggered = false
          hasDispatchedTopReached = false
          downStartedAtTop = !view.canScrollVertically(-1)
          if (!downStartedAtTop && !topLoadingVisible) {
            setTopLoadingPreviewVisible(false)
          }
          setOverDragOffset(0f, animate = true)
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
          if (topDragPointerId == MotionEvent.INVALID_POINTER_ID) {
            val index = ev.actionIndex
            topDragPointerId = ev.getPointerId(index)
            topDragInitialY = ev.getY(index)
            topDragTriggered = false
            downStartedAtTop = !view.canScrollVertically(-1)
            if (!downStartedAtTop && !topLoadingVisible) {
              setTopLoadingPreviewVisible(false)
            }
            setOverDragOffset(0f, animate = true)
          }
        }
        MotionEvent.ACTION_MOVE -> {
          val pointerId = topDragPointerId
          if (pointerId != MotionEvent.INVALID_POINTER_ID) {
            val pointerIndex = ev.findPointerIndex(pointerId)
            if (pointerIndex != -1) {
              val currentY = ev.getY(pointerIndex)
              val dy = currentY - topDragInitialY
              if (!downStartedAtTop) {
                downStartedAtTop = !view.canScrollVertically(-1)
                topDragInitialY = currentY
                if (!downStartedAtTop) {
                  setOverDragOffset(0f, animate = true)
                }
              }
              if (!downStartedAtTop || dy <= touchSlop) {
                topDragTriggered = false
                if (!topLoadingVisible) {
                  setTopLoadingPreviewVisible(false)
                }
                setOverDragOffset(0f, animate = true)
                return
              }
              if (view.canScrollVertically(-1)) {
                downStartedAtTop = false
                topDragTriggered = false
                if (!topLoadingVisible) {
                  setTopLoadingPreviewVisible(false)
                }
                setOverDragOffset(0f, animate = true)
                return
              }
              if (dy <= 0f) {
                topDragTriggered = false
                if (!topLoadingVisible) {
                  setTopLoadingPreviewVisible(false)
                }
                setOverDragOffset(0f, animate = true)
              } else {
                val dragOffset = (dy * 0.5f).coerceAtMost(maxOverDragOffsetPx)
                setOverDragOffset(dragOffset, animate = false)
                if (!topLoadingVisible) {
                  setTopLoadingPreviewVisible(dy >= pullPreviewThresholdPx)
                }
                topDragTriggered = dy >= pullTriggerThresholdPx
              }
            }
          }
        }
        MotionEvent.ACTION_POINTER_UP -> {
          val pointerId = ev.getPointerId(ev.actionIndex)
          if (pointerId == topDragPointerId) {
            val newIndex = if (ev.actionIndex == 0 && ev.pointerCount > 1) 1 else 0
            topDragPointerId = if (newIndex < ev.pointerCount) ev.getPointerId(newIndex) else MotionEvent.INVALID_POINTER_ID
            topDragInitialY = if (newIndex < ev.pointerCount) ev.getY(newIndex) else 0f
            topDragTriggered = false
            downStartedAtTop = !view.canScrollVertically(-1)
            if (!topLoadingVisible) {
              setTopLoadingPreviewVisible(false)
            }
            setOverDragOffset(0f, animate = true)
          }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (topDragTriggered && downStartedAtTop) {
            val offset = view.computeVerticalScrollOffset()
            lastKnownScrollOffset = offset
            onTopReached(offset)
            hasDispatchedTopReached = true
          }
          topDragPointerId = MotionEvent.INVALID_POINTER_ID
          topDragTriggered = false
          downStartedAtTop = false
          if (!topLoadingVisible) {
            setTopLoadingPreviewVisible(false)
          }
          setOverDragOffset(0f, animate = true)
        }
      }
    }

    private fun handleScrollBoundaries(offset: Int, deltaY: Int) {
      val extent = view.computeVerticalScrollExtent()
      val range = view.computeVerticalScrollRange()
      val atTop = offset <= TOP_REACHED_THRESHOLD || range <= extent
      val remaining = (range - extent - offset).coerceAtLeast(0)
      val nearBottom = remaining <= bottomPreviewThresholdPx

      if (deltaY >= 0) {
        if (!atTop && hasDispatchedTopReached) {
          hasDispatchedTopReached = false
        }
        if (!bottomLoadingVisible) {
          setBottomLoadingPreviewVisible(false)
        }
        return
      }

      if (!atTop) {
        hasDispatchedTopReached = false
      }

      if (!bottomLoadingVisible) {
        if (nearBottom && deltaY < 0) {
          setBottomLoadingPreviewVisible(true)
        } else if (!nearBottom) {
          setBottomLoadingPreviewVisible(false)
        }
      }
    }
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

  private fun dispatchTopReachedInternal(offset: Int, retry: Int) {
    if (id == View.NO_ID) {
      if (retry >= MAX_EVENT_RETRY) {
        Log.w(TAG, "Abandon topReached event, view id not ready")
        return
      }
      shortVideoView.post { dispatchTopReachedInternal(offset, retry + 1) }
      return
    }
    val dispatcher = resolveEventDispatcher()
    if (dispatcher != null) {
      emitTopReached(dispatcher, offset)
    } else if (retry < MAX_EVENT_RETRY) {
      shortVideoView.post { dispatchTopReachedInternal(offset, retry + 1) }
    } else {
      Log.w(
        TAG,
        "Failed to dispatch topReached after retries (surfaceId=${resolveSurfaceId()}, tag=$id)"
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

  private fun emitTopReached(dispatcher: EventDispatcher, offset: Int) {
    val surfaceId = resolveSurfaceId()
    dispatcher.dispatchEvent(
      TuiplayerShortVideoTopReachedEvent(surfaceId, id, offset)
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
    val customRenderMode = config.getIntOrNull("renderMode")
    if (customRenderMode != null) {
      val normalized = normalizeRenderMode(customRenderMode)
      builder.setRenderMode(mapToNativeRenderMode(normalized))
      updateRequestedRenderMode(normalized)
    } else {
      builder.setRenderMode(mapToNativeRenderMode(requestedRenderMode))
    }
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

  private fun attachSubtitles(
    player: ITUIVodPlayer,
    source: TuiplayerShortVideoSource?
  ) {

    if (source == null) {
      subtitleAttachmentSources.remove(player)
      return
    }
    val subtitles = source.subtitles?.takeIf { it.isNotEmpty() } ?: run {
      subtitleAttachmentSources.remove(player)
      return
    }
    val previousSource = subtitleAttachmentSources[player]?.get()
    if (previousSource === source) {
      return
    }
    subtitleAttachmentSources[player] = WeakReference(source)
    var added = false
    subtitles.forEach { subtitle ->
      val url = subtitle.url
      if (url.isBlank()) {
        return@forEach
      }
      val name = subtitle.name?.takeIf { it.isNotBlank() } ?: url
      val mimeType = subtitle.normalizedMimeType()
      try {

        // 实际测试发现：即使传 (name, url, mimeType)，SDK仍收到颠倒的参数
        // 改回原始顺序，让SDK的JNI层去调换
        player.addSubtitleSource(url, name, mimeType)

        added = true
      } catch (error: Throwable) {
        Log.w(TAG, "Failed to add subtitle source url=$url", error)
      }
    }
    if (!added && currentVodPlayer === player) {

      bindSubtitleView(player, "attachSubtitlesFallback")
    }
    if (added) {
      playersRequiringSubtitles.add(player)
      subtitleSelectionAttempts.remove(player)
      maybeSelectSubtitleTrack(player)
      if (currentVodPlayer === player) {

        bindSubtitleView(player, "attachSubtitlesSuccess")
      }
    }
  }

  private fun registerPlayerForIndex(
    player: ITUIVodPlayer,
    index: Int,
    source: TuiplayerShortVideoSource?
  ) {
    val previousIndex = playerIndexLookup[player]
    if (previousIndex != null && previousIndex != index) {
      val existingRef = playersByIndex[previousIndex]
      if (existingRef?.get() === player) {
        playersByIndex.remove(previousIndex)
      }
    }
    playersByIndex[index] = WeakReference(player)
    playerIndexLookup[player] = index
    attachSubtitles(player, source)
    val hasSubtitles = source?.subtitles?.isNotEmpty() == true
    if (!hasSubtitles) {
      playersRequiringSubtitles.remove(player)
      subtitleSelectionAttempts.remove(player)
    }
  }

  private fun maybeSelectSubtitleTrack(player: ITUIVodPlayer) {
    if (!playersRequiringSubtitles.contains(player) || isReleased) {

      return
    }
    if (trySelectSubtitleTrack(player)) {

      subtitleSelectionAttempts.remove(player)
      return
    }
    val attempt = (subtitleSelectionAttempts[player] ?: 0)
    if (attempt >= MAX_SUBTITLE_SELECTION_ATTEMPTS) {
      return
    }
    subtitleSelectionAttempts[player] = attempt + 1

    shortVideoView.postDelayed(
      { maybeSelectSubtitleTrack(player) },
      SUBTITLE_SELECTION_DELAY_MS * (attempt + 1)
    )
  }

  private fun trySelectSubtitleTrack(player: ITUIVodPlayer): Boolean {
    val tracks = try {
      player.subtitleTrackInfo
    } catch (error: Throwable) {
      Log.w(TAG, "trySelectSubtitleTrack failed to obtain tracks", error)
      null
    } ?: return false

    val target = tracks.firstOrNull {
      it.trackType == TXTrackInfo.TX_VOD_MEDIA_TRACK_TYPE_SUBTITLE
    } ?: return false
    return try {
      if (player === currentVodPlayer) {
        bindSubtitleView(player, "trySelectSubtitleTrack")
      }
      player.selectTrack(target.trackIndex)

      true
    } catch (error: Throwable) {
      Log.w(TAG, "selectTrack failed", error)
      false
    }
  }

  private fun applySubtitleStyleToPlayers() {
    val players = mutableSetOf<ITUIVodPlayer>()
    currentVodPlayer?.let { players.add(it) }
    playersByIndex.values.forEach { reference ->
      reference.get()?.let { players.add(it) }
    }
    if (players.isEmpty()) {
      return
    }
    players.forEach { applySubtitleStyleToPlayer(it) }
  }

  private fun applySubtitleStyleToPlayer(player: ITUIVodPlayer) {
    val renderModel = subtitleStyle?.toRenderModel() ?: defaultSubtitleRenderModel()
    player.setSubtitleStyle(renderModel)
  }

  private fun bindSubtitleView(player: ITUIVodPlayer, reason: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      try {
        player.setSubtitleView(subtitleView)

      } catch (error: Throwable) {
        Log.w(TAG, "bindSubtitleView failed reason=$reason", error)
      }
    } else {
      mainHandler.post { bindSubtitleView(player, reason) }
    }
  }

  private fun defaultSubtitleRenderModel(): TXSubtitleRenderModel {
    // Fallback 样式，保证文本可见
    return TXSubtitleRenderModel().apply {
      canvasWidth = if (measuredWidth > 0) measuredWidth else 1080
      canvasHeight = if (measuredHeight > 0) measuredHeight else 2376
      // 增大字体确保可见
      fontSize = 48f  // 从36增加到48
      fontScale = 1.0f
      // 纯白色文字
      fontColor = -1  // 0xFFFFFFFF
      // 粗黑色描边，确保对比度
      outlineWidth = 6f  // 从4增加到6
      outlineColor = -16777216  // 0xFF000000
      // 加粗字体
      isBondFontStyle = true
      lineSpace = 8f
      // 边距设置
      startMargin = 20f
      endMargin = 20f
      verticalMargin = 50f
    }
  }

  private fun requestSubtitleSelectionForAllPlayers() {
    currentVodPlayer?.let { maybeSelectSubtitleTrack(it) }
    playersByIndex.values.forEach { ref ->
      ref.get()?.let { maybeSelectSubtitleTrack(it) }
    }
  }

  private fun resolvePlayerForCommand(options: ReadableMap?): ITUIVodPlayer? {
    val requestedIndex = options?.getIntOrNull("index")
    val targetIndex = requestedIndex ?: currentVisibleIndex
    val player = resolvePlayerForIndex(targetIndex)
    if (player != null) {
      return player
    }
    if (targetIndex == null) {
      return currentVodPlayer
    }
    return null
  }

  private fun resolvePlayerForIndex(index: Int?): ITUIVodPlayer? {
    if (index == null || index < 0) {
      return null
    }
    val reference = playersByIndex[index]
    val player = reference?.get()
    if (player == null && reference != null) {
      playersByIndex.remove(index)
    }
    return player
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
    } catch (error: Throwable) {
      Log.w(TAG, "instantiateLayer failed for $className", error)
      null
    }
  }
}

private data class PendingStartCommand(
  val index: Int,
  val smooth: Boolean,
  val forcePlay: Boolean
)

private data class SubtitleStyleConfig(
  val canvasWidth: Int?,
  val canvasHeight: Int?,
  val familyName: String?,
  val fontSize: Double?,
  val fontScale: Double?,
  val fontColor: Int?,
  val bold: Boolean?,
  val outlineWidth: Double?,
  val outlineColor: Int?,
  val lineSpace: Double?,
  val startMargin: Double?,
  val endMargin: Double?,
  val verticalMargin: Double?
) {
  val hasValue: Boolean =
    listOf(
      canvasWidth,
      canvasHeight,
      familyName,
      fontSize,
      fontScale,
      fontColor,
      bold,
      outlineWidth,
      outlineColor,
      lineSpace,
      startMargin,
      endMargin,
      verticalMargin
    ).any { value ->
      when (value) {
        is String -> value.isNotBlank()
        else -> value != null
      }
    }

  fun toRenderModel(): TXSubtitleRenderModel {
    val model = TXSubtitleRenderModel()
    canvasWidth?.let { model.canvasWidth = it }
    canvasHeight?.let { model.canvasHeight = it }
    familyName?.takeIf { it.isNotBlank() }?.let { model.familyName = it }
    fontSize?.let { model.fontSize = it.toFloat() }
    fontScale?.let { model.fontScale = it.toFloat() }
    fontColor?.let { model.fontColor = it }
    bold?.let { model.isBondFontStyle = it }
    outlineWidth?.let { model.outlineWidth = it.toFloat() }
    outlineColor?.let { model.outlineColor = it }
    lineSpace?.let { model.lineSpace = it.toFloat() }
    startMargin?.let { model.startMargin = it.toFloat() }
    endMargin?.let { model.endMargin = it.toFloat() }
    verticalMargin?.let { model.verticalMargin = it.toFloat() }
    return model
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

private fun ReadableMap.getArrayOrNull(key: String): ReadableArray? {
  return if (hasKey(key) && !isNull(key)) getArray(key) else null
}

private fun ReadableMap.getTagList(key: String): List<String>? {
  if (!hasKey(key) || isNull(key)) {
    return null
  }
  return when (getType(key)) {
    ReadableType.Array -> getArray(key)?.toStringList()?.takeIf { it.isNotEmpty() }
    ReadableType.String -> parseTagString(getString(key))
    else -> null
  }
}

private fun parseTagString(value: String?): List<String>? {
  if (value.isNullOrBlank()) {
    return null
  }
  val parts = value.split(Regex("[#|/、,，\\s]+"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
  return if (parts.isEmpty()) null else parts
}

internal fun TUIPlaySource.toWritableMap(): WritableMap {
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
  val subtitles = getArrayOrNull("subtitles")?.toSubtitleList()
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
    metadata = metadata,
    subtitles = subtitles
  )
}

private fun ReadableMap.toSubtitleStyleConfig(): SubtitleStyleConfig? {
  val config = SubtitleStyleConfig(
    canvasWidth = getIntOrNull("canvasWidth"),
    canvasHeight = getIntOrNull("canvasHeight"),
    familyName = getStringOrNull("familyName"),
    fontSize = getDoubleOrNull("fontSize"),
    fontScale = getDoubleOrNull("fontScale"),
    fontColor = getDoubleOrNull("fontColor")?.toInt(),
    bold = getBooleanOrNull("bold"),
    outlineWidth = getDoubleOrNull("outlineWidth"),
    outlineColor = getDoubleOrNull("outlineColor")?.toInt(),
    lineSpace = getDoubleOrNull("lineSpace"),
    startMargin = getDoubleOrNull("startMargin"),
    endMargin = getDoubleOrNull("endMargin"),
    verticalMargin = getDoubleOrNull("verticalMargin")
  )
  return config.takeIf { it.hasValue }
}

private fun ReadableArray.toSubtitleList(): List<TuiplayerShortVideoSource.Subtitle> {
  val result = mutableListOf<TuiplayerShortVideoSource.Subtitle>()
  for (index in 0 until size()) {
    val map = getMap(index) ?: continue
    val url = map.getStringOrNull("url") ?: continue
    val name = map.getStringOrNull("name")
    val mimeType = map.getStringOrNull("mimeType")
    result.add(TuiplayerShortVideoSource.Subtitle(name, url, mimeType))
  }
  return result
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

internal fun TuiplayerShortVideoSource.toSnapshotMap(): WritableMap {
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
  subtitles?.takeIf { it.isNotEmpty() }?.let { entries ->
    val array = Arguments.createArray()
    entries.forEach { entry ->
      val subtitleMap = Arguments.createMap()
      subtitleMap.putString("url", entry.url)
      entry.name?.takeIf { it.isNotBlank() }?.let { subtitleMap.putString("name", it) }
      subtitleMap.putString("mimeType", entry.normalizedMimeType())
      array.pushMap(subtitleMap)
    }
    map.putArray("subtitles", array)
  }
  return map
}

private fun ReadableMap.toShortVideoMetadata(): TuiplayerShortVideoSource.Metadata? {
  val name = getStringOrNull("name")
  val icon = getStringOrNull("icon")
  val type = getTagList("type")
  val details = getStringOrNull("details")
  val likeCount = getDoubleOrNull("likeCount")?.toLong()
  val favoriteCount = getDoubleOrNull("favoriteCount")?.toLong()
  val isShowPaly = getBooleanOrNull("isShowPaly")
  val isLiked = getBooleanOrNull("isLiked")
  val isBookmarked = getBooleanOrNull("isBookmarked")

  val metadata = TuiplayerShortVideoSource.Metadata(
    name = name,
    icon = icon,
    type = type,
    details = details,
    likeCount = likeCount,
    favoriteCount = favoriteCount,
    isShowPaly = isShowPaly,
    isLiked = isLiked,
    isBookmarked = isBookmarked
  )
  return metadata.takeIf { it.hasValue }
}

internal fun TuiplayerShortVideoSource.Metadata.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  name?.takeIf { it.isNotBlank() }?.let { map.putString("name", it) }
  icon?.takeIf { it.isNotBlank() }?.let { map.putString("icon", it) }
  type?.let { list ->
    val array = Arguments.createArray()
    list.forEach { array.pushString(it) }
    map.putArray("type", array)
  }
  details?.takeIf { it.isNotBlank() }?.let { map.putString("details", it) }
  likeCount?.let { map.putDouble("likeCount", it.toDouble()) }
  favoriteCount?.let { map.putDouble("favoriteCount", it.toDouble()) }
  isShowPaly?.let { map.putBoolean("isShowPaly", it) }
  isLiked?.let { map.putBoolean("isLiked", it) }
  isBookmarked?.let { map.putBoolean("isBookmarked", it) }
  return map
}
