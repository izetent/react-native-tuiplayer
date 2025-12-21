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

  private final TUIShortVideoItemView itemView;
  private final RNVodController vodController;
  private final TXSubtitleView subtitleView;

  public RNShortVideoItemView(@NonNull Context context) {
    super(context);
    ReactContext reactContext = (ReactContext) context;
    itemView =
        new TUIShortVideoItemView(context, TUIConstants.RenderViewType.TEXTURE_VIEW);
    itemView.setClickable(false);
    itemView.setFocusableInTouchMode(false);
    itemView.setLongClickable(false);
    addView(itemView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    subtitleView = new TXSubtitleView(context);
    subtitleView.setVisibility(View.GONE);
    LayoutParams subtitleParams =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    subtitleParams.bottomMargin = (int) (context.getResources().getDisplayMetrics().density * 40);
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
  }

  public void hideSubtitleLayer() {
    subtitleView.setVisibility(View.GONE);
  }

  public void dispose() {
    vodController.onShortVideoDestroyed();
    itemView.onViewDestroyed();
    subtitleView.setVisibility(View.GONE);
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
