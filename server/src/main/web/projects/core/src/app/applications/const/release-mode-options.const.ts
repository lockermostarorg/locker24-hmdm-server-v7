import { TOption } from 'hmdm-ui-kit';
import { TReleaseMode } from '../../entity/application/types/release-mode.type';

export const RELEASE_MODE_OPTIONS: TOption<TReleaseMode>[] = [
  { value: 'schedule', viewValue: 'form.application.release.mode.schedule' },
  { value: 'immediate', viewValue: 'form.application.release.mode.immediate' },
  { value: 'hold', viewValue: 'form.application.release.mode.hold' },
];
