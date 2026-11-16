import { TToFormGroup } from 'hmdm-ui-kit';
import { TReleaseMode } from '../../entity/application/types/release-mode.type';

export type TApkFormValue = {
  arch: string;
  name: string;
  pkg: string;
  runAfterInstall: boolean;
  runAtBoot: boolean;
  system: boolean;
  url: string;
  version: string;
  file: File | null;
  releaseMode: TReleaseMode;
  versionCode?: number;
  versionExists?: boolean;
  useKiosk?: boolean;
};

export type TApkForm = TToFormGroup<
  Omit<TApkFormValue, 'versionCode' | 'versionExists' | 'useKiosk'>
>;
