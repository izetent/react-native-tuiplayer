package com.txplayer.rnuiplayer.view;

import androidx.annotation.NonNull;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.txplayer.rnuiplayer.common.RNConstant;

public class RNPlayerViewManager extends SimpleViewManager<RNShortVideoItemView> {

  @NonNull
  @Override
  public String getName() {
    return RNConstant.VIEW_TYPE;
  }

  @NonNull
  @Override
  protected RNShortVideoItemView createViewInstance(@NonNull ThemedReactContext context) {
    return new RNShortVideoItemView(context);
  }

  @Override
  public void onDropViewInstance(@NonNull RNShortVideoItemView view) {
    view.dispose();
    super.onDropViewInstance(view);
  }
}
