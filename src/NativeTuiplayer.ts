import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type TuiplayerLicenseConfig = {
  licenseUrl?: string;
  licenseKey?: string;
  enableLog?: boolean;
};

export interface Spec extends TurboModule {
  initialize(config: TuiplayerLicenseConfig): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Tuiplayer');
