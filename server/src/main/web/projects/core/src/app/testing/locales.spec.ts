import { LOCALES, loadLocale } from './test-providers';

describe('release flow locale keys', () => {
  const keys = [
    'button.release',
    'form.application.release.mode',
    'form.application.release.mode.schedule',
    'form.application.release.mode.immediate',
    'form.application.release.mode.hold',
    'form.application.release.mode.schedule.help',
    'form.application.release.mode.immediate.help',
    'form.application.release.mode.hold.help',
    'release.summary.released',
    'release.summary.held',
    'release.summary.configuration.one',
    'release.summary.configuration.other',
    'release.summary.tablet.one',
    'release.summary.tablet.other',
    'release.summary.first.version',
    'release.summary.notified',
    'release.summary.push.failed',
    'release.summary.not.latest',
    'release.summary.reupload.held',
    'button.notify.tablets',
    'form.application.notify.help',
    'form.application.release.canary.note',
    'error.version.exists',
    'release.state.released',
    'release.state.held',
    'table.heading.application.version.state',
    'form.configuration.app.follow.latest',
    'form.configuration.app.pinned',
  ];

  it('uses the agreed English copy', async () => {
    const en = await loadLocale('en_US');

    expect(en['form.application.release.mode']).toBe('Apply this version');
    expect(en['form.application.release.mode.schedule']).toBe("On each configuration's schedule");
    expect(en['form.application.release.mode.immediate']).toBe('Immediately');
    expect(en['form.application.release.mode.hold']).toBe('Hold (upload only)');
    expect(en['form.application.release.mode.immediate.help']).toBe(
      'Immediately: tablets on agent 6.40-l24.6 or later install now; older agents install in their next update window.',
    );
  });

  it('has the agreed copy for the notify and warning lines', async () => {
    const en = await loadLocale('en_US');

    expect(en['form.application.notify.help']).toBe(
      'Sends the update to tablets again without moving any configuration.',
    );
    expect(en['form.application.release.canary.note']).toBe(
      'Configurations pinned to this version will follow the latest version again.',
    );
    expect(en['error.version.exists']).toBe(
      'This version already exists. Increase the version number and upload again.',
    );
  });

  for (const locale of LOCALES) {
    it(`is present in ${locale}`, async () => {
      const file = await loadLocale(locale);

      for (const key of keys) {
        expect(file[key]).withContext(key).toBeTruthy();
      }
    });
  }
});
