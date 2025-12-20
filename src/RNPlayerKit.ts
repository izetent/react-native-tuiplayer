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
  await NativeTxplayer.setMonetAppInfo(appId, authId, srAlgorithmType);
}
