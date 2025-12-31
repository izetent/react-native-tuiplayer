package com.txplayer.rnuiplayer.view;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import androidx.annotation.NonNull;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants;
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoItemView;
import com.tencent.rtmp.ui.TXSubtitleView;
import com.txplayer.rnuiplayer.common.RNConstant;
import com.txplayer.rnuiplayer.common.TxplayerEventDispatcher;
import com.txplayer.rnuiplayer.player.event.RNVodController;

  public class RNShortVideoItemView extends FrameLayout {

  private final AspectRatioFrameLayout renderContainer;
  private final TUIShortVideoItemView itemView;
  private final RNVodController vodController;
  private final TXSubtitleView subtitleView;
  private int videoWidth = 0;
  private int videoHeight = 0;
  private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

  public RNShortVideoItemView(@NonNull Context context) {
    super(context);
    ReactContext reactContext = (ReactContext) context;
    renderContainer = new AspectRatioFrameLayout(context);
    renderContainer.setLayoutParams(
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    renderContainer.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    itemView =
        new TUIShortVideoItemView(context, TUIConstants.RenderViewType.TEXTURE_VIEW);
    itemView.setClickable(false);
    itemView.setFocusableInTouchMode(false);
    itemView.setLongClickable(false);
    renderContainer.addView(
        itemView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(renderContainer);
    subtitleView = new TXSubtitleView(context);
    subtitleView.setVisibility(View.GONE);
    LayoutParams subtitleParams =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    addView(subtitleView, subtitleParams);
    vodController = new RNVodController(reactContext, this);
    itemView.addVideoItemViewListener(vodController);
    itemView.createDisplayView();
  }

  public TUIShortVideoItemView getVideoItemView() {
    return itemView;
  }

  public RNVodController getVodController() {
    return vodController;
  }

  public TXSubtitleView getSubtitleView() {
    return subtitleView;
  }

  public void showSubtitleLayer() {
    subtitleView.setVisibility(View.VISIBLE);
    subtitleView.bringToFront();
  }

  public void hideSubtitleLayer() {
    subtitleView.setVisibility(View.GONE);
  }

  public void updateVideoSize(int width, int height) {
    if (width > 0 && height > 0) {
      videoWidth = width;
      videoHeight = height;
    }
    applyCurrentResizeMode();
  }

  public void applyCurrentResizeMode() {
    if (videoWidth > 0 && videoHeight > 0) {
      renderContainer.setAspectRatio((float) videoWidth / (float) videoHeight);
    } else {
      renderContainer.resetAspectRatio();
    }
    renderContainer.setResizeMode(resizeMode);
    renderContainer.requestLayout();
  }

  public void setVideoWidthProp(int width) {
    if (width > 0) {
      videoWidth = width;
    }
    applyCurrentResizeMode();
  }

  public void setVideoHeightProp(int height) {
    if (height > 0) {
      videoHeight = height;
    }
    applyCurrentResizeMode();
  }

  public void setResizeMode(String mode) {
    int target;
    if ("cover".equals(mode)) {
      target = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    } else {
      target = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    }
    if (resizeMode != target) {
      resizeMode = target;
      applyCurrentResizeMode();
    }
  }

  public void resetVideoSize() {
    videoWidth = 0;
    videoHeight = 0;
    renderContainer.resetAspectRatio();
  }

  public void dispose() {
    vodController.onShortVideoDestroyed();
    itemView.onViewDestroyed();
    subtitleView.setVisibility(View.GONE);
    resetVideoSize();
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getId());
    RNViewRegistry.unregister(getId());
    TxplayerEventDispatcher.emit(RNConstant.EVENT_VIEW_DISPOSED, params);
  }

  @Override
  public void setId(int id) {
    int oldId = getId();
    super.setId(id);
    if (oldId != id) {
      RNViewRegistry.unregister(oldId);
      RNViewRegistry.register(id, this);
    }
  }
}
