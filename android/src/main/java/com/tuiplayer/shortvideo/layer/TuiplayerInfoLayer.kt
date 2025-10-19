package com.tuiplayer.shortvideo.layer

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.TouchDelegate
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    private const val ACTION_AUTHOR = "author"
    private const val ACTION_AVATAR = "avatar"
    private const val ACTION_LIKE = "like"
    private const val ACTION_COMMENT = "comment"
    private const val ACTION_FAVORITE = "favorite"
    private const val ACTION_WATCH_MORE = "watchMore"
  }

  private var host: TuiplayerLayerHost? = null
  private var boundSource: TUIVideoSource? = null

  private var bottomRootContainer: LinearLayout? = null
  private var panelContainer: LinearLayout? = null
  private var infoRowContainer: LinearLayout? = null
  private var actionContainer: LinearLayout? = null
  private var authorContainer: LinearLayout? = null
  private var avatarView: ImageView? = null
  private var followBadgeView: TextView? = null
  private var authorNameView: TextView? = null
  private var authorMoreView: TextView? = null
  private var titleView: TextView? = null
  private var likeButton: View? = null
  private var likeIconView: ImageView? = null
  private var likeCountView: TextView? = null
  private var commentButton: View? = null
  private var commentIconView: ImageView? = null
  private var commentCountView: TextView? = null
  private var favoriteButton: View? = null
  private var favoriteIconView: ImageView? = null
  private var favoriteCountView: TextView? = null
  private var watchMoreView: TextView? = null
  private var progressBar: TuiplayerProgressBarView? = null
  private var pauseIndicatorView: ImageView? = null
  private var followContainer: FrameLayout? = null
  private var pauseIndicatorVisible = false
  private val defaultIconColor = Color.parseColor("#FEFEFE")
  private val likedIconColor = Color.parseColor("#ff4757")
  private val favoriteIconColor = Color.parseColor("#FFD700")
  private val commentIconColor = Color.parseColor("#FFFFFF")

  private var isTrackingProgress = false
  private var videoDurationMs = 0L

  override fun attachHost(host: TuiplayerLayerHost) {
    this.host = host
    refreshMetadata()
  }

  override fun createView(parent: ViewGroup): View {
    val root =
      LayoutInflater.from(parent.context).inflate(R.layout.tuiplayer_short_video_overlay_layer, parent, false)

    bottomRootContainer = root.findViewById(R.id.tuiplayer_overlay_bottom_root)
    panelContainer = root.findViewById(R.id.tuiplayer_overlay_panel_container)
    infoRowContainer = root.findViewById(R.id.tuiplayer_overlay_info_row)
    actionContainer = root.findViewById(R.id.tuiplayer_overlay_action_container)
    authorContainer = root.findViewById(R.id.tuiplayer_overlay_author_container)
    avatarView = root.findViewById(R.id.tuiplayer_overlay_avatar)
    followBadgeView = root.findViewById(R.id.tuiplayer_overlay_follow_badge)
    authorNameView = root.findViewById(R.id.tuiplayer_overlay_author_name)
    authorMoreView = root.findViewById(R.id.tuiplayer_overlay_more)
    titleView = root.findViewById(R.id.tuiplayer_overlay_title)
    likeButton = root.findViewById(R.id.tuiplayer_overlay_like)
    likeIconView = root.findViewById(R.id.tuiplayer_overlay_like_icon)
    likeCountView = root.findViewById(R.id.tuiplayer_overlay_like_count)
    commentButton = root.findViewById(R.id.tuiplayer_overlay_comment)
    commentIconView = root.findViewById(R.id.tuiplayer_overlay_comment_icon)
    commentCountView = root.findViewById(R.id.tuiplayer_overlay_comment_count)
    favoriteButton = root.findViewById(R.id.tuiplayer_overlay_favorite)
    favoriteIconView = root.findViewById(R.id.tuiplayer_overlay_favorite_icon)
    favoriteCountView = root.findViewById(R.id.tuiplayer_overlay_favorite_count)
    watchMoreView = root.findViewById(R.id.tuiplayer_overlay_watch_more)
    progressBar = root.findViewById(R.id.tuiplayer_overlay_progress)
    pauseIndicatorView = root.findViewById(R.id.tuiplayer_overlay_pause_indicator)
    followContainer = root.findViewById(R.id.tuiplayer_overlay_follow_container)

    setupIcons()
    setupListeners()
    setupProgressBar()
    applyScaledLayout()
    setDefaultAvatar()
    pauseIndicatorView?.apply {
      visibility = View.VISIBLE
      alpha = 0f
      isClickable = false
      isFocusable = false
    }
    updatePauseIndicator(false)

    val viewConfig = ViewConfiguration.get(parent.context)
    val tapTimeout = ViewConfiguration.getTapTimeout()
    val touchSlop = viewConfig.scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var downTime = 0L
    var candidate = false

    root.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = event.rawX
          downY = event.rawY
          downTime = event.downTime
          candidate = !isTapOnInteractiveView(event.rawX, event.rawY)
          candidate
        }
        MotionEvent.ACTION_MOVE -> {
          if (!candidate) {
            return@setOnTouchListener false
          }
          val dx = abs(event.rawX - downX)
          val dy = abs(event.rawY - downY)
          if (dx > touchSlop || dy > touchSlop) {
            candidate = false
            return@setOnTouchListener false
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!candidate) {
            return@setOnTouchListener false
          }
          candidate = false
          val withinTime = (event.eventTime - downTime) <= tapTimeout
          if (withinTime) {
            handleRootClick()
            return@setOnTouchListener true
          }
          false
        }
        MotionEvent.ACTION_CANCEL -> {
          candidate = false
          false
        }
        else -> false
      }
    }

    return root
  }

  override fun onBindData(videoSource: TUIVideoSource) {
    boundSource = videoSource
    isTrackingProgress = false
    videoDurationMs = 0L
    progressBar?.reset()
    progressBar?.isEnabled = false
    refreshMetadata()
    show()
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
  }

  override fun onControllerUnBind(controller: TUIPlayerController) {
    super.onControllerUnBind(controller)
    clearAvatar()
    boundSource = null
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
  }

  override fun onExtInfoChanged(videoSource: TUIVideoSource) {
    super.onExtInfoChanged(videoSource)
    boundSource = videoSource
    refreshMetadata()
  }

  override fun onViewRecycled(videoView: TUIBaseVideoView) {
    super.onViewRecycled(videoView)
    clearAvatar()
    pauseIndicatorVisible = false
    updatePauseIndicator(false)
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
    refreshMetadata()
  }

  private fun setupIcons() {
    likeIconView?.setImageResource(R.drawable.tui_ic_heart_fill)
    commentIconView?.setImageResource(R.drawable.tui_ic_chat_fill)
    favoriteIconView?.setImageResource(R.drawable.tui_ic_star_fill)
    pauseIndicatorView?.setImageResource(R.drawable.tui_ic_play_fill)

    applyIconTint(likeIconView, defaultIconColor)
    applyIconTint(commentIconView, commentIconColor)
    applyIconTint(favoriteIconView, defaultIconColor)
    applyIconTint(pauseIndicatorView, defaultIconColor)
    likeCountView?.setTextColor(defaultIconColor)
    commentCountView?.setTextColor(commentIconColor)
    favoriteCountView?.setTextColor(defaultIconColor)
  }

  private fun setupListeners() {
    likeButton?.setOnClickListener { dispatchAction(ACTION_LIKE) }
    commentButton?.setOnClickListener { dispatchAction(ACTION_COMMENT) }
    favoriteButton?.setOnClickListener { dispatchAction(ACTION_FAVORITE) }
    authorContainer?.setOnClickListener { dispatchAction(ACTION_AUTHOR) }
    avatarView?.setOnClickListener { dispatchAction(ACTION_AVATAR) }
    watchMoreView?.setOnClickListener { dispatchAction(ACTION_WATCH_MORE) }

    val extra = PixelHelper.px(16f)
    expandTouchArea(likeButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(commentButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(favoriteButton, extraLeft = extra, extraRight = extra)
    expandTouchArea(authorContainer, extraLeft = extra, extraRight = extra)
    expandTouchArea(avatarView, extraLeft = extra, extraRight = extra)
    expandTouchArea(watchMoreView, extraLeft = extra, extraRight = extra)
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

  private fun applyScaledLayout() {
    // val bottomOffset = PixelHelper.px(70f)
    val horizontalPadding = PixelHelper.px(12f)
    val actionSpacing = PixelHelper.px(6f)
    val followSpacing = actionSpacing + PixelHelper.px(14f)
    val stackWidth = PixelHelper.px(36f)

    (bottomRootContainer?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
      // params.bottomMargin = bottomOffset
      bottomRootContainer?.layoutParams = params
    }
    panelContainer?.setPadding(0, 0, 0, 0)
    infoRowContainer?.setPadding(horizontalPadding, 0, horizontalPadding, PixelHelper.px(6f))

    (actionContainer?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
      params.marginStart = PixelHelper.px(16f)
      params.width = stackWidth
      actionContainer?.layoutParams = params
    }

    authorContainer?.setPadding(0, 0, 0, 0)

    val avatarSize = stackWidth
    val followSize = stackWidth
    followContainer?.let { container ->
      val params = container.layoutParams
      params.width = followSize
      params.height = followSize
      container.layoutParams = params
      (container.layoutParams as? LinearLayout.LayoutParams)?.let { linearParams ->
        linearParams.bottomMargin = followSpacing
        linearParams.width = followSize
        container.layoutParams = linearParams
      }
    }
    avatarView?.let { avatar ->
      val params = avatar.layoutParams
      params.width = avatarSize
      params.height = avatarSize
      avatar.layoutParams = params
    }
    followBadgeView?.let { badge ->
      (badge.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
        params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        params.width = avatarSize
        params.height = FrameLayout.LayoutParams.WRAP_CONTENT
        params.bottomMargin = -(avatarSize / 2)
        badge.layoutParams = params
      }
      badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(24f))
      badge.gravity = Gravity.CENTER
    }

    val iconSize = PixelHelper.px(24f)
    listOfNotNull(likeIconView, commentIconView, favoriteIconView).forEach { icon ->
      val params = icon.layoutParams
      params?.let {
        it.width = iconSize
        it.height = iconSize
        icon.layoutParams = it
      }
    }

    listOfNotNull(likeCountView, commentCountView, favoriteCountView).forEach { textView ->
      textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(12f))
      (textView.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
        params.topMargin = PixelHelper.px(4f)
        textView.layoutParams = params
      }
    }

    listOfNotNull(likeButton, commentButton, favoriteButton).forEach { button ->
      (button.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
        params.topMargin = actionSpacing
        params.width = followSize
        button.layoutParams = params
      }
    }

    authorNameView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(16f))
    authorMoreView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(16f))
    titleView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(14f))

    (authorMoreView?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
      params.marginStart = PixelHelper.px(6f)
      authorMoreView?.layoutParams = params
    }
    watchMoreView?.apply {
      setTextSize(TypedValue.COMPLEX_UNIT_PX, PixelHelper.pxF(14f))
      gravity = Gravity.CENTER_VERTICAL
      setPadding(horizontalPadding, 0, horizontalPadding, 0)
      (layoutParams as? LinearLayout.LayoutParams)?.let { params ->
        params.topMargin = 0
        params.bottomMargin = 0
        params.height = PixelHelper.px(36f)
        layoutParams = params
      }
    }

    val availableWidth = PixelHelper.screenWidthPx() - PixelHelper.px(160f)
    authorNameView?.maxWidth = max(availableWidth, PixelHelper.px(120f))
    titleView?.maxWidth = max(availableWidth, PixelHelper.px(120f))

    (titleView?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
      params.topMargin = PixelHelper.px(6f)
      titleView?.layoutParams = params
    }

    progressBar?.let { bar ->
      val params = (bar.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      // params.topMargin = PixelHelper.px(8f)
      params.bottomMargin = 0
      params.marginStart = 0
      params.marginEnd = 0
      bar.layoutParams = params
    }

    pauseIndicatorView?.layoutParams = pauseIndicatorView?.layoutParams?.apply {
      width = PixelHelper.px(40f)
      height = PixelHelper.px(40f)
    }
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
    val resolvedAuthor = metadata?.authorName?.takeIf { it.isNotBlank() }
      ?: source?.fileId?.takeIf { it.isNotBlank() }
      ?: source?.url?.takeIf { it.isNotBlank() }

    authorNameView?.apply {
      text = resolvedAuthor.orEmpty()
      visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    titleView?.apply {
      val resolvedTitle = metadata?.title?.takeIf { it.isNotBlank() }
      text = resolvedTitle.orEmpty()
      visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    likeCountView?.text = formatCount(metadata?.likeCount)
    commentCountView?.text = formatCount(metadata?.commentCount)
    favoriteCountView?.text = formatCount(metadata?.favoriteCount)

    val isLiked = metadata?.isLiked == true
    val isBookmarked = metadata?.isBookmarked == true

    applyIconTint(likeIconView, if (isLiked) likedIconColor else defaultIconColor)
    likeCountView?.setTextColor(defaultIconColor)

    applyIconTint(commentIconView, commentIconColor)
    commentCountView?.setTextColor(commentIconColor)

    applyIconTint(favoriteIconView, if (isBookmarked) favoriteIconColor else defaultIconColor)
    favoriteCountView?.setTextColor(defaultIconColor)

    val isFollowed = metadata?.isFollowed == true
    followBadgeView?.visibility = if (isFollowed) View.GONE else View.VISIBLE
    followContainer?.alpha = 1f

    val watchText = metadata?.watchMoreText?.takeIf { it.isNotBlank() }
    watchMoreView?.apply {
      text = watchText ?: ""
      visibility = if (watchText != null) View.VISIBLE else View.GONE
      isEnabled = watchText != null
    }

    val avatarUrl = metadata?.authorAvatar
    val videoView = videoView
    val target = avatarView
    if (!avatarUrl.isNullOrBlank() && videoView != null && target != null) {
      Glide.with(videoView).clear(target)
      ImageViewCompat.setImageTintList(target, null)
      Glide.with(videoView)
        .load(avatarUrl)
        .placeholder(ColorDrawable(Color.parseColor("#33000000")))
        .circleCrop()
        .into(target)
    } else {
      if (videoView != null && target != null) {
        Glide.with(videoView).clear(target)
      }
      setDefaultAvatar()
    }
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

  private fun isTapOnInteractiveView(rawX: Float, rawY: Float): Boolean {
    val interactiveViews = listOf<View?> (
      likeButton,
      commentButton,
      favoriteButton,
      avatarView,
      authorContainer,
      watchMoreView,
      progressBar
    )
    interactiveViews.forEach { view ->
      if (view != null && view.isShown && view.containsRawPoint(rawX, rawY, PixelHelper.px(16f))) {
        return true
      }
    }
    return false
  }

  private fun View.containsRawPoint(rawX: Float, rawY: Float, extra: Int = 0): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)
    val left = location[0] - extra
    val top = location[1] - extra
    val right = location[0] + width + extra
    val bottom = location[1] + height + extra
    return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
  }

  private fun clearAvatar() {
    val target = avatarView ?: return
    val videoView = videoView
    if (videoView != null) {
      Glide.with(videoView).clear(target)
    }
    setDefaultAvatar()
  }

  private fun setDefaultAvatar() {
    val target = avatarView ?: return
    target.setImageResource(R.drawable.tui_ic_avatar_placeholder)
    ImageViewCompat.setImageTintList(target, ColorStateList.valueOf(defaultIconColor))
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
      view.bringToFront()
    }
    view.animate()
      .alpha(targetAlpha)
      .setDuration(160L)
      .start()
  }

  override fun onPlaybackStateChanged(paused: Boolean) {
    updatePauseIndicator(paused)
  }

  private fun applyIconTint(view: ImageView?, color: Int) {
    if (view == null) {
      return
    }
    ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(color))
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
}
