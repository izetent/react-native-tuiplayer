import { forwardRef } from 'react';
import type { ViewProps } from 'react-native';
import { requireNativeComponent } from 'react-native';

import { PLAYER_VIEW_MANAGER } from './types';

type NativeProps = ViewProps;

const NativeRNPlayerView =
  requireNativeComponent<NativeProps>(PLAYER_VIEW_MANAGER);

export type RNPlayerViewProps = NativeProps;

export const RNPlayerView = forwardRef<any, RNPlayerViewProps>((props, ref) => {
  return <NativeRNPlayerView {...props} ref={ref} />;
});

RNPlayerView.displayName = 'RNPlayerView';
