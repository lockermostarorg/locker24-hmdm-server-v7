import { TestBed } from '@angular/core/testing';
import { Chart } from 'hmdm-ui-kit';
import { of } from 'rxjs';
import { SummaryService } from '../../entity/summary/services/summary.service';
import { TSummaryResponse } from '../../entity/summary/types/summary-response.type';
import {
  THEME_MEDIA_QUERY,
  THEME_STORAGE,
  ThemeService,
} from '../../shared/services/theme.service';
import { baseTestProviders } from '../../testing/test-providers';
import { DashboardFacadeService } from './dashboard-facade.service';

const summary: TSummaryResponse = {
  devicesTotal: 3,
  devicesEnrolled: 3,
  devicesEnrolledLastMonth: 1,
  statusSummary: [],
  installSummary: [],
  deviceEnrolledMonthly: [],
  topConfigs: ['Kiosk'],
  statusOfflineByConfig: [1],
  statusOnlineByConfig: [1],
  statusIdleByConfig: [1],
  appFailureByConfig: [0],
  appMismatchByConfig: [0],
  appSuccessByConfig: [3],
};

describe('Dashboard theme colours', () => {
  let root: string | null;

  beforeEach(() => {
    root = document.documentElement.getAttribute('data-theme');
    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        { provide: SummaryService, useValue: { getSummary: () => of(summary) } },
        { provide: THEME_MEDIA_QUERY, useValue: null },
        { provide: THEME_STORAGE, useValue: null },
      ],
    });
  });

  afterEach(() => {
    if (root === null) {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', root);
    }
  });

  it('takes the chart colours from the theme tokens and repaints on a theme change', () => {
    const theme = TestBed.inject(ThemeService);
    const facade = TestBed.inject(DashboardFacadeService);

    theme.setMode('light');
    expect(facade.deviceCircleData().datasets[0].backgroundColor).toEqual([
      '#1d4ed8',
      '#d93636',
      '#b07800',
    ]);

    theme.setMode('dark');
    expect(facade.deviceCircleData().datasets[0].backgroundColor).toEqual([
      '#60a5fa',
      '#ff7a7a',
      '#eab308',
    ]);
  });

  it('gives charts axis and legend colours of the current theme', () => {
    const theme = TestBed.inject(ThemeService);
    theme.setMode('light');
    const fixture = TestBed.createComponent(Chart);
    fixture.componentRef.setInput('chartType', 'bar');
    fixture.componentRef.setInput('data', { datasets: [] });
    fixture.componentRef.setInput('options', { scales: { x: { stacked: true } } });

    const light = fixture.componentInstance.themedOptions() as any;
    expect(light.scales.x.stacked).toBeTrue();
    expect(light.scales.x.ticks.color).toBe('#5f6368');
    expect(light.scales.y.grid.color).toBe('rgba(0, 0, 0, 0.1)');
    expect(light.plugins.legend.labels.color).toBe('#5f6368');

    theme.setMode('dark');
    const dark = fixture.componentInstance.themedOptions() as any;
    expect(dark.scales.x.ticks.color).toBe('#aab1b9');
    expect(dark.scales.x.stacked).toBeTrue();
  });

  it('leaves doughnut charts without axes', () => {
    const fixture = TestBed.createComponent(Chart);
    fixture.componentRef.setInput('chartType', 'doughnut');
    fixture.componentRef.setInput('data', { datasets: [] });

    const options = fixture.componentInstance.themedOptions() as any;
    expect(options.scales).toBeUndefined();
    expect(options.elements.arc.borderColor).toBeTruthy();
  });
});
