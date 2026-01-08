# react-native-txplayer

React Native 封装了 [腾讯云 TUIPlayerKit](https://www.tencentcloud.com/document/product/266/60790) 的短视频播放能力，提供超快首帧、预下载/超分策略、滚动列表绑定等体验，与官方 RN 插件（`TUIPlayerKit_Flutter`）保持一致。

> **重要：**TUIPlayer 需要有效的腾讯云 License。  
> 请在腾讯云控制台申请试用或正式 License，并在创建任何控制器前调用 `setTUIPlayerConfig` 完成配置。

## Installation

```sh
yarn add react-native-txplayer
# iOS
cd ios && pod install
```

The package pulls the required native SDKs (`TXLiteAVSDK_Player_Premium`, `TUIPlayerCore`, `TUIPlayerShortVideo`, `TXCMonetPlugin`, `tsrClient`) automatically through Gradle/CocoaPods.

## Quick start

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
  const viewRef = useRef(null);
  const controllerRef = useRef<RNPlayerShortController>();

  useEffect(() => {
    async function bootstrap() {
      await setTUIPlayerConfig({
        licenseUrl: 'YOUR_URL',
        licenseKey: 'YOUR_KEY',
      });
      const controller = new RNPlayerShortController();
      controllerRef.current = controller;
      await controller.setModels(SOURCES);
      const vod = await controller.bindVodPlayer(viewRef, 0);
      await controller.startCurrent();
      // vod exposes pause/resume/seek/isPlaying APIs & event listeners
    }
    bootstrap();
    return () => controllerRef.current?.release();
  }, []);

  return <RNPlayerView ref={viewRef} style={{ flex: 1 }} />;
}
```

## API 使用

### 初始化

- `setTUIPlayerConfig(config: RNPlayerConfig)`  
  必须在创建任何 controller 前调用；`enableLog` 未传时默认为 `true`。  
  | 字段 | 说明 |
  | --- | --- |
  | `licenseUrl` | 腾讯云控制台发放的 License URL |
  | `licenseKey` | License Key |
  | `enableLog?` | 是否输出原生日志（默认 `true`） |
- `setMonetAppInfo(appId, authId, srAlgorithmType)`  
  可选，开启 Monet 超级分辨率；`srAlgorithmType` 推荐使用 `RNMonetConstant`：`SR_ALGORITHM_TYPE_STANDARD` / `SR_ALGORITHM_TYPE_PROFESSIONAL_HIGH_QUALITY` / `SR_ALGORITHM_TYPE_PROFESSIONAL_FAST`。

### 组件 `RNPlayerView`

- 原生播放器容器，必须通过 `ref` 或 `findNodeHandle` 传给控制器绑定。
- 额外可选属性：`resizeMode?: 'contain' | 'cover'`，`videoWidth?`，`videoHeight?`。
- 示例：`const ref = useRef(null); <RNPlayerView ref={ref} style={{ height: 480 }} resizeMode="cover" />`

### 短视频控制器 `RNPlayerShortController`

列表级控制器；每个列表实例化一个，页面卸载时调用 `release()`（多次调用安全）。

| 方法                                       | 参数                                       | 返回                              | 说明                                                    |
| ------------------------------------------ | ------------------------------------------ | --------------------------------- | ------------------------------------------------------- |
| `setModels(sources)`                       | `RNVideoSource[]`                          | `Promise<number>`                 | 重置播放列表                                            |
| `appendModels(sources)`                    | `RNVideoSource[]`                          | `Promise<number>`                 | 追加播放列表                                            |
| `bindVodPlayer(viewRef, index)`            | `ref` / `findNodeHandle` / `viewTag`，索引 | `Promise<TUIVodPlayerController>` | 绑定指定索引到 `RNPlayerView`，返回单个播放器控制器     |
| `preCreateVodPlayer(viewRef, index)`       | 同上                                       | `Promise<void>`                   | 预绑定邻接项，减少滑动延迟                              |
| `setVodStrategy(strategy)`                 | `RNPlayerVodStrategy`                      | `Promise<void>`                   | 未填字段按默认值补齐（见下表）                          |
| `startCurrent()`                           | -                                          | `Promise<number>`                 | 开始当前索引播放（需先绑定）                            |
| `setVideoLoop(isLoop)`                     | `boolean`                                  | `Promise<void>`                   | 开启/关闭循环                                           |
| `switchResolution(resolution, switchType)` | `number`, `RNTUIResolutionType`            | `Promise<void>`                   | 列表级切清晰度，`switchType` 建议用 `TUIResolutionType` |
| `release()`                                | -                                          | `Promise<void>`                   | 释放 native 短视频控制器及缓存                          |

**`RNPlayerVodStrategy` 默认值**（`serializeVodStrategy` 自动填充）：

| 字段                    | 默认值       | 说明                         |
| ----------------------- | ------------ | ---------------------------- |
| `preloadCount`          | `3`          | 预创建/预加载数量            |
| `preDownloadSize`       | `1`          | 预下载个数                   |
| `preloadBufferSizeInMB` | `0.5`        | 预加载缓冲大小               |
| `maxBufferSize`         | `10`         | 最大缓冲大小                 |
| `preferredResolution`   | `720 * 1280` | 期望分辨率                   |
| `progressInterval`      | `500`        | 进度事件间隔（ms）           |
| `renderMode`            | `1`          | LiteAV 渲染模式              |
| `enableSuperResolution` | `false`      | 是否启用 SR（需 Monet 授权） |

### 单个播放器控制器 `TUIVodPlayerController`

由 `bindVodPlayer` 返回，绑定到具体的 `RNPlayerView`。内部维护 `playerState`（`INIT/PLAYING/PAUSE/LOADING/END`），事件驱动更新。

| 方法                                                                     | 作用                                           |
| ------------------------------------------------------------------------ | ---------------------------------------------- |
| `startPlay(source)`                                                      | 手动播放指定源（一般由短视频控制器驱动）       |
| `pause()` / `resume()`                                                   | 暂停 / 恢复                                    |
| `seekTo(time)`                                                           | 跳转到指定秒数                                 |
| `setRate(rate)` / `setMute(mute)`                                        | 倍速、静音                                     |
| `setMirror(mirror)` / `setRenderMode(renderMode)`                        | 镜像、渲染模式                                 |
| `setStringOption(value, key)`                                            | 透传字符串配置到原生                           |
| `getSupportResolution()`                                                 | 获取清晰度/码率列表（`RNPlayerBitrateItem[]`） |
| `switchResolution(resolution, switchType?)`                              | 切换清晰度，默认 `TUIResolutionType.CURRENT`   |
| `selectSubtitleTrack(trackIndex)`                                        | 选择字幕轨道，`-1` 隐藏                        |
| `getDuration()` / `getCurrentPlayTime()` / `isPlaying()`                 | 查询时长、进度、播放状态                       |
| `addListener(listener)` / `removeListener(listener)` / `clearListener()` | 管理监听器                                     |
| `release()`                                                              | 释放当前播放器并清理缓存                       |

**监听器 `RNVodControlListener`**：

| 回调                                            | 触发时机                                 |
| ----------------------------------------------- | ---------------------------------------- |
| `onVodControllerBind` / `onVodControllerUnBind` | 控制器与 `RNPlayerView` 绑定/解绑        |
| `onRcvFirstIframe`                              | 收到首帧（`PLAY_EVT_RCV_FIRST_I_FRAME`） |
| `onPlayBegin` / `onPlayEnd`                     | 开始 / 完成播放                          |
| `onVodPlayerEvent`                              | 原始 `TXVodPlayEvent` 事件包             |
| `onSubtitleTracksUpdate(tracks)`                | 字幕轨道更新                             |

### 源数据 `RNVideoSource`

- 播放源：`videoURL` 或 `fileId`（配 `appId`/`pSign`）；`isAutoPlay` 默认 `true`。
- 画面：`coverPictureUrl`
- 字幕：`subtitleSources?: { url; mimeType; name? }[]`（Premium 版本支持原生轨道，不传则为 `null`）
- 其他：`extInfo` 透传到原生层。

### 常量与事件

- `RNMonetConstant`：`SR_ALGORITHM_TYPE_STANDARD` / `SR_ALGORITHM_TYPE_PROFESSIONAL_HIGH_QUALITY` / `SR_ALGORITHM_TYPE_PROFESSIONAL_FAST`。
- `TUIResolutionType`：`GLOBAL = -1`、`CURRENT = -2`，用于 `switchResolution` 的 `switchType`。
- `TXVodPlayEvent`：封装 LiteAV 播放事件码，常用如 `PLAY_EVT_RCV_FIRST_I_FRAME`、`PLAY_EVT_PLAY_BEGIN`、`PLAY_EVT_PLAY_END`、`PLAY_EVT_PLAY_LOADING`、`PLAY_EVT_VOD_LOADING_END`、`PLAY_ERR_NET_DISCONNECT`；事件包中键名如 `EVT_EVENT`/`event`、`EVT_TIME`、`EVT_PLAY_PROGRESS_MS`、`EVT_PLAY_DURATION_MS` 可直接读取。
- 事件总线（`TxplayerEventEmitter`）：事件名 `EVENT_PLAY_EVENT`、`EVENT_CONTROLLER_BIND`、`EVENT_CONTROLLER_UNBIND`、`EVENT_VIEW_DISPOSED`、`EVENT_SUBTITLE_TRACKS`。

### 字幕使用说明

1. 在 `RNVideoSource.subtitleSources` 中配置字幕地址与 MIME。
2. 监听 `onSubtitleTracksUpdate` 获取原生轨道列表。
3. 调用 `selectSubtitleTrack(trackIndex)` 选择轨道，传 `-1` 隐藏。
4. Android 默认使用原生 `TXSubtitleView` 渲染；示例 App 为避免层级问题默认关闭原生字幕并在 RN 覆盖层自绘。iOS 暂时仅暴露 API，建议自绘。

### 清晰度 & 超分示例

```ts
// 获取并切换清晰度
const items = await vodController.getSupportResolution();
await vodController.switchResolution(items[0]?.index ?? 0);

// 开启超分（需 Monet 授权）
await setMonetAppInfo(
  APP_ID,
  AUTH_ID,
  RNMonetConstant.SR_ALGORITHM_TYPE_STANDARD
);
await shortController.setVodStrategy({ enableSuperResolution: true });
```

## 常见注意事项

- **License 先配置**：未配置有效 license 时 `startCurrent()` 会返回 `TUI_ERROR_INVALID_LICENSE`。
- **资源释放**：页面卸载时调用 `vodController.release()` 和 `shortController.release()`。
- **依赖版本**：字幕轨道、SR 等能力需要 LiteAV Premium 套件，请确认原生依赖版本满足需求。

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT
