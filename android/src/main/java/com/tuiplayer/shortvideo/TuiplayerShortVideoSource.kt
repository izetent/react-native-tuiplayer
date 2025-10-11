package com.tuiplayer.shortvideo

import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource

internal data class TuiplayerShortVideoSource(
  val type: SourceType,
  val appId: Int?,
  val fileId: String?,
  val url: String?,
  val coverPictureUrl: String?,
  val pSign: String?
) {

  enum class SourceType {
    FILE_ID,
    URL
  }

  fun toPlaySource(): TUIPlaySource? {
    val playSource = TUIVideoSource()
    coverPictureUrl?.takeIf { it.isNotBlank() }?.let { playSource.setCoverPictureUrl(it) }
    pSign?.takeIf { it.isNotBlank() }?.let { playSource.setPSign(it) }

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
}
