import { TFleetRow } from '../types/fleet.type';
import { DISPLAY_STATUSES, displayStatus, kioskState, statusRank, TDisplayStatus } from './status';

export type TSortKey =
  | 'status'
  | 'number'
  | 'configuration'
  | 'groups'
  | 'locker'
  | 'lastSeen'
  | 'battery'
  | 'temperature'
  | 'kioskAppVersion'
  | 'agentVersion'
  | 'kioskState';

export type TSortDir = 'asc' | 'desc';

export type TSort = {
  key: TSortKey;
  dir: TSortDir;
};

export type TFleetFilter = {
  configurationId: number | null;
  status: TDisplayStatus | null;
  model: string | null;
  search: string;
};

export const DEFAULT_SORT: TSort = { key: 'status', dir: 'asc' };

export const EMPTY_FILTER: TFleetFilter = {
  configurationId: null,
  status: null,
  model: null,
  search: '',
};

const SORT_KEYS: TSortKey[] = [
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
];

const collator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

function sortValue(row: TFleetRow, key: TSortKey): string | number | null {
  switch (key) {
    case 'status':
      return statusRank(displayStatus(row));
    case 'number':
      return row.number;
    case 'configuration':
      return row.configurationName;
    case 'groups':
      return row.groups;
    case 'locker':
      return row.lockerShortUid;
    case 'lastSeen':
      return row.lastSeen;
    case 'battery':
      return row.batteryPct;
    case 'temperature':
      return row.temperatureC;
    case 'kioskAppVersion':
      return row.kioskAppVersion;
    case 'agentVersion':
      return row.agentVersion;
    case 'kioskState':
      return kioskState(row);
  }
}

function compareValues(a: string | number, b: string | number): number {
  if (typeof a === 'number' && typeof b === 'number') {
    return a - b;
  }
  return collator.compare(String(a), String(b));
}

export function sortRows(rows: TFleetRow[], sort: TSort): TFleetRow[] {
  const factor = sort.dir === 'asc' ? 1 : -1;
  return [...rows].sort((left, right) => {
    const a = sortValue(left, sort.key);
    const b = sortValue(right, sort.key);
    const aEmpty = a === null || a === '';
    const bEmpty = b === null || b === '';
    if (aEmpty !== bEmpty) {
      return aEmpty ? 1 : -1;
    }
    const primary = aEmpty || bEmpty ? 0 : compareValues(a!, b!) * factor;
    return primary !== 0 ? primary : collator.compare(left.number, right.number);
  });
}

export function filterRows(rows: TFleetRow[], filter: TFleetFilter): TFleetRow[] {
  const search = filter.search.trim().toLowerCase();
  return rows.filter((row) => {
    if (filter.configurationId !== null && row.configurationId !== filter.configurationId) {
      return false;
    }
    if (filter.status !== null && displayStatus(row) !== filter.status) {
      return false;
    }
    if (filter.model !== null && row.model !== filter.model) {
      return false;
    }
    if (search) {
      const haystack = [row.number, row.description, row.lockerShortUid, row.groups]
        .filter((v): v is string => !!v)
        .join('\n')
        .toLowerCase();
      if (!haystack.includes(search)) {
        return false;
      }
    }
    return true;
  });
}

export function countByStatus(rows: TFleetRow[]): Record<TDisplayStatus, number> {
  const counts = { red: 0, yellow: 0, admin: 0, unknown: 0, green: 0 };
  rows.forEach((row) => counts[displayStatus(row)]++);
  return counts;
}

export function configurationOptions(rows: TFleetRow[]): { value: number; viewValue: string }[] {
  const byId = new Map<number, string>();
  rows.forEach((row) => {
    if (row.configurationId !== null) {
      byId.set(row.configurationId, row.configurationName || `#${row.configurationId}`);
    }
  });
  return [...byId.entries()]
    .map(([value, viewValue]) => ({ value, viewValue }))
    .sort((a, b) => collator.compare(a.viewValue, b.viewValue));
}

export function modelOptions(rows: TFleetRow[]): { value: string; viewValue: string }[] {
  const models = new Set<string>();
  rows.forEach((row) => row.model && models.add(row.model));
  return [...models].sort(collator.compare).map((value) => ({ value, viewValue: value }));
}

export function parseFilter(params: Record<string, string | null | undefined>): TFleetFilter {
  const configurationId = Number(params['configuration']);
  const status = params['status'] as TDisplayStatus | undefined;
  return {
    configurationId:
      params['configuration'] && Number.isInteger(configurationId) ? configurationId : null,
    status: status && DISPLAY_STATUSES.includes(status) ? status : null,
    model: params['model'] || null,
    search: params['q'] || '',
  };
}

export function parseSort(params: Record<string, string | null | undefined>): TSort {
  const key = params['sort'] as TSortKey | undefined;
  if (!key || !SORT_KEYS.includes(key)) {
    return DEFAULT_SORT;
  }
  return { key, dir: params['dir'] === 'desc' ? 'desc' : 'asc' };
}

export function toQueryParams(filter: TFleetFilter, sort: TSort): Record<string, string | null> {
  const isDefaultSort = sort.key === DEFAULT_SORT.key && sort.dir === DEFAULT_SORT.dir;
  return {
    configuration: filter.configurationId !== null ? String(filter.configurationId) : null,
    status: filter.status,
    model: filter.model,
    q: filter.search.trim() ? filter.search : null,
    sort: isDefaultSort ? null : sort.key,
    dir: isDefaultSort ? null : sort.dir,
  };
}

export function nextSort(current: TSort, key: TSortKey): TSort {
  if (current.key !== key) {
    return { key, dir: 'asc' };
  }
  return { key, dir: current.dir === 'asc' ? 'desc' : 'asc' };
}
