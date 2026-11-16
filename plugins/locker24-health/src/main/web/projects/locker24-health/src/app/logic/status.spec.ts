import { displayStatus, kioskState, reasonKey, statusRank } from './status';

describe('status', () => {
  it('passes red, green and yellow through', () => {
    expect(displayStatus({ status: 'red', reasons: ['offline'] })).toBe('red');
    expect(displayStatus({ status: 'green', reasons: [] })).toBe('green');
    expect(displayStatus({ status: 'yellow', reasons: ['battery_low'] })).toBe('yellow');
  });

  it('shows a yellow caused only by an admin exit as admin', () => {
    expect(displayStatus({ status: 'yellow', reasons: ['kiosk_admin_off'] })).toBe('admin');
  });

  it('keeps yellow when an admin exit comes with another condition', () => {
    expect(displayStatus({ status: 'yellow', reasons: ['kiosk_admin_off', 'not_charging'] })).toBe(
      'yellow',
    );
  });

  it('treats a yellow without reasons as yellow', () => {
    expect(displayStatus({ status: 'yellow', reasons: null })).toBe('yellow');
  });

  it('maps unknown or unexpected statuses to unknown', () => {
    expect(displayStatus({ status: 'unknown', reasons: [] })).toBe('unknown');
    expect(displayStatus({ status: 'purple' as never, reasons: [] })).toBe('unknown');
  });

  it('ranks red first and green last', () => {
    expect(statusRank('red')).toBeLessThan(statusRank('yellow'));
    expect(statusRank('yellow')).toBeLessThan(statusRank('admin'));
    expect(statusRank('admin')).toBeLessThan(statusRank('unknown'));
    expect(statusRank('unknown')).toBeLessThan(statusRank('green'));
  });

  it('derives the kiosk state', () => {
    const base = { kioskAdminUnlocked: false, kioskRequired: true, kioskRunning: true };
    expect(kioskState(base)).toBe('running');
    expect(kioskState({ ...base, kioskRunning: false })).toBe('not_running');
    expect(kioskState({ ...base, kioskRunning: null })).toBe('unknown');
    expect(kioskState({ ...base, kioskRequired: false, kioskRunning: false })).toBe('not_required');
    expect(kioskState({ ...base, kioskAdminUnlocked: true, kioskRunning: false })).toBe(
      'admin_off',
    );
  });

  it('maps reason codes to translation keys', () => {
    expect(reasonKey('battery_low')).toBe('plugin.locker24-health.reason.battery_low');
    expect(reasonKey('something_new')).toBe('plugin.locker24-health.reason.other');
  });
});
