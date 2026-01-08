package com.txplayer.rnuiplayer.player.event;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerController;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIFileVideoInfo;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource;
import com.tencent.qcloud.tuiplayer.core.api.ui.player.ITUIVodPlayer;
import com.tencent.qcloud.tuiplayer.core.api.ui.player.TUIVodObserver;
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseVideoView;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerBitrateItem;
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants;
import com.tencent.qcloud.tuiplayer.core.api.ui.view.vod.TUIVodViewListener;
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoItemView;
import com.txplayer.rnuiplayer.common.RNConstant;
import com.txplayer.rnuiplayer.common.TxplayerEventDispatcher;
import com.txplayer.rnuiplayer.tools.RNUtils;
import com.txplayer.rnuiplayer.view.RNShortVideoItemView;
import com.tencent.rtmp.TXTrackInfo;
import com.tencent.rtmp.ui.TXSubtitleView;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;

public class RNVodController
    implements TUIVodViewListener, TUIVodObserver {

  // TX/TUI SDK render mode constants: 0 = FULL_FILL_SCREEN, 1 = ADJUST_RESOLUTION.
  private static final int RENDER_MODE_FIT = 1; // 等比
  private static final int RENDER_MODE_FILL = 0; // 填充

  private final ReactContext reactContext;
  private final RNShortVideoItemView parentView;
  private TUIPlayerController controller;
  private TUIVideoSource curSource;
  private final List<TXTrackInfo> lastSubtitleTracks = new ArrayList<>();
  private int selectedSubtitleTrack = -1;
  private int currentRenderMode = RENDER_MODE_FIT;
  private int videoWidth = 0;
  private int videoHeight = 0;
  private int sizeRetryCount = 0;

  public RNVodController(ReactContext context, RNShortVideoItemView parentView) {
    this.reactContext = context;
    this.parentView = parentView;
  }

  private int getViewTag() {
    return parentView.getId();
  }

  private void emitEvent(String eventName, @Nullable Map<String, Object> payload) {
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getViewTag());
    if (payload != null) {
      params.putMap("event", Arguments.makeNativeMap(payload));
    }
    TxplayerEventDispatcher.emit(eventName, params);
  }

  @Override
  public void onControllerPlayerAttached(TUIPlayerController tuiPlayerController) {}

  @Override
  public void onPlayerControllerBind(TUIPlayerController playerController) {
    controller = playerController;
    controller.addPlayerObserver(this);
    videoWidth = 0;
    videoHeight = 0;
    sizeRetryCount = 0;
    applyRenderMode(currentRenderMode);
    emitEvent(RNConstant.EVENT_CONTROLLER_BIND, null);
  }

  @Override
  public void onPlayerControllerUnBind(TUIPlayerController playerController) {
    controller = null;
    parentView.hideSubtitleLayer();
    parentView.resetVideoSize();
    sizeRetryCount = 0;
    emitEvent(RNConstant.EVENT_CONTROLLER_UNBIND, null);
  }

  @Override
  public void onBindData(TUIVideoSource tuiVideoSource) {
    curSource = tuiVideoSource;
  }

  @Override
  public void onExtInfoChanged(TUIVideoSource tuiVideoSource) {
    curSource = tuiVideoSource;
  }

  @Override
  public void onViewRecycled(TUIBaseVideoView tuiBaseVideoView) {}

  @Override
  public void onShortVideoDestroyed() {
    onShortVideoDestroyedInternal();
  }

  public void onShortVideoDestroyedInternal() {
    if (controller != null) {
      controller.stop();
    }
    videoWidth = 0;
    videoHeight = 0;
    parentView.resetVideoSize();
  }

  public void startPlay(@NonNull TUIVideoSource source) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
      player.startPlay(source);
    }
  }

  public void pause() {
    if (controller != null) {
      controller.pause();
    }
  }

  public void resume() {
    if (controller != null) {
      controller.resume();
    }
  }

  public void setRate(double rate) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ((ITUIVodPlayer) controller.getPlayer()).setRate((float) rate);
    }
  }

  public void switchResolution(long resolution, int switchType) {
    if (controller == null || !(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
    if (switchType == TUIConstants.TUIResolutionType.GLOBAL) {
      player.onGlobalResolutionChanged(resolution);
    }
    player.switchResolution(resolution);
  }

  public List<TUIPlayerBitrateItem> getSupportResolution() {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      List<TUIPlayerBitrateItem> list =
          ((ITUIVodPlayer) controller.getPlayer()).getSupportResolution();
      return list != null ? list : Collections.emptyList();
    }
    return Collections.emptyList();
  }

  public void setMirror(boolean mirror) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ((ITUIVodPlayer) controller.getPlayer()).setMirror(mirror);
    }
  }

  public void setMute(boolean mute) {
    if (controller != null && controller.getPlayer() != null) {
      controller.getPlayer().setMute(mute);
    }
  }

  public void setRenderMode(int renderMode) {
    currentRenderMode = renderMode;
    applyRenderMode(renderMode);
  }

  public void seekTo(double time) {
    if (controller != null) {
      controller.seekTo((float) time);
    }
  }

  public void setStringOption(String value, @Nullable Object key) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ((ITUIVodPlayer) controller.getPlayer()).setStringOption(value, key);
    }
  }

  public double getDuration() {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      return ((ITUIVodPlayer) controller.getPlayer()).getDuration();
    }
    return 0;
  }

  public double getCurrentPlayTime() {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      return ((ITUIVodPlayer) controller.getPlayer()).getCurrentPlayTime();
    }
    return 0;
  }

  public boolean isPlaying() {
    return controller != null && controller.isPlaying();
  }

  public void release() {
    onShortVideoDestroyedInternal();
    if (curSource != null) {
      curSource.attachView(null);
    }
  }

  @Override
  public void onPlayEvent(ITUIVodPlayer player, int event, Bundle bundle) {
    Map<String, Object> payload = RNUtils.getParams(event, bundle);
    maybeUpdateVideoSize(bundle);
    emitEvent(RNConstant.EVENT_PLAY_EVENT, payload);
  }

  @Override
  public void onPlayPrepare() {}

  @Override
  public void onPlayBegin() {
    updateSizeFromPlayer();
    if (!lastSubtitleTracks.isEmpty()) {
      parentView.post(
          () -> {
            attachSubtitleTrackInternal(selectedSubtitleTrack);
          });
    }
    applyRenderMode(currentRenderMode);
  }

  @Override
  public void onPlayLoading() {}

  @Override
  public void onPlayLoadingEnd() {
    updateSizeFromPlayer();
    if (!lastSubtitleTracks.isEmpty() && selectedSubtitleTrack >= 0) {
      parentView.post(
          () -> attachSubtitleTrackInternal(selectedSubtitleTrack));
    }
    applyRenderMode(currentRenderMode);
  }

  @Override
  public void onPlayProgress(long l, long l1, long l2) {}

  @Override
  public void onSeek(float v) {}

  @Override
  public void onError(int i, String s, Bundle bundle) {
    Map<String, Object> payload = RNUtils.getParams(i, bundle);
    emitEvent(RNConstant.EVENT_PLAY_EVENT, payload);
  }

  @Override
  public void onRcvFirstIframe() {}

  @Override
  public void onRcvAudioTrackInformation(List<TXTrackInfo> list) {}

  @Override
  public void onRcvTrackInformation(List<TXTrackInfo> list) {}

  @Override
  public void onRcvSubTitleTrackInformation(List<TXTrackInfo> list) {
    if (controller == null || controller.getPlayer() == null || list == null || list.isEmpty()) {
      parentView.hideSubtitleLayer();
      return;
    }
    List<TXTrackInfo> tracksCopy = new ArrayList<>(list);
    lastSubtitleTracks.clear();
    lastSubtitleTracks.addAll(tracksCopy);
    // Default to no native subtitle; RN侧自行渲染。
    selectedSubtitleTrack = -1;
    parentView.post(
        () -> {
          if (controller == null || controller.getPlayer() == null || tracksCopy.isEmpty()) {
            parentView.hideSubtitleLayer();
            return;
          }
          emitSubtitleTracks(tracksCopy);
        });
  }

  @Override
  public void onRecFileVideoInfo(TUIFileVideoInfo tuiFileVideoInfo) {}

  @Override
  public void onResolutionChanged(long width, long height) {
    if (width > 0 && height > 0) {
      parentView.updateVideoSize((int) width, (int) height);
    }
  }

  @Override
  public void onFirstFrameRendered() {
    updateSizeFromPlayer();
    if (controller != null
        && controller.getPlayer() != null
        && !lastSubtitleTracks.isEmpty()
        && selectedSubtitleTrack >= 0) {
      parentView.post(() -> attachSubtitleTrackInternal(selectedSubtitleTrack));
    }
    applyRenderMode(currentRenderMode);
    emitEvent(
        RNConstant.EVENT_PLAY_EVENT,
        RNUtils.getParams(RNConstant.PLAY_EVT_FIRST_FRAME_RENDERED, new Bundle()));
  }

  @Override
  public void onPlayEnd() {}

  @Override
  public void onRetryConnect(int i, Bundle bundle) {}

  @Override
  public void onPlayPause() {}

  @Override
  public void onPlayStop() {}

  public void selectSubtitleTrack(int trackIndex) {
    if (controller == null || controller.getPlayer() == null) {
      return;
    }
    if (!(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    selectedSubtitleTrack = trackIndex;
    parentView.post(() -> attachSubtitleTrackInternal(trackIndex));
  }

  private void attachSubtitleTrackInternal(int trackIndex) {
    if (controller == null || controller.getPlayer() == null) {
      return;
    }
    if (!(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
    if (trackIndex < 0) {
      player.setSubtitleView(null);
      parentView.hideSubtitleLayer();
    } else {
      TXSubtitleView subtitleView = parentView.getSubtitleView();
      player.setSubtitleView(subtitleView);
      player.selectTrack(trackIndex);
      parentView.showSubtitleLayer();
    }
  }

  private void emitSubtitleTracks(List<TXTrackInfo> tracks) {
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getViewTag());
    WritableArray trackArray = Arguments.createArray();
    for (TXTrackInfo info : tracks) {
      WritableMap trackMap = Arguments.createMap();
      trackMap.putInt("trackIndex", info.trackIndex);
      if (info.language != null) {
        trackMap.putString("language", info.language);
      }
      String trackName = info.getName();
      if (trackName != null) {
        trackMap.putString("name", trackName);
      }
      trackMap.putInt("type", info.trackType);
      trackArray.pushMap(trackMap);
    }
    params.putArray("tracks", trackArray);
    TxplayerEventDispatcher.emit(RNConstant.EVENT_SUBTITLE_TRACKS, params);
  }

  private void applyRenderMode(int rawMode) {
    if (controller == null || !(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    int mode = rawMode == 2 ? RENDER_MODE_FILL : RENDER_MODE_FIT;
    ((ITUIVodPlayer) controller.getPlayer()).setRenderMode(mode);
    // renderMode==fit 时遵循视频宽高比，否则放开比例
    parentView.applyCurrentResizeMode();
  }

  private void maybeUpdateVideoSize(@Nullable Bundle bundle) {
    if (bundle == null) {
      return;
    }
    int width = bundle.getInt("EVT_WIDTH", 0);
    int height = bundle.getInt("EVT_HEIGHT", 0);
    if (width == 0 && height == 0) {
      width = bundle.getInt("EVT_PARAM1", 0);
      height = bundle.getInt("EVT_PARAM2", 0);
    }
    if (width == 0 && height == 0) {
      // Some SDKs report as double.
      double w = bundle.getDouble("EVT_WIDTH", 0);
      double h = bundle.getDouble("EVT_HEIGHT", 0);
      if (w == 0 && h == 0) {
        w = bundle.getDouble("EVT_PARAM1", 0);
        h = bundle.getDouble("EVT_PARAM2", 0);
      }
      width = (int) Math.round(w);
      height = (int) Math.round(h);
    }
    if (width > 0 && height > 0) {
      videoWidth = width;
      videoHeight = height;
      parentView.updateVideoSize(width, height);
    } else {
      updateSizeFromPlayer();
    }
  }

  private void updateSizeFromPlayer() {
    if (controller == null || !(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
    int w = player.getWidth();
    int h = player.getHeight();
    if (w > 0 && h > 0 && (w != videoWidth || h != videoHeight)) {
      videoWidth = w;
      videoHeight = h;
      parentView.updateVideoSize(w, h);
      sizeRetryCount = 0;
    } else {
      if (sizeRetryCount < 10) {
        sizeRetryCount++;
        parentView.postDelayed(this::updateSizeFromPlayer, 100);
      }
    }
  }
}
