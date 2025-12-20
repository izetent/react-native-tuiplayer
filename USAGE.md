# react-native-txplayer 使用指南

React Native 版的 [Tencent Cloud TUIPlayerKit](https://www.tencentcloud.com/document/product/266/60790) 短视频体验。组件封装自官方 Flutter 插件：提供 Feed 类竖屏短视频、首帧秒开、预加载以及超分策略等能力。

> ⚠️ **许可证必需**：播放 SDK 需要有效的腾讯云 license（`licenseUrl` + `licenseKey`）。没有配置许可证，`startCurrent` 会返回 `TUI_ERROR_INVALID_LICENSE`，播放器无法正常初始化。

## 1. 安装与构建准备

```sh
yarn add react-native-txplayer
# iOS 额外执行
cd ios && pod install
```

- **Android**：库自带 `.aar` 依赖（`android/libs/*.aar`）和 `LiteAVSDK_Player_Premium`，通过 Gradle 自动拉取。
- **iOS**：`Txplayer.podspec` 会为你下载 LiteAV SDK。若你的项目未开启自动 Pods，同步 `example/react-native.config.js` 中对依赖的 `platforms` 声明。

## 2. 初始化流程

1. **配置 License**：应用启动时调用 `setTUIPlayerConfig`。
2. **（可选）超级分辨率**：如果购买了 Monet 授权，调用 `setMonetAppInfo(appId, authId, srAlgorithmType)` 并在策略中启用 `enableSuperResolution`。
3. **创建控制器**：列表页面创建 `RNPlayerShortController`，并在离开页面时调用 `release`。
4. **绑定视图**：将 `RNPlayerView` 渲染在列表 Cell 中，通过 `bindVodPlayer`/`preCreateVodPlayer` 绑定 `viewTag`。

## 3. 快速上手示例

```tsx
import React, { useEffect, useRef } from 'react';
import {
  RNPlayerShortController,
  RNPlayerView,
  setTUIPlayerConfig,
  RNVideoSource,
} from 'react-native-txplayer';

const SOURCES: RNVideoSource[] = [
  { videoURL: 'https://liteavapp.qcloud.com/general/vod_demo/vod-demo.mp4' },
];

export default function FeedPlayer() {
  const playerRef = useRef(null);
  const controllerRef = useRef<RNPlayerShortController>();

  useEffect(() => {
    async function bootstrap() {
      await setTUIPlayerConfig({
        licenseUrl: 'YOUR_LICENSE_URL',
        licenseKey: 'YOUR_LICENSE_KEY',
      });
      const controller = new RNPlayerShortController();
      controllerRef.current = controller;
      await controller.setModels(SOURCES);
      const vod = await controller.bindVodPlayer(playerRef, 0);
      await controller.startCurrent();
      vod.addListener({
        onVodPlayerEvent: (event) => console.log('play event', event),
      });
    }
    bootstrap();
    return () => controllerRef.current?.release();
  }, []);

  return <RNPlayerView ref={playerRef} style={{ flex: 1 }} />;
}
```

## 4. API 速览

### `setTUIPlayerConfig(config: RNPlayerConfig)`
配置全局 License 以及日志选项，`RNPlayerConfig` 字段：

| 字段 | 说明 |
| --- | --- |
| `licenseUrl` | 腾讯云控制台发放的 License URL |
| `licenseKey` | License Key |
| `enableLog?` | 默认 `true`，是否在原生层输出日志 |

### `setMonetAppInfo(appId, authId, srAlgorithmType)`
初始化 Monet 超级分辨率。`srAlgorithmType` 可用 `RNMonetConstant` 中的常量：

- `SR_ALGORITHM_TYPE_STANDARD`
- `SR_ALGORITHM_TYPE_PROFESSIONAL_HIGH_QUALITY`
- `SR_ALGORITHM_TYPE_PROFESSIONAL_FAST`

### `RNPlayerShortController`
短视频列表控制器。每个列表实例化一个，并在页面销毁时 `release()`。

| 方法 | 作用 |
| --- | --- |
| `setModels(sources)` | 重置数据源 |
| `appendModels(sources)` | 追加视频数据 |
| `bindVodPlayer(viewRef, index)` | 将 `RNPlayerView` 绑定到指定索引，返回 `TUIVodPlayerController` |
| `preCreateVodPlayer(viewRef, index)` | 预创建/绑定邻接 Cell，提升切换速度 |
| `setVodStrategy(strategy)` | 设置预加载策略，详见下方 |
| `startCurrent()` | 播放当前索引 |
| `setVideoLoop(isLoop)` | 开启/关闭循环播放 |
| `release()` | 释放 native 控制器及缓存 |

`RNPlayerVodStrategy` 字段：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `preloadCount` | `3` | 预创建/预加载视频数量 |
| `preDownloadSize` | `1` | 预下载视频数 |
| `preloadBufferSizeInMB` | `0.5` | 预加载缓冲大小 |
| `maxBufferSize` | `10` | 最大缓冲大小 |
| `preferredResolution` | `720 * 1280` | 期望分辨率 |
| `progressInterval` | `500ms` | 播放进度回调间隔 |
| `renderMode` | `1` | 对应 LiteAV 渲染模式 |
| `enableSuperResolution` | `false` | 是否启用 SR（需 Monet 授权） |

### `RNPlayerView`
渲染实际视频画面的原生组件，需通过 `ref` 或 `findNodeHandle` 获取 `viewTag` 用于绑定。

### `TUIVodPlayerController`
`bindVodPlayer` 返回的实例，针对单个 `RNPlayerView` 控制播放：

- 控制：`startPlay(source)`, `pause()`, `resume()`, `seekTo(seconds)`, `setRate(rate)`, `setMute(boolean)`, `setStringOption(value, key)`
- 状态：`getDuration()`, `getCurrentPlayTime()`, `isPlaying()`, `playerState`
- 生命周期：`addListener(listener)`, `removeListener(listener)`, `clearListener()`, `release()`

监听器回调 `RNVodControlListener`：

```ts
{
  onVodControllerBind?: () => void;
  onVodControllerUnBind?: () => void;
  onVodPlayerEvent?: (event: RNVodEvent) => void;
}
```

事件 `event` 中的 `EVT_EVENT`/`event` 可以与 `TXVodPlayEvent` 常量比对，例如：

- `PLAY_EVT_RCV_FIRST_I_FRAME`：收到首帧
- `PLAY_EVT_PLAY_BEGIN`：开始播放
- `PLAY_EVT_PLAY_LOADING` / `PLAY_EVT_VOD_LOADING_END`：缓冲状态
- `PLAY_EVT_PLAY_END`：播放完成

### `RNVideoSource`
描述单个视频数据，常用字段：

| 字段 | 说明 |
| --- | --- |
| `videoURL` | http(s) 播放地址（与 fileId 至少填一个） |
| `coverPictureUrl` | 封面图 |
| `appId` + `fileId` | 点播 fileId 播放 |
| `pSign` | 私有 DRM 播放授权 |
| `isAutoPlay` | 默认 `true` |
| `extInfo` | 透传字典到 native 层 |

## 5. 事件与调试

- 所有原生事件通过 `TxplayerEventEmitter` 分发，事件名在 `src/types.ts` 中以 `EVENT_*` 常量导出。
- 需要手动监听时，可直接订阅 `EVENT_PLAY_EVENT` 观察底层 `TXVodPlayEvent` 码流。
- 播放视图销毁事件 `EVENT_VIEW_DISPOSED` 会自动释放缓存的 `TUIVodPlayerController`，无需额外处理。

## 6. 故障排查

| 现象 | 处理建议 |
| --- | --- |
| `UnsupportedGenericParserError` | 确保使用 0.1.0+ 版本（Native 类型定义已迁移至 TurboModule spec） |
| Gradle 无法解析 AAR | 确认 `android/build.gradle` 中的 `libs/*.aar` 未被删除，并在 `android/libs` 存在 |
| `TUI_ERROR_INVALID_LICENSE` | 检查 `licenseUrl`/`licenseKey` 是否匹配产品环境，或更换新证书 |
| SR 不生效 | 已调用 `setMonetAppInfo` 且策略 `enableSuperResolution=true`，需要真实设备验证 |

---

如需更详细的开发/贡献流程，请参见项目根目录的 `README.md` 与 `CONTRIBUTING.md`。
