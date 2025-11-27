package com.tuiplayer.shortvideo.layer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerController
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource
import com.tencent.qcloud.tuiplayer.core.api.ui.player.ITUIVodPlayer
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseVideoView
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIVodLayer
import com.tuiplayer.R
import com.tuiplayer.shortvideo.TuiplayerShortVideoSource
import kotlin.math.abs
import kotlin.math.max

internal class TuiplayerInfoLayer : TUIVodLayer(), TuiplayerHostAwareLayer {

  companion object {
    const val TAG = "TuiplayerInfoLayer"
    const val ACTION_LIKE = "like"
    const val ACTION_COLLECT = "favorite"
    const val ACTION_MORE = "comment"
    const val ACTION_PLAY = "play"
    const val ACTION_ICON = "icon"
    const val ACTION_NAME = "name"
    const val ACTION_DETAILS = "details"
  }

  private var host: TuiplayerLayerHost? = null
  private var boundSource: TUIVideoSource? = null

  // Views
  private var rootView: View? = null
  private var albumArtView: ImageView? = null
  private var titleView: TextView? = null
  private var typeTextView: TextView? = null
  private var descriptionView: TextView? = null
  private var playButton: View? = null
  private var panelContainer: View? = null

  private var collectButton: View? = null
  private var collectIconView: ImageView? = null
  private var collectCountView: TextView? = null

  private var likeButton: View? = null
  private var likeIconView: ImageView? = null
  private var likeCountView: TextView? = null

  private var moreButton: View? = null
  private var moreIconView: ImageView? = null
  private var moreCountView: TextView? = null

  private var progressBar: TuiplayerProgressBarView? = null
  private var pauseIndicatorView: ImageView? = null

  private var pauseIndicatorVisible = false
  private var lastResolvedTypeText: String? = null
  private var lastIsCollected = false
  private var lastIsLiked = false
  private var lastFavoriteCount: Long = 0L
  private var lastLikeCount: Long = 0L
  private var overlayVisible = true

  // Colors
  private val defaultIconColor = Color.parseColor("#FFFFFF")
  private val collectedIconColor = Color.parseColor("#FFE63D")
  private val likedIconColor = Color.parseColor("#FF58F5")

  private var isTrackingProgress = false
  private var videoDurationMs = 0L

  override fun attachHost(host: TuiplayerLayerHost) {
    this.host = host
    refreshMetadata()
  }

  override fun createView(parent: ViewGroup): View {
    val root = LayoutInflater.from(parent.context).inflate(R.layout.tuiplayer_short_video_overlay_layer, parent, false)
    rootView = root

    // Bind Views
    albumArtView = root.findViewById(R.id.tuiplayer_overlay_album_art)
    titleView = root.findViewById(R.id.tuiplayer_overlay_title)
    typeTextView = root.findViewById(R.id.tuiplayer_overlay_type)
    descriptionView = root.findViewById(R.id.tuiplayer_overlay_description)
    playButton = root.findViewById(R.id.tuiplayer_overlay_play_btn)
    panelContainer = root.findViewById(R.id.tuiplayer_overlay_panel_container)

    collectButton = root.findViewById(R.id.tuiplayer_overlay_favorite)
    collectIconView = root.findViewById(R.id.tuiplayer_overlay_favorite_icon)
    collectCountView = root.findViewById(R.id.tuiplayer_overlay_favorite_count)

    likeButton = root.findViewById(R.id.tuiplayer_overlay_like)
    likeIconView = root.findViewById(R.id.tuiplayer_overlay_like_icon)
    likeCountView = root.findViewById(R.id.tuiplayer_overlay_like_count)

    moreButton = root.findViewById(R.id.tuiplayer_overlay_comment)
    moreIconView = root.findViewById(R.id.tuiplayer_overlay_comment_icon)
    moreCountView = root.findViewById(R.id.tuiplayer_overlay_comment_count)

    progressBar = root.findViewById(R.id.tuiplayer_overlay_progress)
    pauseIndicatorView = root.findViewById(R.id.tuiplayer_overlay_pause_indicator)

    setupIcons()
    setupListeners()
    setupProgressBar()

    pauseIndicatorView?.apply {
      visibility = View.VISIBLE
      alpha = 0f
      isClickable = false
      isFocusable = false
    }
    updatePauseIndicator(false)
    applyOverlayVisibility()

    // Tap handling for pause/resume on background area
    // The panelContainer consumes clicks, so clicks here are only from the empty area above
    root.setOnClickListener { handleRootClick() }
    // 长按不触发点击，避免长按松手也触发暂停
    root.setOnLongClickListener { true }
    
    // Consume clicks on the panel container to prevent them from bubbling up to root
    panelContainer?.setOnClickListener { /* Consume click */ }

    return root
  }

  override fun onBindData(videoSource: TUIVideoSource) {
    boundSource = videoSource
    lastResolvedTypeText = null
    lastFavoriteCount = 0L
    lastLikeCount = 0L
    lastIsCollected = false
    lastIsLiked = false
    refreshMetadata()
    show()
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
  }

  override fun onControllerUnBind(controller: TUIPlayerController) {
    super.onControllerUnBind(controller)
    boundSource = null
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
    lastResolvedTypeText = null
    lastFavoriteCount = 0L
    lastLikeCount = 0L
    lastIsCollected = false
    lastIsLiked = false
  }

  override fun onExtInfoChanged(videoSource: TUIVideoSource) {
    super.onExtInfoChanged(videoSource)
    boundSource = videoSource
    // Removed: Stale metadata from native events was reverting RN state.
  }

  override fun onViewRecycled(videoView: TUIBaseVideoView) {
    super.onViewRecycled(videoView)
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
    lastResolvedTypeText = null
    lastFavoriteCount = 0L
    lastLikeCount = 0L
    lastIsCollected = false
    lastIsLiked = false
  }

  override fun onPlayPrepare() {
    super.onPlayPrepare()
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
    progressBar?.setLoadingVisible(true)
  }

  override fun onPlayBegin() {
    super.onPlayBegin()
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
    progressBar?.setLoadingVisible(false)
  }

  override fun onPlayPause() {
    super.onPlayPause()
    if (pauseIndicatorVisible) {
      updatePauseIndicator(true)
    }
    progressBar?.setLoadingVisible(false)
  }

  override fun onPlayStop() {
    super.onPlayStop()
    if (pauseIndicatorVisible) {
      updatePauseIndicator(true)
    } else {
      updatePauseIndicator(false)
    }
    progressBar?.setLoadingVisible(false)
  }

  override fun onPlayLoading() {
    super.onPlayLoading()
    if (!pauseIndicatorVisible) {
      updatePauseIndicator(false)
    }
    progressBar?.setLoadingVisible(true)
  }

  override fun onPlayEnd() {
    super.onPlayEnd()
    if (pauseIndicatorVisible) {
      updatePauseIndicator(true)
    } else {
      updatePauseIndicator(false)
    }
    progressBar?.setLoadingVisible(false)
  }

  override fun onPlayProgress(current: Long, duration: Long, playable: Long) {
    super.onPlayProgress(current, duration, playable)
    videoDurationMs = duration.coerceAtLeast(0L)
    val bar = progressBar ?: return
    if (videoDurationMs <= 0L) {
      bar.reset()
      bar.isEnabled = false
      return
    }
    bar.isEnabled = true
    val safeDuration = videoDurationMs.coerceAtLeast(1L)
    val progressRatio =
      (current.coerceAtLeast(0).toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val playableRatio =
      (playable.coerceAtLeast(0).toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0).toFloat()
    bar.setBufferedRatio(playableRatio)
    if (!isTrackingProgress) {
      bar.setProgressRatio(progressRatio)
    }
    progressBar?.setLoadingVisible(false)
  }

  override fun tag(): String = TAG

  override fun onMetadataUpdated(source: TuiplayerShortVideoSource?) {
    // Source validation is now done in applyMetadata
    refreshMetadata()
  }

  private fun setupIcons() {
    collectIconView?.setImageResource(R.drawable.tui_ic_collect_custom)
    likeIconView?.setImageResource(R.drawable.tui_ic_like_custom)
    moreIconView?.setImageResource(R.drawable.tui_ic_more_custom)
    pauseIndicatorView?.setImageResource(R.drawable.tui_ic_play_custom)

    applyIconTint(collectIconView, defaultIconColor)
    applyIconTint(likeIconView, defaultIconColor)
    applyIconTint(moreIconView, defaultIconColor)
    applyIconTint(pauseIndicatorView, defaultIconColor)

    collectCountView?.setTextColor(defaultIconColor)
    likeCountView?.setTextColor(defaultIconColor)
    moreCountView?.setTextColor(defaultIconColor)
  }

  private fun setupListeners() {
    val collectListener = View.OnClickListener {
      dispatchAction(ACTION_COLLECT)
    }
    collectButton?.setOnClickListener(collectListener)
    collectIconView?.setOnClickListener(collectListener)
    collectCountView?.setOnClickListener(collectListener)

    val likeListener = View.OnClickListener {
      dispatchAction(ACTION_LIKE)
    }
    likeButton?.setOnClickListener(likeListener)
    likeIconView?.setOnClickListener(likeListener)
    likeCountView?.setOnClickListener(likeListener)

    val moreListener = View.OnClickListener { dispatchAction(ACTION_MORE) }
    moreButton?.setOnClickListener(moreListener)
    moreIconView?.setOnClickListener(moreListener)
    moreCountView?.setOnClickListener(moreListener)

    playButton?.setOnClickListener { dispatchAction(ACTION_PLAY) }
    
    // New click listeners
    albumArtView?.setOnClickListener { dispatchAction(ACTION_ICON) }
    titleView?.setOnClickListener { dispatchAction(ACTION_NAME) }
    descriptionView?.setOnClickListener { dispatchAction(ACTION_DETAILS) }

    val extra = PixelHelper.px(16f)
    expandTouchArea(collectButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(likeButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(moreButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(playButton, extraLeft = extra, extraRight = extra)
    
    expandTouchArea(albumArtView, extraLeft = extra, extraRight = extra)
    expandTouchArea(titleView, extraLeft = extra, extraRight = extra)
    expandTouchArea(descriptionView, extraLeft = extra, extraRight = extra)

    val interceptViews = listOf(
      collectButton, collectIconView, collectCountView,
      likeButton, likeIconView, likeCountView,
      moreButton, moreIconView, moreCountView,
      playButton
    )
    interceptViews.forEach { attachPreventParentIntercept(it) }
  }

  private fun setupProgressBar() {
    val bar = progressBar ?: return
    bar.isEnabled = false
    bar.setOnSeekListener(object : TuiplayerProgressBarView.OnSeekListener {
      override fun onSeekStart() {
        isTrackingProgress = true
        progressBar?.let { disallowParentIntercept(it, true) }
      }

      override fun onSeekChanged(ratio: Float) = Unit

      override fun onSeekFinished(ratio: Float, cancelled: Boolean) {
        progressBar?.let { disallowParentIntercept(it, false) }
        isTrackingProgress = false
        if (cancelled) {
          return
        }
        val player = getPlayer() as? ITUIVodPlayer ?: return
        val duration = videoDurationMs
        if (duration <= 0L) {
          return
        }
        val targetMs = (duration * ratio).toLong().coerceIn(0, duration)
        player.seekTo(targetMs / 1000f)
        progressBar?.setProgressRatio(ratio)
      }
    })
    expandTouchArea(bar, extraTop = PixelHelper.px(8f), extraBottom = PixelHelper.px(12f))
  }

  private fun disallowParentIntercept(view: View, disallow: Boolean) {
    var parentView = view.parent
    while (parentView is View) {
      parentView.requestDisallowInterceptTouchEvent(disallow)
      parentView = parentView.parent
    }
  }

  private fun expandTouchArea(
    view: View?,
    extraLeft: Int = 0,
    extraTop: Int = 0,
    extraRight: Int = 0,
    extraBottom: Int = 0
  ) {
    val target = view ?: return
    val parentView = target.parent as? View ?: return
    parentView.post {
      val rect = Rect()
      target.getHitRect(rect)
      rect.left -= extraLeft
      rect.top -= extraTop
      rect.right += extraRight
      rect.bottom += extraBottom
      parentView.touchDelegate = TouchDelegate(rect, target)
    }
  }

  private fun attachPreventParentIntercept(view: View?) {
    view ?: return
    view.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> disallowParentIntercept(v, true)
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> disallowParentIntercept(v, false)
      }
      false
    }
  }

  private fun refreshMetadata() {
    val source = resolveCurrentSource() ?: return
    val targetSource = host?.resolveSource(source)
    applyMetadata(targetSource?.metadata, targetSource)
  }

  private fun resolveCurrentSource(): TUIVideoSource? {
    val cached = boundSource
    if (cached != null) {
      return cached
    }
    val resolved = getVideoView()?.videoModel as? TUIVideoSource
    if (resolved != null) {
      boundSource = resolved
    }
    return resolved
  }

  private fun applyMetadata(
    metadata: TuiplayerShortVideoSource.Metadata?,
    source: TuiplayerShortVideoSource?
  ) {
    // Validate that this metadata belongs to the current video
    val current = boundSource
    if (current != null && source != null) {
      if (current is TUIVideoSource && !source.matchesModel(current)) {
        android.util.Log.w(TAG, "applyMetadata: Ignoring metadata from different video source")
        return
      }
    }
    
    // Additional validation: Check if favoriteCount is drastically different
    // This catches cases where matchesModel might not work correctly
    if (metadata != null && lastFavoriteCount > 0) {
      val incomingFavoriteCount = metadata.favoriteCount ?: 0L
      val diff = kotlin.math.abs(incomingFavoriteCount - lastFavoriteCount)
      // If the difference is more than 2, it's likely from a different video
      if (diff > 2 && incomingFavoriteCount < lastFavoriteCount) {
        android.util.Log.w(TAG, "applyMetadata: Ignoring metadata with suspicious favoriteCount - local=$lastFavoriteCount, incoming=$incomingFavoriteCount")
        return
      }
    }

    // 如果拿不到有效的元信息，保持当前 UI 状态，避免把点赞/收藏等状态清零
    if (metadata == null) {
      return
    }
    
    // Title (name)
    titleView?.apply {
      val resolvedTitle = metadata.name?.takeIf { it.isNotBlank() }
      text = resolvedTitle.orEmpty()
      visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Episode Cover (icon)
    val episodeCoverUrl = metadata.icon
    val videoView = videoView
    val target = albumArtView
    if (!episodeCoverUrl.isNullOrBlank() && videoView != null && target != null) {
      Glide.with(videoView).clear(target)
      Glide.with(videoView)
        .load(episodeCoverUrl)
        .placeholder(R.drawable.tui_overlay_album_art_bg)
        .centerCrop()
        .into(target)
    } else {
      if (videoView != null && target != null) {
        Glide.with(videoView).clear(target)
      }
      target?.setImageDrawable(null)
      target?.setBackgroundResource(R.drawable.tui_overlay_album_art_bg)
    }

    // Tags (type) - Create horizontal rows that wrap
    val resolvedTag = metadata.type
      ?.filter { it.isNotBlank() }
      ?.takeIf { it.isNotEmpty() }
      ?.joinToString(" / ")
      ?: lastResolvedTypeText
    lastResolvedTypeText = resolvedTag
    typeTextView?.apply {
      text = resolvedTag.orEmpty()
      visibility = if (resolvedTag.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Description (details)
    descriptionView?.apply {
      val resolvedDetails = metadata.details?.takeIf { it.isNotBlank() }
      text = resolvedDetails.orEmpty()
      visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Play Button Visibility (isShowPaly)
    playButton?.visibility = if (metadata.isShowPaly == true) View.VISIBLE else View.GONE

    // Counts & states - simplified: just apply the incoming metadata
    lastLikeCount = max(0L, metadata.likeCount ?: 0L)
    lastFavoriteCount = max(0L, metadata.favoriteCount ?: 0L)
    lastIsLiked = metadata.isLiked ?: false
    lastIsCollected = metadata.isBookmarked ?: false
    
    renderReactionState()
    
    // More icon always default
    applyIconTint(moreIconView, defaultIconColor)
  }

  private fun dispatchAction(action: String) {
    val source = resolveCurrentSource() ?: return
    host?.emitOverlayAction(source, action)
  }


  private fun handleRootClick() {
    val toggledPaused = host?.requestTogglePlay()
    if (toggledPaused != null) {
      pauseIndicatorVisible = toggledPaused
      updatePauseIndicator(toggledPaused)
    }
  }

  private fun updatePauseIndicator(visible: Boolean) {
    val view = pauseIndicatorView ?: return
    val targetAlpha = if (visible) 1f else 0f
    if (view.alpha == targetAlpha) {
      return
    }
    view.animate().cancel()
    view.visibility = View.VISIBLE
    if (visible) {
      // view.bringToFront() // Removed to prevent layout/focus issues
    }
    view.animate()
      .alpha(targetAlpha)
      .setDuration(160L)
      .start()
  }

  override fun onPlaybackStateChanged(paused: Boolean) {
    updatePauseIndicator(paused)
  }

  override fun onOverlayVisibilityChanged(visible: Boolean) {
    overlayVisible = visible
    applyOverlayVisibility()
  }

  private fun applyIconTint(view: ImageView?, color: Int) {
    if (view == null) {
      return
    }
    ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(color))
  }

  private fun renderReactionState() {
    collectCountView?.text = formatCount(lastFavoriteCount)
    likeCountView?.text = formatCount(lastLikeCount)
    applyIconTint(collectIconView, if (lastIsCollected) collectedIconColor else defaultIconColor)
    applyIconTint(likeIconView, if (lastIsLiked) likedIconColor else defaultIconColor)
  }

  private fun formatCount(count: Long?): String {
    val value = max(0L, count ?: 0L)
    return when {
      value >= 100_000 -> "10w+"
      value >= 10_000 -> {
        val scaledTenth = value / 1_000
        val major = scaledTenth / 10
        val minor = scaledTenth % 10
        if (minor == 0L) "${major}w" else "${major}.${minor}w"
      }
      value >= 1_000 -> {
        val scaledTenth = value / 10
        val major = scaledTenth / 100
        val minor = (scaledTenth / 10) % 10
        if (minor == 0L) "${major}k" else "${major}.${minor}k"
      }
      else -> value.toString()
    }
  }

  private fun applyOverlayVisibility() {
    val root = rootView ?: return
    if (overlayVisible) {
      root.visibility = View.VISIBLE
      updatePauseIndicator(pauseIndicatorVisible)
    } else {
      root.visibility = View.GONE
      pauseIndicatorView?.animate()?.cancel()
      pauseIndicatorView?.alpha = 0f
    }
  }
}
