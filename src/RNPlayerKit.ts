import NativeTxplayer from './NativeTxplayer';
import { serializePlayerConfig } from './types';
import type { RNPlayerConfig } from './types';

export async function setTUIPlayerConfig(config: RNPlayerConfig) {
  await NativeTxplayer.setPlayerConfig(serializePlayerConfig(config));
}

export async function setMonetAppInfo(
  appId: number,
  authId: number,
  srAlgorithmType: number
) {
  void appId;
  void authId;
  void srAlgorithmType;
  console.warn(
    '[react-native-tuiplayer] setMonetAppInfo ignored: super resolution is disabled in this build.'
  );
  return;
}
