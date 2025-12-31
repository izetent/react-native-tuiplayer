package com.txplayer.rnuiplayer.view;

import androidx.annotation.NonNull;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
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

  @ReactProp(name = "resizeMode")
  public void setResizeMode(RNShortVideoItemView view, String resizeMode) {
    view.setResizeMode(resizeMode);
  }

  @ReactProp(name = "videoWidth")
  public void setVideoWidth(RNShortVideoItemView view, int width) {
    view.setVideoWidthProp(width);
  }

  @ReactProp(name = "videoHeight")
  public void setVideoHeight(RNShortVideoItemView view, int height) {
    view.setVideoHeightProp(height);
  }
}
