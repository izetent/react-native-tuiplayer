package com.tuiplayer.shortvideo

import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerVideoConfig
import com.tencent.qcloud.tuiplayer.core.api.model.TUISubtitleSource
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
  val metadata: Metadata?,
  val subtitles: List<Subtitle>?
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
    val name: String?,       // Was title
    val icon: String?,       // Was episodeCover/authorAvatar
    val type: List<String>?, // Was tags
    val details: String?,    // Was description/authorName?
    val showCover: Boolean?,
    val playText: String?,
    val moreText: String?,
    val watchMoreText: String?,
    val likeCount: Long?,
    val favoriteCount: Long?,
    val isShowPaly: Boolean?, // Was isPlayShow
    // State fields (kept for logic)
    val isLiked: Boolean?,
    val isBookmarked: Boolean?
  ) {
    val hasValue: Boolean =
      listOf(
        name,
        icon,
        type,
        details,
        showCover,
        playText,
        moreText,
        watchMoreText,
        likeCount,
        favoriteCount,
        isShowPaly,
        isLiked,
        isBookmarked
      ).any { value ->
        when (value) {
          is String -> value.isNotBlank()
          is List<*> -> value.isNotEmpty()
          else -> value != null
        }
      }
  }

  data class Subtitle(
    val name: String?,
    val url: String,
    val mimeType: String?
  ) {
    fun toNative(): TUISubtitleSource? {
      if (url.isBlank()) {
        return null
      }
      val resolvedName = if (!name.isNullOrBlank()) name else url
      return TUISubtitleSource(url, resolvedName, normalizedMimeType())
    }

    fun normalizedMimeType(): String {
      return normalizeMimeType(mimeType)
    }

    companion object {
      private fun normalizeMimeType(value: String?): String {
        if (value.isNullOrBlank()) {
          return "text/vtt"
        }
        return when (value.trim().lowercase()) {
          "text/srt", "srt", "application/x-subrip" -> "text/srt"
          "text/vtt", "vtt", "text/webvtt" -> "text/vtt"
          else -> "text/vtt"
        }
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
    subtitles?.let { entries ->
      val resolved = entries.mapNotNull { it.toNative() }
      if (resolved.isNotEmpty()) {
        playSource.setExternalSubtitle(resolved)
      }
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
