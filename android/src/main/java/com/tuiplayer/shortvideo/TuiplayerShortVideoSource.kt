package com.tuiplayer.shortvideo

import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerVideoConfig
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource

internal data class TuiplayerShortVideoSource(
  val type: SourceType,
  val appId: Int?,
  val fileId: String?,
  val url: String?,
  val coverPictureUrl: String?,
  val pSign: String?,
  val extViewType: Int?,
  val autoPlay: Boolean?,
  val videoConfig: VideoConfig?,
  val metadata: Metadata?
) {

  enum class SourceType {
    FILE_ID,
    URL
  }

  data class VideoConfig(
    val preloadBufferSizeInMB: Float?,
    val preDownloadSizeInMB: Double?
  )

  data class Metadata(
    val authorName: String?,
    val authorAvatar: String?,
    val title: String?,
    val likeCount: Long?,
    val commentCount: Long?,
    val favoriteCount: Long?,
    val isLiked: Boolean?,
    val isBookmarked: Boolean?,
    val isFollowed: Boolean?,
    val watchMoreText: String?
  ) {
    val hasValue: Boolean =
      listOf(
        authorName,
        authorAvatar,
        title,
        likeCount,
        commentCount,
        favoriteCount,
        isLiked,
        isBookmarked,
        isFollowed,
        watchMoreText
      ).any { value ->
        when (value) {
          is String -> value.isNotBlank()
          else -> value != null
        }
      }
  }

  fun toPlaySource(): TUIPlaySource? {
    val playSource = TUIVideoSource()
    coverPictureUrl?.takeIf { it.isNotBlank() }?.let { playSource.setCoverPictureUrl(it) }
    pSign?.takeIf { it.isNotBlank() }?.let { playSource.setPSign(it) }
    extViewType?.let { playSource.setExtViewType(it) }
    autoPlay?.let { playSource.setAutoPlay(it) }
    videoConfig?.let { config ->
      val playerConfig = TUIPlayerVideoConfig()
      config.preloadBufferSizeInMB?.let { playerConfig.setPreloadBufferSizeInMB(it) }
      config.preDownloadSizeInMB?.let { size ->
        val bytes = (size * 1024 * 1024).toLong()
        playerConfig.setPreDownloadSize(bytes)
      }
      playSource.setVideoConfig(playerConfig)
    }

    when (type) {
      SourceType.FILE_ID -> {
        if (fileId.isNullOrBlank()) {
          return null
        }
        appId?.let { playSource.setAppId(it) }
        playSource.setFileId(fileId)
        url?.takeIf { it.isNotBlank() }?.let { playSource.setVideoURL(it) }
      }
      SourceType.URL -> {
        val resolvedUrl = url?.takeIf { it.isNotBlank() } ?: return null
        playSource.setVideoURL(resolvedUrl)
      }
    }
    return playSource
  }

  fun matchesModel(model: TUIVideoSource): Boolean {
    val modelFileId = model.fileId
    val modelUrl = model.videoURL
    val modelAppId = model.appId

    if (!fileId.isNullOrBlank() && !modelFileId.isNullOrBlank()) {
      if (!fileId.equals(modelFileId, ignoreCase = false)) {
        return false
      }
      return appId == null || appId == 0 || appId == modelAppId
    }

    if (!url.isNullOrBlank() && !modelUrl.isNullOrBlank()) {
      return url == modelUrl
    }

    return false
  }
}
