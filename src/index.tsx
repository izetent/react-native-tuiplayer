import { useMemo } from 'react';
import { StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import NativeTuiplayer from './NativeTuiplayer';
import TuiplayerShortVideoNativeComponent, {
  type NativeShortVideoSource,
} from './TuiplayerShortVideoViewNativeComponent';
import type { ShortVideoSource, TuiplayerLicenseConfig } from './types';

export const initialize = (config: TuiplayerLicenseConfig) => {
  NativeTuiplayer.initialize(config);
};

export type TuiplayerShortVideoViewProps = {
  sources: ShortVideoSource[];
  autoPlay?: boolean;
  style?: StyleProp<ViewStyle>;
};

export const TuiplayerShortVideoView = ({
  sources,
  autoPlay = true,
  style,
}: TuiplayerShortVideoViewProps) => {
  const normalizedSources = useMemo<NativeShortVideoSource[]>(() => {
    return sources.map((item) => ({
      type: item.type ?? 'fileId',
      appId: item.appId,
      fileId: item.fileId,
      url: item.url,
      coverPictureUrl: item.coverPictureUrl,
      pSign: item.pSign,
    }));
  }, [sources]);

  return (
    <TuiplayerShortVideoNativeComponent
      style={StyleSheet.compose(styles.container, style)}
      sources={normalizedSources}
      autoPlay={autoPlay}
    />
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});

export type { ShortVideoSource, TuiplayerLicenseConfig } from './types';
