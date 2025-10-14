package com.tuiplayer.shortvideo.layer

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerController
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants
import com.tencent.qcloud.tuiplayer.core.api.model.TUIFileVideoInfo
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseVideoView
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIVodLayer

internal class TuiplayerCoverLayer : TUIVodLayer() {

  companion object {
    const val TAG = "TuiplayerCoverLayer"
  }

  private var coverUrlFromServer: String? = null

  private val baseRequestOptions = RequestOptions()
    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    .dontAnimate()
    .dontTransform()
    .format(DecodeFormat.PREFER_RGB_565)

  override fun createView(parent: ViewGroup): View {
    return ImageView(parent.context).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      ).apply {
        gravity = Gravity.CENTER
      }
      scaleType = ImageView.ScaleType.FIT_XY
    }
  }

  override fun show() {
    super.show()
    loadCover()
  }

  override fun onBindData(videoSource: TUIVideoSource) {
    show()
  }

  override fun onControllerUnBind(controller: TUIPlayerController) {
    super.onControllerUnBind(controller)
    show()
    coverUrlFromServer = null
  }

  override fun onFirstFrameRendered() {
    hidden()
  }

  override fun onRecFileVideoInfo(params: TUIFileVideoInfo) {
    if (!isShowing) {
      return
    }
    val coverUrl = params.coverUrl
    if (!coverUrl.isNullOrEmpty()) {
      coverUrlFromServer = coverUrl
      loadCover()
    }
  }

  override fun onViewRecycled(videoView: TUIBaseVideoView) {
    super.onViewRecycled(videoView)
    coverUrlFromServer = null
  }

  private fun loadCover() {
    val videoView = getVideoView() ?: return
    val videoSource = videoView.videoModel as? TUIVideoSource ?: return
    val imageView = resolveImageView()
    val requestOptions = resolveRequestOptions(imageView)
    val coverUrl = when {
      !videoSource.coverPictureUrl.isNullOrEmpty() -> videoSource.coverPictureUrl
      !coverUrlFromServer.isNullOrEmpty() -> coverUrlFromServer
      else -> null
    }
    val request = Glide.with(videoView)
    if (coverUrl.isNullOrEmpty()) {
      request
        .load(ColorDrawable(0x00000000))
        .apply(requestOptions)
        .into(imageView)
    } else {
      request
        .load(coverUrl)
        .apply(requestOptions)
        .into(imageView)
    }
  }

  private fun resolveRequestOptions(imageView: ImageView): RequestOptions {
    val renderMode = renderMode
    val options = baseRequestOptions.clone()

    val params = imageView.layoutParams as? FrameLayout.LayoutParams
      ?: FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      ).also { imageView.layoutParams = it }

    return when (renderMode) {
      TUIConstants.TUIRenderMode.FULL_FILL_SCREEN -> {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        imageView.adjustViewBounds = false
        options.centerCrop()
      }

      TUIConstants.TUIRenderMode.ADJUST_RESOLUTION -> {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        imageView.adjustViewBounds = true
        options.fitCenter()
      }

      else -> options
    }
  }

  private fun resolveImageView(): ImageView =
    requireNotNull(getView() as? ImageView) { "Cover layer view must be ImageView" }

  override fun tag(): String = TAG
}
