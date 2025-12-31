# react-native-txplayer

React Native bindings for [Tencent Cloud TUIPlayerKit](https://www.tencentcloud.com/document/product/266/60790) short‑video feed.  
This package mirrors the official RN plugin (`TUIPlayerKit_Flutter`) and exposes the same short‑video experience (ultra fast first frame, preload & SR strategy, scroll feed binding).

> **Important:** TUIPlayer requires a valid Tencent Cloud license.  
> Request a trial or production license in the Tencent Cloud console and configure it through `setTUIPlayerConfig` before creating controllers.

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

## API 速览（实事求是）

### 初始化

- `setTUIPlayerConfig(config: RNPlayerConfig)`  
  传入 licenseUrl / licenseKey（必填）和 enableLog，必须在创建任何 controller 前调用。
- `setMonetAppInfo(appId, authId, srAlgorithmType)`  
  可选，开启腾讯 Monet 超分（需要对应授权）。

### 组件

- `RNPlayerView`  
  原生播放器容器，必须用 ref 传给控制器绑定：`const ref = useRef(null); <RNPlayerView ref={ref} />`

### 短视频控制器 `RNPlayerShortController`

| 方法 | 说明 |
| --- | --- |
| `setModels(sources: RNVideoSource[])` / `appendModels(...)` | 重置/追加列表数据 |
| `bindVodPlayer(viewRef, index)` | 绑定指定索引到某个 `RNPlayerView`，返回 `TUIVodPlayerController` |
| `preCreateVodPlayer(viewRef, index)` | 预绑定相邻项以加速切换 |
| `setVodStrategy(strategy)` | 设置渲染模式、预下载/预加载、SR 等策略 |
| `startCurrent()` | 开始当前索引播放 |
| `setVideoLoop(isLoop)` | 设置循环 |
| `switchResolution(resolution, switchType)` | 切换清晰度（配合 `getSupportResolution` 使用） |
| `release()` | 释放所有播放器/资源 |

### 单个播放器控制器 `TUIVodPlayerController`

- 播控：`startPlay(source)`, `pause()`, `resume()`, `seekTo(time)`  
  速率/静音/镜像/渲染：`setRate(rate)`, `setMute(mute)`, `setMirror(mirror)`, `setRenderMode(mode)`  
  自定义字符串选项：`setStringOption(value, key)`
- 清晰度：`getSupportResolution()`，`switchResolution(resolution, switchType?)`
- 字幕：`selectSubtitleTrack(trackIndex)`（-1 隐藏）
- 查询：`getDuration()`, `getCurrentPlayTime()`, `isPlaying()`
- 生命周期：`release()`
- 事件：`addListener({ onSubtitleTracksUpdate, onVodPlayerEvent, onVodControllerBind, onVodControllerUnBind, onRcvFirstIframe, onPlayBegin, onPlayEnd })`

### `RNVideoSource` 关键字段

- 播放源：`videoURL` 或 `fileId`（配 `appId`/`pSign`）
- 画面：`coverPictureUrl`
- 字幕：`subtitleSources?: { url; mimeType; name? }[]`（需要 LiteAV Premium 版本支持原生字幕轨道）
- 其他：`extInfo` 自定义透传，`isAutoPlay` 自动播放

### 字幕现状（务必阅读）

- 原生字幕：`onSubtitleTracksUpdate` 会在轨道到达时触发，`selectSubtitleTrack(idx)` 将原生 `TXSubtitleView` 绑定到播放器。需要 LiteAV Premium 才有字幕轨道。
- 示例 App：为了避免原生层级问题，示例默认 `selectSubtitleTrack(-1)` 关闭原生字幕，并在 RN 覆盖层自行解析渲染 `subtitleSources[0]`（见 `example/src/App.tsx` 的自绘逻辑）。如果你要启用原生字幕，请移除该行并在轨道回调中调用 `selectSubtitleTrack`。
- iOS：目前仅暴露字幕相关 API，原生渲染未完全打通，推荐使用 RN 覆盖层自绘。
- 自定义样式：原生 `TXSubtitleView` 样式能力有限，若需自定义字号/颜色/背景，建议使用 RN 自绘字幕（参考示例）。

### 清晰度切换示例

```ts
const items = await vodController.getSupportResolution();
// 选择一档分辨率（SDK 返回的分辨率值或 bitrate item 的 index）
await vodController.switchResolution(items[0]?.index ?? 0);
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
