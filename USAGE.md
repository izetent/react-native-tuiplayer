# react-native-tuiplayer 使用指南

React Native 版的 [Tencent Cloud TUIPlayerKit](https://www.tencentcloud.com/document/product/266/60790) 短视频体验。组件封装 RN 插件：提供 Feed 类竖屏短视频、首帧秒开、预加载以及超分策略等能力。

> ⚠️ **许可证必需**：播放 SDK 需要有效的腾讯云 license（`licenseUrl` + `licenseKey`）。没有配置许可证，`startCurrent` 会返回 `TUI_ERROR_INVALID_LICENSE`，播放器无法正常初始化。

## 1. 安装与构建准备

```sh
yarn add react-native-tuiplayer
# iOS 额外执行
cd ios && pod install
```

- **Android**：库自带 `.aar` 依赖（`android/libs/*.aar`）和 `LiteAVSDK_Player_Premium`，通过 Gradle 自动拉取。
- **iOS**：默认通过 CocoaPods 拉取 `TXLiteAVSDK_Player_Premium`。如果 `ios/TUIPlayerCoreSDK` 与 `ios/TUIPlayerShortVideoSDK` 存在，则优先使用本地 SDK；否则会尝试从 podspec 源解析 `TUIPlayerCore/Player_Premium` 与 `TUIPlayerShortVideo/Player_Premium`。若 `ios/TXLiteAVSDK_Player_Premium` 存在，则优先使用本地 LiteAV SDK 并跳过 `TXLiteAVSDK_Player_Premium` 解析。也可以设置 `TUIPLAYERKIT_IOS_SDK_ROOT` 与 `TXLITEAVSDK_ROOT` 指向外部 SDK 目录。若你的项目未开启自动 Pods，同步 `example/react-native.config.js` 中对依赖的 `platforms` 声明。

> iOS SDK 更新：将下载的 `TUIPlayerCoreSDK` 与 `TUIPlayerShortVideoSDK` 替换到 `node_modules/react-native-tuiplayer/ios/`（保持 `SDKProduct` 目录结构）。如需本地 LiteAV，放到 `node_modules/react-native-tuiplayer/ios/TXLiteAVSDK_Player_Premium/`，或设置 `TXLITEAVSDK_ROOT` 指向解压后的 SDK 根目录。

示例（使用外部 SDK 目录）：
```sh
TUIPLAYERKIT_IOS_SDK_ROOT=/Users/akx/Desktop/npm/tuiplayer/TUIPlayerKit_iOS_V2.1.0.96/SDK \
TXLITEAVSDK_ROOT=/path/to/TXLiteAVSDK_Player_Premium \
pod install
```
如需启用腾讯云私有 Pod 源，在 `example/ios/Podfile` 中通过 `USE_TENCENT_PODS=1` 打开。
- **Android 仓库模式注意**：如果 app 的 `settings.gradle` 使用了
  `dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) }`
  或 `FAIL_ON_PROJECT_REPOS`，需要在 `settings.gradle` 中添加 `flatDir`（路径按项目实际位置调整）：

```gradle
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
    flatDir { dirs("$rootDir/../node_modules/react-native-tuiplayer/android/libs") }
  }
}
```

## 2. 初始化流程

1. **配置 License**：应用启动时调用 `setTUIPlayerConfig`。
2. **（可选）超级分辨率**：当前版本已禁用超分相关逻辑（`setMonetAppInfo`/`enableSuperResolution` 不生效）。
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
} from 'react-native-tuiplayer';

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

| 字段         | 说明                              |
| ------------ | --------------------------------- |
| `licenseUrl` | 腾讯云控制台发放的 License URL    |
| `licenseKey` | License Key                       |
| `enableLog?` | 默认 `true`，是否在原生层输出日志 |

### `setMonetAppInfo(appId, authId, srAlgorithmType)`

当前版本已禁用超分相关逻辑（调用不会生效）。

### `RNPlayerShortController`

短视频列表控制器。每个列表实例化一个，并在页面销毁时 `release()`。

| 方法                                 | 作用                                                            |
| ------------------------------------ | --------------------------------------------------------------- |
| `setModels(sources)`                 | 重置数据源                                                      |
| `appendModels(sources)`              | 追加视频数据                                                    |
| `bindVodPlayer(viewRef, index)`      | 将 `RNPlayerView` 绑定到指定索引，返回 `TUIVodPlayerController` |
| `preCreateVodPlayer(viewRef, index)` | 预创建/绑定邻接 Cell，提升切换速度                              |
| `setVodStrategy(strategy)`           | 设置预加载策略，详见下方                                        |
| `startCurrent()`                     | 播放当前索引                                                    |
| `setVideoLoop(isLoop)`               | 开启/关闭循环播放                                               |
| `release()`                          | 释放 native 控制器及缓存                                        |

`RNPlayerVodStrategy` 字段：

| 字段                    | 默认值       | 说明                         |
| ----------------------- | ------------ | ---------------------------- |
| `preloadCount`          | `3`          | 预创建/预加载视频数量        |
| `preDownloadSize`       | `1`          | 预下载视频数                 |
| `preloadBufferSizeInMB` | `0.5`        | 预加载缓冲大小               |
| `maxBufferSize`         | `10`         | 最大缓冲大小                 |
| `preferredResolution`   | 不设置       | 期望分辨率（传入会锁定清晰度） |
| `progressInterval`      | `500ms`      | 播放进度回调间隔             |
| `renderMode`            | `1`          | 对应 LiteAV 渲染模式         |
| `enableSuperResolution` | `false`      | 暂时禁用（不生效）           |

**HLS 自适应注意事项**：
- 播放地址需要是 master playlist（包含多码率/多清晰度）。
- 不要设置 `preferredResolution`，自动模式下不要调用 `switchResolution`。
- 手动切档后可能锁定清晰度，如需恢复自适应可重新绑定/重建播放器。

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

| 字段               | 说明                                                                    |
| ------------------ | ----------------------------------------------------------------------- |
| `videoURL`         | http(s) 播放地址（与 fileId 至少填一个）                                |
| `coverPictureUrl`  | 封面图                                                                  |
| `appId` + `fileId` | 点播 fileId 播放                                                        |
| `pSign`            | 私有 DRM 播放授权                                                       |
| `isAutoPlay`       | 默认 `true`                                                             |
| `extInfo`          | 透传字典到 native 层                                                    |
| `subtitleSources`  | 外挂字幕数组，元素包含 `url`、`mimeType`、`name?`（Premium SDK 才支持） |

### 外挂字幕

1. 在 `RNVideoSource` 中配置 `subtitleSources`。
2. 在 UI 层监听 `TUIVodPlayerController.addListener` 并实现 `onSubtitleTracksUpdate`，可以拿到 `trackIndex`/`name` 等信息。
3. 调用 `controller.selectSubtitleTrack(trackIndex)` 选择对应字幕；传入 `-1` 可隐藏字幕。
4. Android 端会在 `TXSubtitleView` 中渲染字幕；iOS 暂时只暴露 API，需等待后续 SDK 能力。

## 5. 事件与调试

- 所有原生事件通过 `TxplayerEventEmitter` 分发，事件名在 `src/types.ts` 中以 `EVENT_*` 常量导出。
- 需要手动监听时，可直接订阅 `EVENT_PLAY_EVENT` 观察底层 `TXVodPlayEvent` 码流。
- 播放视图销毁事件 `EVENT_VIEW_DISPOSED` 会自动释放缓存的 `TUIVodPlayerController`，无需额外处理。

## 6. 故障排查

| 现象                            | 处理建议                                                                         |
| ------------------------------- | -------------------------------------------------------------------------------- |
| `UnsupportedGenericParserError` | 确保使用 0.1.0+ 版本（Native 类型定义已迁移至 TurboModule spec）                 |
| Gradle 无法解析 AAR             | 确认 `android/libs/*.aar` 存在；若使用 `PREFER_SETTINGS`/`FAIL_ON_PROJECT_REPOS`，在 `settings.gradle` 中添加 `flatDir` |
| `TUI_ERROR_INVALID_LICENSE`     | 检查 `licenseUrl`/`licenseKey` 是否匹配产品环境，或更换新证书                    |
| SR 不生效                       | 已调用 `setMonetAppInfo` 且策略 `enableSuperResolution=true`，需要真实设备验证   |

---

如需更详细的开发/贡献流程，请参见项目根目录的 `README.md` 与 `CONTRIBUTING.md`。
