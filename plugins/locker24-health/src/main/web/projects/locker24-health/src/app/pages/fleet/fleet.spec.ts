import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { fleetRow } from '../../logic/fixtures.spec';
import { shallow } from '../../testing/ui-stubs.spec';
import { Fleet, POLL_INTERVAL_MS } from './fleet';

const BASE = 'rest/private/plugin-locker24-health';

describe('Fleet page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Fleet],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    });
    shallow(Fleet);
    http = TestBed.inject(HttpTestingController);
  });

  function render(devices = defaultDevices(), truncated = false) {
    const fixture = TestBed.createComponent(Fleet);
    fixture.detectChanges();
    http.expectOne(`${BASE}/settings`).flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet: false, timeZone: 'Europe/Sarajevo', editable: true },
    });
    tick(0);
    http
      .expectOne(`${BASE}/devices`)
      .flush({ status: 'OK', message: null, data: { devices, truncated } });
    fixture.detectChanges();
    return fixture;
  }

  function defaultDevices() {
    return [
      fleetRow({ deviceId: 7, number: 'T-7', status: 'green', lastSeen: Date.now() - 120_000 }),
      fleetRow({
        deviceId: 8,
        number: 'T-8',
        status: 'yellow',
        reasons: ['kiosk_admin_off'],
        kioskAdminUnlocked: true,
        charging: false,
      }),
      fleetRow({ deviceId: 9, number: 'T-9', status: 'red', reasons: ['offline', 'battery_low'] }),
    ];
  }

  it('renders one row per tablet, worst status first, with real links', fakeAsync(() => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    const rows = Array.from(el.querySelectorAll('tbody tr'));
    expect(rows.length).toBe(3);

    const links = rows.map((row) => row.querySelector('a.device-link') as HTMLAnchorElement);
    expect(links.map((a) => a.textContent?.trim())).toEqual(['T-9', 'T-8', 'T-7']);
    expect(links[0].getAttribute('href')).toBe('/main/device/9');

    expect(rows[0].textContent).toContain('plugin.locker24-health.status.red');
    expect(rows[0].textContent).toContain('plugin.locker24-health.reason.offline');
    expect(rows[1].textContent).toContain('plugin.locker24-health.status.admin');
    expect(rows[1].textContent).not.toContain('plugin.locker24-health.reason.kiosk_admin_off');
    expect(rows[1].textContent).toContain('plugin.locker24-health.battery.not_charging');
    expect(rows[2].querySelector('time')?.getAttribute('title')).toMatch(
      /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}/,
    );

    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('marks the sorted column for assistive technology', fakeAsync(() => {
    const fixture = render();
    const headers = Array.from(fixture.nativeElement.querySelectorAll('thead th')) as HTMLElement[];
    expect(headers[0].getAttribute('aria-sort')).toBe('ascending');
    expect(headers[1].getAttribute('aria-sort')).toBe('none');
    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('shows the truncated notice', fakeAsync(() => {
    const fixture = render(defaultDevices(), true);
    expect(fixture.nativeElement.textContent).toContain('plugin.locker24-health.fleet.truncated');
    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('polls every minute and keeps the last data when a poll fails', fakeAsync(() => {
    const fixture = render();
    tick(POLL_INTERVAL_MS);
    http.expectOne(`${BASE}/devices`).flush('down', { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('tbody tr').length).toBe(3);
    expect(el.textContent).toContain('plugin.locker24-health.error.network');
    const lastSeen = el.querySelectorAll('tbody tr')[2].querySelector('time')?.textContent;
    expect(lastSeen).toContain('"count":3');
    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('refreshes at once when the tab becomes visible again', fakeAsync(() => {
    const fixture = render();
    document.dispatchEvent(new Event('visibilitychange'));
    http
      .expectOne(`${BASE}/devices`)
      .flush({ status: 'OK', message: null, data: { devices: [], truncated: false } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('plugin.locker24-health.fleet.empty');
    fixture.destroy();
    discardPeriodicTasks();
  }));

  afterEach(() => http.verify());
});
