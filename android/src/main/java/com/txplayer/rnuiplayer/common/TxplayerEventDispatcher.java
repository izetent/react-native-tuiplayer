package com.txplayer.rnuiplayer.common;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public final class TxplayerEventDispatcher {

  @Nullable
  private static ReactApplicationContext reactContext;

  private TxplayerEventDispatcher() {}

  public static void init(ReactApplicationContext context) {
    reactContext = context;
  }

  public static void emit(String eventName, WritableMap params) {
    if (reactContext == null) {
      return;
    }
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  public static WritableMap createParams() {
    return Arguments.createMap();
  }
}
