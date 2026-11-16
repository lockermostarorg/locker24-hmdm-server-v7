import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TReleaseSummary } from '../../entity/application/types/release-mode.type';
import { baseTestProviders, useEnglish } from '../../testing/test-providers';
import { releaseSummaryDialogData } from './release-summary';

describe('releaseSummaryDialogData', () => {
  let translate: TranslateService;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    translate = TestBed.inject(TranslateService);
    await useEnglish(translate);
  });

  const lines = (summary: Partial<TReleaseSummary>, options = {}) =>
    releaseSummaryDialogData(
      { configurations: 2, devices: 5, mode: 'schedule', ...summary },
      translate,
      options,
    ).lines.map((l) => [l.text, l.warning]);

  it('labels devices as assigned tablets', () => {
    expect(lines({})).toEqual([['Released to 2 configurations (5 assigned tablets)', false]]);
  });

  it('uses the singular for one configuration and one tablet', () => {
    expect(lines({ configurations: 1, devices: 1 })).toEqual([
      ['Released to 1 configuration (1 assigned tablet)', false],
    ]);
  });

  it('warns when tablets of some configurations could not be notified', () => {
    expect(lines({ pushFailed: 2 })[1]).toEqual([
      'Tablets of 2 configurations could not be notified. Use Notify tablets again.',
      true,
    ]);
    expect(lines({ pushFailed: 1 })[1]).toEqual([
      'Tablets of 1 configuration could not be notified. Use Notify tablets again.',
      true,
    ]);
  });

  it('adds no warning when every push went through', () => {
    expect(lines({ pushFailed: 0, notLatest: false }).length).toBe(1);
  });

  it('warns when the version is lower than the latest', () => {
    expect(lines({ configurations: 0, devices: 0, notLatest: true })[1]).toEqual([
      'This version is lower than the latest released version; no configuration was moved.',
      true,
    ]);
  });

  it('shows both warnings together', () => {
    expect(lines({ pushFailed: 1, notLatest: true }).length).toBe(3);
  });

  it('says a re-upload of a held version is already held', () => {
    expect(
      lines({ mode: 'hold', configurations: 0, devices: 0, requestedMode: 'schedule' }),
    ).toEqual([
      ['Held; not applied', false],
      ['Already uploaded and held. Use Release to apply it.', true],
    ]);
  });

  it('shows no re-upload note for a plain hold upload', () => {
    expect(lines({ mode: 'hold', requestedMode: 'hold' })).toEqual([['Held; not applied', false]]);
  });

  it('words a notify answer as an update sent', () => {
    expect(lines({}, { notify: true })).toEqual([
      ['Update sent for 2 configurations (5 assigned tablets)', false],
    ]);
  });
});
