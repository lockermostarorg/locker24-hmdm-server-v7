import { buildUpdate, isHttpsUrl, timeZoneOptions } from './settings';

describe('settings', () => {
  it('accepts only https URLs with a host and no credentials', () => {
    expect(isHttpsUrl('https://hooks.example.com/abc')).toBeTrue();
    expect(isHttpsUrl('  https://hooks.example.com/abc  ')).toBeTrue();
    expect(isHttpsUrl('http://hooks.example.com/abc')).toBeFalse();
    expect(isHttpsUrl('https://user:pw@hooks.example.com')).toBeFalse();
    expect(isHttpsUrl('hooks.example.com')).toBeFalse();
    expect(isHttpsUrl('')).toBeFalse();
    expect(isHttpsUrl('https://example.com/' + 'a'.repeat(1100))).toBeFalse();
  });

  it('keeps the stored webhook unless it is replaced or cleared', () => {
    expect(buildUpdate('Europe/Sarajevo', 'keep', 'ignored')).toEqual({
      timeZone: 'Europe/Sarajevo',
    });
    expect('webhookUrl' in buildUpdate('UTC', 'keep', '')).toBeFalse();
    expect(buildUpdate('UTC', 'clear', 'ignored')).toEqual({ timeZone: 'UTC', webhookUrl: '' });
    expect(buildUpdate('UTC', 'replace', ' https://x.example/h ')).toEqual({
      timeZone: 'UTC',
      webhookUrl: 'https://x.example/h',
    });
  });

  it('lists IANA zones including UTC and the current value', () => {
    const zones = timeZoneOptions('Mars/Olympus');
    expect(zones).toContain('Europe/Sarajevo');
    expect(zones).toContain('UTC');
    expect(zones).toContain('Mars/Olympus');
    expect([...zones].sort()).toEqual(zones);
  });
});
