import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  BaseComponent,
  Chart,
  HelpTooltip,
  MatButtonModule,
  MatCardModule,
  MatIconModule,
  TranslatePipe,
} from 'hmdm-ui-kit';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  EMPTY,
  finalize,
  map,
  startWith,
  Subject,
  switchMap,
  take,
  tap,
} from 'rxjs';
import { StatusBadge } from '../../components/status-badge/status-badge';
import {
  chartTheme,
  lineChartData,
  lineChartOptions,
  OVERLAY_PLUGIN,
  TThreshold,
} from '../../logic/chart';
import {
  absoluteTime,
  formatDuration,
  formatPercent,
  formatStorage,
  formatTemperature,
  relativeTime,
} from '../../logic/format';
import {
  hasValues,
  newestFirst,
  parseHours,
  RANGE_HOURS,
  series,
  TSeriesKey,
} from '../../logic/history';
import { displayStatus, kioskState, reasonKey } from '../../logic/status';
import { onThemeChange } from '../../logic/theme';
import { errorKey, HealthService } from '../../services/health.service';
import { TFleetRow } from '../../types/fleet.type';
import { THistory } from '../../types/history.type';

type TChartSpec = {
  key: TSeriesKey;
  unit: string;
  yMin?: number;
  yMax?: number;
  ySuggestedMax?: number;
  thresholds: (theme: ReturnType<typeof chartTheme>) => TThreshold[];
};

const CHARTS: TChartSpec[] = [
  {
    key: 'batteryPct',
    unit: '%',
    yMin: 0,
    yMax: 100,
    thresholds: (theme) => [
      { value: 20, color: theme.warn },
      { value: 10, color: theme.error },
    ],
  },
  {
    key: 'temperatureC',
    unit: '°C',
    ySuggestedMax: 52,
    thresholds: (theme) => [
      { value: 45, color: theme.warn },
      { value: 50, color: theme.error },
    ],
  },
  {
    key: 'rssiDbm',
    unit: 'dBm',
    thresholds: () => [],
  },
];

@Component({
  selector: 'l24-device',
  templateUrl: './device.html',
  styleUrl: './device.scss',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    HelpTooltip,
    TranslatePipe,
    Chart,
    StatusBadge,
  ],
})
export class Device extends BaseComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly health = inject(HealthService);
  private readonly theme = signal(chartTheme());
  private readonly reload$ = new Subject<void>();

  readonly pluginRoot = this.route.parent;
  readonly ranges = RANGE_HOURS;
  readonly plugins = [OVERLAY_PLUGIN] as never;

  readonly deviceId = signal<number | null>(null);
  readonly hours = signal(24);
  readonly row = signal<TFleetRow | null>(null);
  readonly rowMissing = signal(false);
  readonly history = signal<THistory | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly timeZone = signal<string | null>(null);
  readonly now = signal(Date.now());

  readonly status = computed(() => {
    const row = this.row();
    return row ? displayStatus(row) : 'unknown';
  });
  readonly reasons = computed(() => {
    const row = this.row();
    return row && this.status() !== 'admin' ? (row.reasons ?? []).map(reasonKey) : [];
  });
  readonly facts = computed(() => {
    const row = this.row();
    if (!row) {
      return [];
    }
    const zone = this.timeZone();
    return [
      { key: 'configuration', value: row.configurationName },
      { key: 'groups', value: row.groups },
      { key: 'locker', value: row.lockerShortUid, mono: true },
      { key: 'model', value: row.model },
      { key: 'battery', value: formatPercent(row.batteryPct) },
      {
        key: 'charging',
        translate:
          row.charging === null
            ? null
            : `plugin.locker24-health.battery.${row.charging ? 'charging' : 'not_charging'}`,
      },
      { key: 'temperature', value: formatTemperature(row.temperatureC) },
      { key: 'storage', value: formatStorage(row.freeStorageMb) },
      { key: 'kioskAppVersion', value: row.kioskAppVersion, mono: true },
      { key: 'agentVersion', value: row.agentVersion, mono: true },
      { key: 'kioskState', translate: `plugin.locker24-health.kiosk.${kioskState(row)}` },
      { key: 'lastSeenAbsolute', value: absoluteTime(row.lastSeen, zone) },
    ].map((fact) => ({ ...fact, label: `plugin.locker24-health.column.${fact.key}` }));
  });
  readonly lastSeen = computed(() => relativeTime(this.row()?.lastSeen ?? null, this.now()));

  readonly charts = computed(() => {
    const history = this.history();
    if (!history) {
      return [];
    }
    const zone = this.timeZone();
    const theme = this.theme();
    return CHARTS.map((spec) => {
      const input = {
        label: spec.key,
        unit: spec.unit,
        data: series(history.points, spec.key, history.offlinePeriods),
        from: history.from,
        to: history.to,
        periods: history.offlinePeriods,
        thresholds: spec.thresholds(theme),
        timeZone: zone,
        yMin: spec.yMin,
        yMax: spec.yMax,
        ySuggestedMax: spec.ySuggestedMax,
        theme,
      };
      return {
        key: spec.key,
        title: `plugin.locker24-health.chart.${spec.key}`,
        help: `plugin.locker24-health.chart.${spec.key}.help`,
        empty: !hasValues(history.points, spec.key),
        data: lineChartData(input) as never,
        options: lineChartOptions(input) as never,
      };
    });
  });

  readonly periods = computed(() => {
    const history = this.history();
    if (!history) {
      return [];
    }
    const zone = this.timeZone();
    return newestFirst(history.offlinePeriods).map((period) => ({
      from: absoluteTime(period.from, zone, false),
      to: absoluteTime(period.to, zone, false),
      ongoing: period.to >= history.to,
      duration: formatDuration(period.to - period.from),
    }));
  });

  constructor() {
    super();
    inject(DestroyRef).onDestroy(onThemeChange(() => this.theme.set(chartTheme())));
  }

  ngOnInit(): void {
    this.health
      .fetchSettings()
      .pipe(
        take(1),
        catchError(() => EMPTY),
      )
      .subscribe((settings) => this.timeZone.set(settings.timeZone));

    const deviceId$ = this.route.paramMap.pipe(
      map((params) => Number(params.get('deviceId'))),
      distinctUntilChanged(),
    );
    const hours$ = this.route.queryParamMap.pipe(
      map((params) => parseHours(params.get('hours'))),
      distinctUntilChanged(),
    );

    deviceId$
      .pipe(
        tap((id) => this.deviceId.set(Number.isInteger(id) && id > 0 ? id : null)),
        switchMap((id) => this.loadRow(id)),
        this.untilDestroyed(),
      )
      .subscribe();

    combineLatest([deviceId$, hours$, this.reload$.pipe(startWith(null))])
      .pipe(
        tap(([, hours]) => this.hours.set(hours)),
        switchMap(([id, hours]) => this.loadHistory(id, hours)),
        this.untilDestroyed(),
      )
      .subscribe();
  }

  refresh(): void {
    const id = this.deviceId();
    if (id !== null) {
      this.loadRow(id).pipe(take(1)).subscribe();
    }
    this.reload$.next();
  }

  rangeLabel(hours: number): string {
    return `plugin.locker24-health.range.${hours}`;
  }

  private loadRow(id: number) {
    return this.health.fetchFleet().pipe(
      tap((fleet) => {
        const row = fleet.devices.find((d) => d.deviceId === id) ?? null;
        this.row.set(row);
        this.rowMissing.set(row === null);
        this.now.set(Date.now());
      }),
      catchError(() => EMPTY),
    );
  }

  private loadHistory(id: number, hours: number) {
    if (!Number.isInteger(id) || id <= 0) {
      this.error.set('plugin.locker24-health.error.permission');
      return EMPTY;
    }
    this.loading.set(true);
    return this.health.fetchHistory(id, hours).pipe(
      tap((history) => {
        this.history.set(history);
        this.error.set(null);
      }),
      catchError((error: unknown) => {
        this.history.set(null);
        this.error.set(errorKey(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
    );
  }
}
