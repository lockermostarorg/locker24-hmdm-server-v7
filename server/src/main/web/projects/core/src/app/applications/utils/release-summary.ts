import { TranslateService } from '@ngx-translate/core';
import { TReleaseSummary } from '../../entity/application/types/release-mode.type';

export type TSummaryLine = { text: string; warning: boolean };

export type TReleaseSummaryDialogData = { lines: TSummaryLine[] };

export function releaseSummaryDialogData(
  summary: TReleaseSummary,
  translate: TranslateService,
  options: { notify?: boolean } = {},
): TReleaseSummaryDialogData {
  const lines: TSummaryLine[] = [];
  const say = (key: string, params: Record<string, string> = {}, warning = false): void => {
    lines.push({ text: translate.instant(key, params), warning });
  };
  const count = (key: string, value: number): string =>
    translate.instant(`${key}.${value === 1 ? 'one' : 'other'}`, { count: value });

  if (summary.mode === 'hold') {
    say('release.summary.held');
    if (summary.requestedMode && summary.requestedMode !== 'hold') {
      say('release.summary.reupload.held', {}, true);
    }
    return { lines };
  }

  say(options.notify ? 'release.summary.notified' : 'release.summary.released', {
    configurations: count('release.summary.configuration', summary.configurations),
    devices: count('release.summary.tablet', summary.devices),
  });

  if (summary.pushFailed) {
    say(
      'release.summary.push.failed',
      { configurations: count('release.summary.configuration', summary.pushFailed) },
      true,
    );
  }

  if (summary.notLatest) {
    say('release.summary.not.latest', {}, true);
  }

  return { lines };
}
