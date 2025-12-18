package com.tuiplayer.shortvideo.playlist

import android.util.Log
import com.facebook.react.bridge.WritableMap
import com.tencent.qcloud.tuiplayer.core.api.common.TUIErrorCode
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource
import com.tencent.qcloud.tuiplayer.shortvideo.api.data.TUIShortVideoDataManager
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoView
import com.tuiplayer.shortvideo.TuiplayerShortVideoSource
import com.tuiplayer.shortvideo.toSnapshotMap
import com.tuiplayer.shortvideo.toWritableMap

private const val TAG = "TuiplayerPlaylist"

internal data class ResolvedSource(
  val source: TuiplayerShortVideoSource,
  val model: TUIPlaySource
)

internal interface PlaylistHost {
  val shortVideoView: TUIShortVideoView
  fun dataManagerOrNull(): TUIShortVideoDataManager?
  val isViewReady: Boolean
  val isReleased: Boolean
  val autoPlayEnabled: Boolean
  var lastKnownIndex: Int
  var lastEndReachedTotal: Int
  fun notifyPlaylistItemChanged(index: Int)
  fun notifyOverlayUpdated(index: Int, source: TuiplayerShortVideoSource)
  fun dispatchPageChanged(index: Int, total: Int)
  fun dispatchEndReachedIfNeeded(index: Int, total: Int)
  fun onPlaylistStateReset()
}

internal interface PlaylistPlaybackCoordinator {
  fun maybeApplyInitialIndex(): Boolean
  fun ensureInitialPlayback(index: Int, attempt: Int)
  fun scheduleStartAtIndex(index: Int, smooth: Boolean, force: Boolean)
  fun setPendingStart(
    index: Int,
    smooth: Boolean,
    reason: String,
    force: Boolean = false,
    autoFlush: Boolean = true
  )
  fun flushPendingStartCommand(reason: String)
  fun hasPendingStartCommand(): Boolean
  fun hasActiveVodPlayer(): Boolean
  fun detachCurrentVodPlayer()
}

internal class PlaylistController(
  private val host: PlaylistHost,
  private val playback: PlaylistPlaybackCoordinator
) {

  private var sources: MutableList<TuiplayerShortVideoSource> = mutableListOf()
  private var pendingResolved: MutableList<ResolvedSource>? = null

  fun setSources(rawSources: List<TuiplayerShortVideoSource>) {
    Log.d(
      TAG,
      "setSources count=${rawSources.size} isViewReady=${host.isViewReady} isReleased=${host.isReleased}"
    )
    val resolved = resolvePlayableSources(rawSources)
    Log.d(TAG, "setSources resolvedCount=${resolved.size}")
    if (!host.isViewReady) {
      storePendingSources(resolved, sources.isEmpty())
      return
    }
    applyResolvedSources(resolved, "setSources")
  }

  fun appendSources(rawSources: List<TuiplayerShortVideoSource>) {
    Log.d(TAG, "appendSources incoming=${rawSources.size} isViewReady=${host.isViewReady}")
    val resolved = resolvePlayableSources(rawSources)
    if (resolved.isEmpty()) {
      Log.w(TAG, "appendSources ignored; no playable entries (incoming=${rawSources.size})")
      return
    }
    if (!host.isViewReady) {
      if (pendingResolved.isNullOrEmpty()) {
        storePendingSources(resolved, sources.isEmpty())
      } else {
        queuePendingAppend(resolved)
      }
      Log.d(TAG, "appendSources deferred until native ready (appended=${resolved.size})")
      return
    }
    if (sources.isEmpty()) {
      applyResolvedSources(resolved, "appendSources.replace")
      return
    }
    appendPlaylist(resolved)
    sources.addAll(resolved.map { it.source })
    finalizePlaylistChange("appendSources", previousWasEmpty = false)
  }

  fun onNativeReady() {
    pendingResolved?.toList()?.let {
      applyResolvedSources(it, "pendingResolved", skipIdentityCheck = true)
    }
    pendingResolved = null
  }

  fun addData(source: TuiplayerShortVideoSource, index: Int) {
    val manager = host.dataManagerOrNull() ?: return
    val model = source.toPlaySource() ?: return
    val target = index.coerceIn(0, sources.size)
    manager.addData(model, target)
    sources.add(target, source)
  }

  fun addRangeData(rawSources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    val manager = host.dataManagerOrNull() ?: return
    if (rawSources.isEmpty()) {
      return
    }
    val resolved = resolvePlayableSources(rawSources)
    if (resolved.isEmpty()) {
      return
    }
    if (resolved.size != rawSources.size) {
      Log.w(TAG, "addRangeData dropped ${rawSources.size - resolved.size} non-playable sources")
    }
    val models = resolved.map { it.model }
    val target = startIndex.coerceIn(0, sources.size)
    manager.addRangeData(models, target)
    sources.addAll(target, resolved.map { it.source })
  }

  fun replaceData(source: TuiplayerShortVideoSource, index: Int) {
    val manager = host.dataManagerOrNull() ?: return
    if (index !in sources.indices) {
      return
    }
    val model = source.toPlaySource() ?: return
    manager.replaceData(model, index)
    sources[index] = source
    val currentIndex = currentIndex()
    if (currentIndex != null && currentIndex == index) {
      host.notifyOverlayUpdated(index, source)
    }
    host.notifyPlaylistItemChanged(index)
    playback.flushPendingStartCommand("replaceData")
  }

  fun updateMetadata(index: Int, metadata: TuiplayerShortVideoSource.Metadata?) {
    if (index !in sources.indices || metadata == null) {
      return
    }
    val existing = sources[index]
    val merged = mergeMetadata(existing.metadata, metadata)
    val normalized = merged.takeIf { it.hasValue }
    if (existing.metadata == normalized) {
      return
    }
    sources[index] = existing.copy(metadata = normalized)
    val currentIndex = currentIndex()
    if (currentIndex != null && currentIndex == index) {
      host.notifyOverlayUpdated(index, sources[index])
    } else {
      host.notifyPlaylistItemChanged(index)
    }
    playback.flushPendingStartCommand("updateMetadata")
  }

  fun replaceRangeData(rawSources: List<TuiplayerShortVideoSource>, startIndex: Int) {
    val manager = host.dataManagerOrNull() ?: return
    if (rawSources.isEmpty()) {
      return
    }
    val resolved = resolvePlayableSources(rawSources)
    if (resolved.isEmpty()) {
      return
    }
    if (resolved.size != rawSources.size) {
      Log.w(TAG, "replaceRangeData aborted due to non-playable sources")
      return
    }
    val models = resolved.map { it.model }
    val start = startIndex.coerceIn(0, sources.size)
    val end = (start + rawSources.size).coerceAtMost(sources.size)
    if (end - start != rawSources.size) {
      return
    }
    manager.replaceRangeData(models, start)
    for (offset in resolved.indices) {
      sources[start + offset] = resolved[offset].source
    }
    val currentIndex = currentIndex()
    if (currentIndex != null && currentIndex in start until (start + resolved.size)) {
      val source = sources[currentIndex]
      host.notifyOverlayUpdated(currentIndex, source)
      host.notifyPlaylistItemChanged(currentIndex)
    }
  }

  fun removeData(index: Int) {
    val manager = host.dataManagerOrNull() ?: return
    if (index !in sources.indices) {
      return
    }
    manager.removeData(index)
    sources.removeAt(index)
    clampLastKnownIndex()
  }

  fun removeRangeData(index: Int, count: Int) {
    val manager = host.dataManagerOrNull() ?: return
    if (count <= 0 || sources.isEmpty()) {
      return
    }
    val start = index.coerceIn(0, sources.lastIndex)
    val endExclusive = (start + count).coerceAtMost(sources.size)
    if (endExclusive <= start) {
      return
    }
    val actualCount = endExclusive - start
    manager.removeRangeData(start, actualCount)
    sources.subList(start, endExclusive).clear()
    clampLastKnownIndex()
  }

  fun removeDataByIndexes(indexes: List<Int>) {
    val manager = host.dataManagerOrNull() ?: return
    if (indexes.isEmpty()) {
      return
    }
    val normalized = indexes.filter { it in sources.indices }.sorted()
    if (normalized.isEmpty()) {
      return
    }
    manager.removeDataByIndex(normalized)
    normalized.asReversed().forEach { sources.removeAt(it) }
    clampLastKnownIndex()
  }

  fun dataCount(): Int {
    return sources.size
  }

  fun snapshotAt(index: Int): WritableMap? {
    if (index !in sources.indices) {
      return null
    }
    return sources[index].toSnapshotMap()
  }

  fun currentIndex(): Int? {
    val dataManagerIndex = host.shortVideoView.dataManager?.currentIndex
    if (dataManagerIndex != null && dataManagerIndex >= 0) {
      return dataManagerIndex
    }
    if (host.lastKnownIndex in sources.indices) {
      return host.lastKnownIndex
    }
    return null
  }

  fun currentSourceSnapshot(): WritableMap? {
    val index = currentIndex() ?: return null
    if (index !in sources.indices) {
      val model = host.shortVideoView.currentModel ?: return null
      return model.toWritableMap()
    }
    return sources[index].toSnapshotMap()
  }

  fun findSourceMatch(model: TUIVideoSource): Pair<Int, TuiplayerShortVideoSource>? {
    sources.forEachIndexed { index, source ->
      if (source.matchesModel(model)) {
        return index to source
      }
    }
    val currentIndex = host.shortVideoView.dataManager?.currentIndex
    if (currentIndex != null && currentIndex in sources.indices) {
      val fallback = sources[currentIndex]
      return currentIndex to fallback
    }
    val lastKnown = host.lastKnownIndex
    if (lastKnown in sources.indices) {
      val fallback = sources[lastKnown]
      return lastKnown to fallback
    }
    return null
  }

  fun reset() {
    sources.clear()
    pendingResolved = null
    host.lastKnownIndex = -1
    host.lastEndReachedTotal = -1
    host.onPlaylistStateReset()
  }

  fun pendingResolvedCount(): Int {
    return pendingResolved?.size ?: 0
  }

  private fun resolvePlayableSources(
    rawSources: List<TuiplayerShortVideoSource>
  ): List<ResolvedSource> {
    if (rawSources.isEmpty()) {
      return emptyList()
    }
    val resolved = ArrayList<ResolvedSource>(rawSources.size)
    rawSources.forEach { source ->
      val model = source.toPlaySource()
      if (model != null) {
        resolved.add(ResolvedSource(source, model))
      } else {
        Log.w(
          TAG,
          "Drop source without playable payload (type=${source.type}, fileId=${source.fileId}, url=${source.url})"
        )
      }
    }
    return resolved
  }

  private fun applyResolvedSources(
    resolved: List<ResolvedSource>,
    reason: String,
    skipIdentityCheck: Boolean = false
  ) {
    if (!host.isViewReady) {
      storePendingSources(resolved, sources.isEmpty())
      return
    }
    val previous = sources.toList()
    val previousWasEmpty = previous.isEmpty()
    val newSources = resolved.map { it.source }

    when {
      previousWasEmpty -> replacePlaylist(resolved)
      previous == newSources && !skipIdentityCheck -> {
        playback.flushPendingStartCommand("$reason.identity")
        return
      }
      shouldAppend(previous, newSources) -> appendPlaylist(resolved.drop(previous.size))
      canUpdateInPlace(previous, newSources) -> updatePlaylistInPlace(resolved)
      else -> replacePlaylist(resolved)
    }

    sources = newSources.toMutableList()
    finalizePlaylistChange(reason, previousWasEmpty)
  }

  private fun shouldAppend(
    previous: List<TuiplayerShortVideoSource>,
    incoming: List<TuiplayerShortVideoSource>
  ): Boolean {
    if (incoming.size <= previous.size) {
      return false
    }
    return previous == incoming.subList(0, previous.size)
  }

  private fun canUpdateInPlace(
    previous: List<TuiplayerShortVideoSource>,
    incoming: List<TuiplayerShortVideoSource>
  ): Boolean {
    if (previous.size != incoming.size) {
      return false
    }
    return previous.indices.all { previous[it] === incoming[it] || previous[it].hasSamePlayableIdentity(incoming[it]) }
  }

  private fun storePendingSources(resolved: List<ResolvedSource>, wasEmpty: Boolean) {
    pendingResolved = resolved.toMutableList()
    sources = resolved.map { it.source }.toMutableList()
    host.lastKnownIndex = -1
    host.lastEndReachedTotal = -1
    if (wasEmpty && resolved.isNotEmpty() && host.autoPlayEnabled && !playback.hasPendingStartCommand()) {
      playback.setPendingStart(0, false, "pendingSources", autoFlush = false)
    }
  }

  private fun queuePendingAppend(resolved: List<ResolvedSource>) {
    val pending = pendingResolved ?: mutableListOf()
    pending.addAll(resolved)
    pendingResolved = pending
    sources = pending.map { it.source }.toMutableList()
  }

  private fun appendPlaylist(appended: List<ResolvedSource>) {
    if (appended.isEmpty()) {
      return
    }
    val models = appended.map { it.model }
    val result = host.shortVideoView.appendModels(models)
    if (result != TUIErrorCode.TUI_ERROR_NONE) {
      Log.e(TAG, "appendModels failed (result=$result, appended=${models.size})")
    } else {
      Log.d(TAG, "appendPlaylist appended=${models.size}")
    }
  }

  private fun replacePlaylist(resolved: List<ResolvedSource>) {
    val models = resolved.map { it.model }
    playback.detachCurrentVodPlayer()
    val result = host.shortVideoView.setModels(models)
    if (result != TUIErrorCode.TUI_ERROR_NONE) {
      Log.e(TAG, "setModels failed (result=$result, count=${models.size})")
    } else {
      Log.d(TAG, "replacePlaylist models=${models.size}")
    }
    host.lastEndReachedTotal = -1
    if (models.isEmpty()) {
      host.lastKnownIndex = -1
    }
  }

  private fun updatePlaylistInPlace(resolved: List<ResolvedSource>) {
    val manager = host.dataManagerOrNull()
    val currentIndex = currentIndex()
    resolved.forEachIndexed { index, entry ->
      val existing = sources.getOrNull(index) ?: return@forEachIndexed
      val incoming = entry.source
      if (existing == incoming || !existing.hasSamePlayableIdentity(incoming)) {
        return@forEachIndexed
      }
      if (index == currentIndex) {
        host.notifyOverlayUpdated(index, incoming)
      } else {
        manager?.replaceData(entry.model, index)
      }
      host.notifyPlaylistItemChanged(index)
    }
  }

  private fun finalizePlaylistChange(reason: String, previousWasEmpty: Boolean) {
    playback.maybeApplyInitialIndex()
    clampLastKnownIndex()
    ensureKnownIndexInitialized()
    if (sources.isNotEmpty() && !playback.hasActiveVodPlayer()) {
      val target = if (host.lastKnownIndex in sources.indices) host.lastKnownIndex else 0
      playback.ensureInitialPlayback(target, 1)
    }
    pendingResolved = null
    if (sources.isNotEmpty() && previousWasEmpty && host.autoPlayEnabled && !playback.hasPendingStartCommand()) {
      playback.setPendingStart(0, false, reason)
    } else {
      playback.flushPendingStartCommand(reason)
    }
  }

  private fun clampLastKnownIndex() {
    if (sources.isEmpty()) {
      host.lastKnownIndex = -1
      return
    }
    if (host.lastKnownIndex >= sources.size) {
      host.lastKnownIndex = sources.lastIndex
    }
  }

  private fun ensureKnownIndexInitialized() {
    if (host.lastKnownIndex >= 0 || sources.isEmpty()) {
      return
    }
    val index = host.shortVideoView.dataManager?.currentIndex ?: 0
    host.lastKnownIndex = index
    host.dispatchPageChanged(index, sources.size)
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
  android.util.Log.d("TuiplayerMeta", "mergeMetadata - current.type: ${current?.type}, update.type: ${update.type}")
  val resolvedType = if (update.type.isNullOrEmpty()) current?.type else update.type
  
  val result = TuiplayerShortVideoSource.Metadata(
    name = update.name ?: current?.name,
    icon = update.icon ?: current?.icon,
    type = resolvedType,
    details = update.details ?: current?.details,
    showCover = update.showCover ?: current?.showCover,
    playText = update.playText ?: current?.playText,
    moreText = update.moreText ?: current?.moreText,
    watchMoreText = update.watchMoreText ?: current?.watchMoreText,
    likeCount = update.likeCount ?: current?.likeCount,
    favoriteCount = update.favoriteCount ?: current?.favoriteCount,
    isShowPaly = update.isShowPaly ?: current?.isShowPaly,
    isLiked = update.isLiked ?: current?.isLiked,
    isBookmarked = update.isBookmarked ?: current?.isBookmarked
  )
  
  android.util.Log.d("TuiplayerMeta", "mergeMetadata - result.type: ${result.type}")
  return result
}
