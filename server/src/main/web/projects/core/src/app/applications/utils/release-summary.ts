import { TranslateService } from '@ngx-translate/core';
import { TReleaseSummary } from '../../entity/application/types/release-mode.type';

export function releaseSummaryDialogData(
  summary: TReleaseSummary,
  translate: TranslateService,
): { message: string; params: Record<string, string> } {
  if (summary.mode === 'hold') {
    return { message: 'release.summary.held', params: {} };
  }

  const count = (key: string, value: number): string =>
    translate.instant(`${key}.${value === 1 ? 'one' : 'other'}`, { count: value });

  return {
    message: 'release.summary.released',
    params: {
      configurations: count('release.summary.configuration', summary.configurations),
      devices: count('release.summary.tablet', summary.devices),
    },
  };
}
