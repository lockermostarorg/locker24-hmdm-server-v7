import { TReleaseMode } from './release-mode.type';

export type TCreateVersionRequest = {
  applicationId: number;
  version: string;
  arch: string | null;
  url?: string | null;
  filePath?: string | null;
  releaseMode?: TReleaseMode;
  urlArmeabi?: string | null;
  urlArm64?: string | null;
  versionCode?: number;
  pkg?: string;
  name?: string;
  type?: string;
  runAtBoot?: boolean;
  runAfterInstall?: boolean;
  showIcon?: boolean;
  useKiosk?: boolean;
  system?: boolean;
};
