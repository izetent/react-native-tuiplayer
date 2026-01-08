import { forwardRef } from 'react';
import type { ViewProps } from 'react-native';
import { requireNativeComponent } from 'react-native';

import { PLAYER_VIEW_MANAGER } from './types';

export type ResizeMode = 'contain' | 'cover';

type NativeProps = ViewProps & {
  resizeMode?: ResizeMode;
  videoWidth?: number;
  videoHeight?: number;
};

const NativeRNPlayerView =
  requireNativeComponent<NativeProps>(PLAYER_VIEW_MANAGER);

export type RNPlayerViewProps = NativeProps;

export const RNPlayerView = forwardRef<any, RNPlayerViewProps>((props, ref) => {
  return <NativeRNPlayerView {...props} ref={ref} />;
});

RNPlayerView.displayName = 'RNPlayerView';
