# react-native-txplayer

React Native bindings for [Tencent Cloud TUIPlayerKit](https://www.tencentcloud.com/document/product/266/60790) short‑video feed.  
This package mirrors the official Flutter plugin (`TUIPlayerKit_Flutter`) and exposes the same short‑video experience (ultra fast first frame, preload & SR strategy, scroll feed binding).

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
      await setTUIPlayerConfig({ licenseUrl: 'YOUR_URL', licenseKey: 'YOUR_KEY' });
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

## API overview

### `setTUIPlayerConfig(config: RNPlayerConfig)`
Configures the global TUIPlayer license and logging behaviour. Must be called before instantiating controllers.

### `setMonetAppInfo(appId: number, authId: number, srAlgorithmType: number)`
Initialises Tencent Monet (super resolution) when you own the licence. The SR algorithm type constants are exposed via `RNMonetConstant`.

### `RNPlayerShortController`
Represents a short‑video feed controller (one per list). Methods mirror the Flutter plugin:

| Method | Description |
| --- | --- |
| `setModels(sources: RNVideoSource[])` | Resets the feed data set. |
| `appendModels(sources)` | Appends more videos for endless scrolling. |
| `bindVodPlayer(viewRef, index)` | Binds a `RNPlayerView` to a feed index and returns a `TUIVodPlayerController`. Call this for the current page. |
| `preCreateVodPlayer(viewRef, index)` | Prepares neighbour cells for instant playback. |
| `setVodStrategy(strategy: RNPlayerVodStrategy)` | Fine tune preload counts, buffer sizes, render mode and SR toggle. |
| `startCurrent()` | Resumes playback of the currently bound index. |
| `setVideoLoop(isLoop: boolean)` | Enables/Disables loop playback. |
| `release()` | Releases all players and preload resources. |

### `RNPlayerView`
Native view that renders the current `TUIShortVideoItemView`. Use `ref`/`findNodeHandle` when binding.

### `TUIVodPlayerController`
Returned by `bindVodPlayer`. Provides on-demand control and events:

* `pause()`, `resume()`, `seekTo(seconds)`, `setRate(rate)`, `setMute(true/false)`
* `getDuration()`, `getCurrentPlayTime()`, `isPlaying()`
* `addListener(listener)` — receives controller bind/unbind notifications and `onVodPlayerEvent` with `TXVodPlayEvent` codes. Remove with `removeListener`.

### `RNVideoSource`
Define one of `videoURL` or `fileId` (with `appId` & optional `pSign`). `coverPictureUrl` is shown before the first frame. `extInfo` allows passing custom metadata down to the native layer.

## Notes

- **Licensing:** Without a valid licence `startCurrent()` returns `TUI_ERROR_INVALID_LICENSE`. Configure `licenseUrl` + `licenseKey` before using this package.
- **Super Resolution:** Call `setMonetAppInfo` first, then toggle `RNPlayerVodStrategy.enableSuperResolution`.  
- **Platform parity:** The TypeScript surface mimics the Flutter plugin so existing business logic can be ported with minimal changes.

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT
