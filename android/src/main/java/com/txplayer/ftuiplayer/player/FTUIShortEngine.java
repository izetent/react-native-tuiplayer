package com.txplayer.ftuiplayer.player;

import android.content.Context;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.tencent.qcloud.tuiplayer.core.TUIPlayerCore;
import com.tencent.qcloud.tuiplayer.core.tools.TUIPlayerLog;
import com.txplayer.ftuiplayer.tools.FTUITransformer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FTUIShortEngine implements FTUIShortEngineObserver {

  private static final String TAG = "FTUIShortEngine";

  private final ReactApplicationContext reactContext;
  private final AtomicInteger idProvider = new AtomicInteger(0);
  private final Map<Integer, FTUIShortController> controllers = new ConcurrentHashMap<>();

  public FTUIShortEngine(ReactApplicationContext context) {
    this.reactContext = context;
  }

  public int createShortController() {
    int controllerId = idProvider.getAndIncrement();
    FTUIShortController controller =
        new FTUIShortController(reactContext, controllerId, this);
    controllers.put(controllerId, controller);
    return controllerId;
  }

  @Nullable
  public FTUIShortController getController(int controllerId) {
    return controllers.get(controllerId);
  }

  public void setConfig(ReadableMap map) {
    TUIPlayerLog.i(TAG, "set player config");
    TUIPlayerCore.init(reactContext, FTUITransformer.transformToConfig(map));
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
