export type ShortVideoSource = {
  type?: 'fileId' | 'url';
  appId?: number;
  fileId?: string;
  url?: string;
  coverPictureUrl?: string;
  pSign?: string;
};

export type TuiplayerLicenseConfig = {
  licenseUrl?: string;
  licenseKey?: string;
  enableLog?: boolean;
};
