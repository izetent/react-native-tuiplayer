import type { HostComponent, ViewProps } from 'react-native';
// eslint-disable-next-line @react-native/no-deep-imports
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type {
  Int32,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';

export type NativeShortVideoSource = Readonly<{
  type?: WithDefault<string, 'fileId'>;
  appId?: Int32;
  fileId?: string;
  url?: string;
  coverPictureUrl?: string;
  pSign?: string;
}>;

export interface NativeProps extends ViewProps {
  sources?: ReadonlyArray<NativeShortVideoSource>;
  autoPlay?: WithDefault<boolean, true>;
}

export default codegenNativeComponent<NativeProps>(
  'TuiplayerShortVideoView'
) as HostComponent<NativeProps>;
