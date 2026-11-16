import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { fleetRow } from '../../logic/fixtures.spec';
import { shallow } from '../../testing/ui-stubs.spec';
import { Device } from './device';

const BASE = 'rest/private/plugin-locker24-health';

describe('Device page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'main/device/:deviceId', component: Device }]),
      ],
    });
    shallow(Device);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the requested range and shows charts, bands and offline periods', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/main/device/9?hours=168', Device);

    http.expectOne(`${BASE}/settings`).flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet: false, timeZone: 'UTC', editable: false },
    });
    http.expectOne(`${BASE}/devices`).flush({
      status: 'OK',
      message: null,
      data: {
        devices: [fleetRow({ deviceId: 9, number: 'T-9', status: 'red', reasons: ['offline'] })],
        truncated: false,
      },
    });
    const to = Date.UTC(2026, 10, 16, 12);
    const from = to - 168 * 3_600_000;
    const historyReq = http.expectOne((req) => req.url === `${BASE}/devices/9/history`);
    expect(historyReq.request.params.get('hours')).toBe('168');
    historyReq.flush({
      status: 'OK',
      message: null,
      data: {
        from,
        to,
        bucketMs: 604800,
        truncated: true,
        points: [
          {
            t: from + 1000,
            batteryPct: 80,
            temperatureC: 31,
            rssiDbm: -60,
            charging: true,
            kioskRunning: true,
          },
          {
            t: to - 7_200_000,
            batteryPct: 70,
            temperatureC: 33,
            rssiDbm: -65,
            charging: true,
            kioskRunning: true,
          },
        ],
        offlinePeriods: [
          { from: from + 3_600_000, to: from + 7_200_000 },
          { from: to - 1_800_000, to },
        ],
      },
    });
    harness.detectChanges();

    const el: HTMLElement = harness.routeNativeElement!;
    expect(el.querySelector('h3')?.textContent).toContain('T-9');
    expect(el.textContent).toContain('plugin.locker24-health.reason.offline');
    expect(el.textContent).toContain('plugin.locker24-health.device.truncated');
    expect(el.querySelectorAll('hmdm-chart').length).toBe(3);

    const ranges = Array.from(el.querySelectorAll('nav.ranges a')) as HTMLAnchorElement[];
    expect(ranges.map((a) => a.getAttribute('href'))).toEqual([
      '/main/device/9?hours=24',
      '/main/device/9?hours=168',
      '/main/device/9?hours=720',
    ]);
    expect(ranges[1].getAttribute('aria-current')).toBe('true');

    const periodRows = el.querySelectorAll('table.periods tbody tr');
    expect(periodRows.length).toBe(2);
    expect(periodRows[0].textContent).toContain('plugin.locker24-health.offline.ongoing');
    expect(periodRows[1].textContent).toContain('1 h');
  });

  it('shows an access error for a device the user cannot see', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/main/device/404', Device);
    http.expectOne(`${BASE}/settings`).flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet: false, timeZone: 'UTC', editable: false },
    });
    http
      .expectOne(`${BASE}/devices`)
      .flush({ status: 'OK', message: null, data: { devices: [], truncated: false } });
    http
      .expectOne((req) => req.url === `${BASE}/devices/404/history`)
      .flush({ status: 'ERROR', message: 'error.permission.denied', data: null });
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain(
      'plugin.locker24-health.error.permission',
    );
  });
});
