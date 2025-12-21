import { NativeEventEmitter } from 'react-native';

import NativeTxplayer from './NativeTxplayer';

export const TxplayerEventEmitter = new NativeEventEmitter(
  NativeTxplayer as any
);
