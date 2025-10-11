# react-native-tuiplayer

TUIPlayer 短视频组件的 React Native 封装，提供 Android/iOS 新架构支持。

## Installation


```sh
npm install react-native-tuiplayer
```


## Usage


```ts
import React, { useEffect } from 'react';
import {
  initialize,
  TuiplayerShortVideoView,
  type ShortVideoSource,
} from 'react-native-tuiplayer';

const sources: ShortVideoSource[] = [
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294394366256',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/3d98015b387702294394366256/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
];

export function Player() {
  useEffect(() => {
    initialize({
      licenseKey: process.env.TUIPLAYER_LICENSE_KEY ?? '',
      licenseUrl: process.env.TUIPLAYER_LICENSE_URL ?? '',
      enableLog: true,
    });
  }, []);

  return <TuiplayerShortVideoView sources={sources} style={{ flex: 1 }} />;
}
```

### License

请在腾讯云 [License 管理](https://console.cloud.tencent.com/vcube/mobile) 中获取移动端高级播放器 License，将 `licenseKey` 与 `licenseUrl` 传给 `initialize`。如果未设置 License，SDK 会无法正常播放。

### iOS 手动设置

Pod 安装后会自动引入 `TUIPlayerCore` 与 `TUIPlayerShortVideo` 的 xcframework。确保项目启用了 Swift，并在 `AppDelegate.mm` 中启用 React Native 新架构（RN ≥ 0.79 默认开启）。

### Android 配置

- `android/libs` 中已包含 `tuiplayercore` 与 `tuiplayershortvideo` aar。
- 默认使用 `minSdkVersion 21`，需要在宿主应用中设置对应的 LicenseKey/LicenseURL（通过 JS 调用即可）。


## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
