import { Location } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  BaseComponent,
  HelpTooltip,
  MatButtonModule,
  MatCardModule,
  MatFormFieldModule,
  MatIconModule,
  MatInputModule,
  MatSelectModule,
  TranslatePipe,
} from 'hmdm-ui-kit';
import {
  catchError,
  debounceTime,
  EMPTY,
  filter,
  finalize,
  fromEvent,
  map,
  merge,
  Observable,
  switchMap,
  take,
  tap,
  timer,
} from 'rxjs';
import { StatusBadge } from '../../components/status-badge/status-badge';
import {
  configurationOptions,
  countByStatus,
  DEFAULT_SORT,
  EMPTY_FILTER,
  filterRows,
  modelOptions,
  nextSort,
  parseFilter,
  parseSort,
  sortRows,
  TFleetFilter,
  TSort,
  TSortKey,
  toQueryParams,
} from '../../logic/fleet';
import {
  absoluteTime,
  formatPercent,
  formatTemperature,
  relativeTime,
  TRelativeTime,
} from '../../logic/format';
import {
  DISPLAY_STATUSES,
  displayStatus,
  kioskState,
  reasonKey,
  TDisplayStatus,
  TKioskState,
} from '../../logic/status';
import { errorKey, HealthService } from '../../services/health.service';
import { TFleetRow } from '../../types/fleet.type';

export const POLL_INTERVAL_MS = 60_000;

type TColumn = {
  key: TSortKey;
  title: string;
  help: string;
};

type TFleetView = {
  row: TFleetRow;
  status: TDisplayStatus;
  reasons: string[];
  lastSeen: TRelativeTime;
  lastSeenAbsolute: string;
  lastSeenIso: string | null;
  battery: string;
  temperature: string;
  kiosk: TKioskState;
};

const COLUMNS: TColumn[] = [
  'status',
  'number',
  'configuration',
  'groups',
  'locker',
  'lastSeen',
  'battery',
  'temperature',
  'kioskAppVersion',
  'agentVersion',
  'kioskState',
].map((key) => ({
  key: key as TSortKey,
  title: `plugin.locker24-health.column.${key}`,
  help: `plugin.locker24-health.column.${key}.help`,
}));

@Component({
  selector: 'l24-fleet',
  templateUrl: './fleet.html',
  styleUrl: './fleet.scss',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    HelpTooltip,
    TranslatePipe,
    StatusBadge,
  ],
})
export class Fleet extends BaseComponent implements OnInit {
  private readonly health = inject(HealthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly pluginRoot = this.route.parent;
  readonly columns = COLUMNS;
  readonly statuses = DISPLAY_STATUSES;

  readonly rows = signal<TFleetRow[]>([]);
  readonly truncated = signal(false);
  readonly loaded = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly updatedAt = signal<number | null>(null);
  readonly now = signal(Date.now());
  readonly timeZone = signal<string | null>(null);
  readonly filter = signal<TFleetFilter>(EMPTY_FILTER);
  readonly sort = signal<TSort>(DEFAULT_SORT);

  readonly form = new FormGroup({
    configurationId: new FormControl<number | null>(null),
    status: new FormControl<TDisplayStatus | null>(null),
    model: new FormControl<string | null>(null),
    search: new FormControl<string>('', { nonNullable: true }),
  });

  readonly counts = computed(() => countByStatus(this.rows()));
  readonly configurationOptions = computed(() => configurationOptions(this.rows()));
  readonly modelOptions = computed(() => modelOptions(this.rows()));
  readonly isFiltered = computed(() => {
    const f = this.filter();
    return f.configurationId !== null || f.status !== null || f.model !== null || !!f.search.trim();
  });
  readonly views = computed<TFleetView[]>(() => {
    const now = this.now();
    const zone = this.timeZone();
    return sortRows(filterRows(this.rows(), this.filter()), this.sort()).map((row) => {
      const status = displayStatus(row);
      return {
        row,
        status,
        reasons: status === 'admin' ? [] : (row.reasons ?? []).map(reasonKey),
        lastSeen: relativeTime(row.lastSeen, now),
        lastSeenAbsolute: absoluteTime(row.lastSeen, zone),
        lastSeenIso: row.lastSeen !== null ? new Date(row.lastSeen).toISOString() : null,
        battery: formatPercent(row.batteryPct),
        temperature: formatTemperature(row.temperatureC),
        kiosk: kioskState(row),
      };
    });
  });
  readonly updatedAtText = computed(() => absoluteTime(this.updatedAt(), this.timeZone()));

  ngOnInit(): void {
    this.route.queryParamMap.pipe(this.untilDestroyed()).subscribe((map) => {
      const params = Object.fromEntries(map.keys.map((key) => [key, map.get(key)]));
      const parsed = parseFilter(params);
      this.filter.set(parsed);
      this.sort.set(parseSort(params));
      this.patchForm(parsed);
    });

    this.form.valueChanges
      .pipe(debounceTime(250), this.untilDestroyed())
      .subscribe(() => this.applyQuery(this.formFilter(), this.sort()));

    this.health
      .fetchSettings()
      .pipe(
        take(1),
        catchError(() => EMPTY),
      )
      .subscribe((settings) => this.timeZone.set(settings.timeZone));

    const ticks$ = timer(0, POLL_INTERVAL_MS).pipe(
      tap(() => this.now.set(Date.now())),
      filter((tick) => tick === 0 || !this.isHidden()),
    );
    const visible$ =
      typeof document === 'undefined'
        ? EMPTY
        : fromEvent(document, 'visibilitychange').pipe(filter(() => !this.isHidden()));
    merge(ticks$, visible$)
      .pipe(
        switchMap(() => this.load()),
        this.untilDestroyed(),
      )
      .subscribe();
  }

  refresh(): void {
    this.load().pipe(take(1)).subscribe();
  }

  onSort(key: TSortKey): void {
    this.applyQuery(this.filter(), nextSort(this.sort(), key));
  }

  ariaSort(key: TSortKey): 'ascending' | 'descending' | 'none' {
    const sort = this.sort();
    if (sort.key !== key) {
      return 'none';
    }
    return sort.dir === 'asc' ? 'ascending' : 'descending';
  }

  sortIcon(key: TSortKey): string {
    const sort = this.sort();
    if (sort.key !== key) {
      return 'unfold_more';
    }
    return sort.dir === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  statusLink(status: TDisplayStatus): Record<string, string | null> {
    const active = this.filter().status === status;
    return toQueryParams({ ...this.filter(), status: active ? null : status }, this.sort());
  }

  clearFilters(): void {
    this.applyQuery(EMPTY_FILTER, this.sort());
  }

  onRowClick(event: MouseEvent, row: TFleetRow): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('a, button, input, select, textarea')) {
      return;
    }
    if (event.type === 'click' && window.getSelection()?.toString()) {
      return;
    }
    if (event.type === 'auxclick' && event.button !== 1) {
      return;
    }
    const tree = this.router.createUrlTree(['main', 'device', row.deviceId], {
      relativeTo: this.pluginRoot,
    });
    if (event.type === 'auxclick' || event.ctrlKey || event.metaKey || event.shiftKey) {
      window.open(this.location.prepareExternalUrl(this.router.serializeUrl(tree)), '_blank');
      return;
    }
    this.router.navigateByUrl(tree);
  }

  private isHidden(): boolean {
    return typeof document !== 'undefined' && document.hidden;
  }

  private load(): Observable<void> {
    this.loading.set(true);
    return this.health.fetchFleet().pipe(
      tap((response) => {
        this.rows.set(response.devices ?? []);
        this.truncated.set(!!response.truncated);
        this.updatedAt.set(Date.now());
        this.now.set(Date.now());
        this.error.set(null);
        this.loaded.set(true);
      }),
      map(() => undefined),
      catchError((error: unknown) => {
        this.error.set(errorKey(error));
        return EMPTY;
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  private formFilter(): TFleetFilter {
    const value = this.form.getRawValue();
    return {
      configurationId: value.configurationId,
      status: value.status,
      model: value.model,
      search: value.search,
    };
  }

  private applyQuery(filterValue: TFleetFilter, sort: TSort): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams(filterValue, sort),
      replaceUrl: true,
    });
  }

  private patchForm(value: TFleetFilter): void {
    const current = this.form.getRawValue();
    const patch: Partial<typeof current> = {};
    (Object.keys(current) as (keyof typeof current)[]).forEach((key) => {
      if (current[key] !== value[key]) {
        (patch as Record<string, unknown>)[key] = value[key];
      }
    });
    if (Object.keys(patch).length) {
      this.form.patchValue(patch, { emitEvent: false });
    }
  }
}
