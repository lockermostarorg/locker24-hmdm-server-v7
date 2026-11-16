import { TReleaseMode } from '../../entity/application/types/release-mode.type';
import { TToFormGroup } from '../../shared/types/to-form-group.type';

export type TVersionFormValue = {
  version: string;
  arch: string;
  url: string;
  file: File | null;
  releaseMode: TReleaseMode;
};

export type TVersionForm = TToFormGroup<TVersionFormValue>;
