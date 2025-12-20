package com.txplayer.rnuiplayer.player;

import android.content.Context;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.tencent.qcloud.tuiplayer.core.TUIPlayerCore;
import com.tencent.qcloud.tuiplayer.core.tools.TUIPlayerLog;
import com.txplayer.rnuiplayer.tools.RNTransformer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RNShortEngine implements RNShortEngineObserver {

  private static final String TAG = "RNShortEngine";

  private final ReactApplicationContext reactContext;
  private final AtomicInteger idProvider = new AtomicInteger(0);
  private final Map<Integer, RNShortController> controllers = new ConcurrentHashMap<>();

  public RNShortEngine(ReactApplicationContext context) {
    this.reactContext = context;
  }

  public int createShortController() {
    int controllerId = idProvider.getAndIncrement();
    RNShortController controller =
        new RNShortController(reactContext, controllerId, this);
    controllers.put(controllerId, controller);
    return controllerId;
  }

  @Nullable
  public RNShortController getController(int controllerId) {
    return controllers.get(controllerId);
  }

  public void setConfig(ReadableMap map) {
    TUIPlayerLog.i(TAG, "set player config");
    TUIPlayerCore.init(reactContext, RNTransformer.transformToConfig(map));
  }

  public void setMonetAppInfo(long appId, int authId, int srType) {
    TUIPlayerLog.i(TAG, "init monet app info");
    setAppInfoReflectively(appId, authId, srType);
  }

  @Override
  public void onRelease(int controllerId) {
    controllers.remove(controllerId);
  }

  private static void setAppInfoReflectively(long appId, int authId, int srId) {
    try {
      Class<?> monetPluginClass = Class.forName("com.tencent.liteav.monet.MonetPlugin");
      Method setAppInfoMethod =
          monetPluginClass.getDeclaredMethod("setAppInfo", long.class, int.class, int.class);
      setAppInfoMethod.setAccessible(true);
      setAppInfoMethod.invoke(null, appId, authId, srId);
    } catch (ClassNotFoundException e) {
      TUIPlayerLog.e(TAG, "MonetPlugin not found", e);
    } catch (NoSuchMethodException e) {
      TUIPlayerLog.e(TAG, "method setAppInfo not found", e);
    } catch (Exception e) {
      TUIPlayerLog.e(TAG, "setAppInfo failed", e);
    }
  }
}
