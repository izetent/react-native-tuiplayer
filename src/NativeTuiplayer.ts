import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type TuiplayerLicenseConfig = {
  licenseUrl?: string;
  licenseKey?: string;
  enableLog?: boolean;
};

export type ShortVideoConstants = {
  listPlayMode?: { [key: string]: number };
  resolutionType?: { [key: string]: number };
};

export interface Spec extends TurboModule {
  initialize(config: TuiplayerLicenseConfig): void;
  getShortVideoConstants(): ShortVideoConstants;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Tuiplayer');
