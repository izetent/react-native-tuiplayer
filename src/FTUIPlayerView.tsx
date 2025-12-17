import React, { forwardRef } from 'react';
import type { ViewProps } from 'react-native';
import { requireNativeComponent } from 'react-native';

import { PLAYER_VIEW_MANAGER } from './types';

type NativeProps = ViewProps;

const NativeFTUIPlayerView =
  requireNativeComponent<NativeProps>(PLAYER_VIEW_MANAGER);

export type FTUIPlayerViewProps = NativeProps;

export const FTUIPlayerView = forwardRef<any, FTUIPlayerViewProps>(
  (props, ref) => {
    return <NativeFTUIPlayerView {...props} ref={ref} />;
  }
);

FTUIPlayerView.displayName = 'FTUIPlayerView';

