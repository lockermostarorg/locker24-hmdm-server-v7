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
  toQueryParams,
} from './fleet';
import { fleetRow } from './fixtures.spec';

describe('fleet', () => {
  const rows = [
    fleetRow({
      deviceId: 1,
      number: 'T-10',
      status: 'green',
      configurationId: 1,
      configurationName: 'Post',
      lastSeen: 300,
    }),
    fleetRow({
      deviceId: 2,
      number: 'T-2',
      status: 'red',
      reasons: ['offline'],
      configurationId: 2,
      configurationName: 'Beach',
      lastSeen: 100,
      model: 'SM-X200',
    }),
    fleetRow({
      deviceId: 3,
      number: 'T-3',
      status: 'yellow',
      reasons: ['kiosk_admin_off'],
      configurationId: 1,
      configurationName: 'Post',
      lastSeen: null,
      lockerShortUid: 'RS2',
    }),
    fleetRow({
      deviceId: 4,
      number: 'T-1',
      status: 'unknown',
      reasons: [],
      configurationId: null,
      configurationName: null,
      batteryPct: null,
      model: null,
    }),
  ];

  it('sorts by status worst first by default, then by number', () => {
    expect(sortRows(rows, DEFAULT_SORT).map((r) => r.deviceId)).toEqual([2, 3, 4, 1]);
  });

  it('sorts numbers naturally and keeps empty values last in both directions', () => {
    expect(sortRows(rows, { key: 'number', dir: 'asc' }).map((r) => r.number)).toEqual([
      'T-1',
      'T-2',
      'T-3',
      'T-10',
    ]);
    expect(sortRows(rows, { key: 'lastSeen', dir: 'asc' }).map((r) => r.deviceId)).toEqual([
      2, 1, 4, 3,
    ]);
    expect(sortRows(rows, { key: 'lastSeen', dir: 'desc' }).map((r) => r.deviceId)).toEqual([
      1, 2, 4, 3,
    ]);
    expect(sortRows(rows, { key: 'battery', dir: 'desc' }).at(-1)?.deviceId).toBe(4);
  });

  it('does not mutate the input', () => {
    const copy = [...rows];
    sortRows(rows, { key: 'number', dir: 'desc' });
    expect(rows).toEqual(copy);
  });

  it('filters by configuration, status, model and search text', () => {
    expect(
      filterRows(rows, { ...EMPTY_FILTER, configurationId: 1 }).map((r) => r.deviceId),
    ).toEqual([1, 3]);
    expect(filterRows(rows, { ...EMPTY_FILTER, status: 'admin' }).map((r) => r.deviceId)).toEqual([
      3,
    ]);
    expect(filterRows(rows, { ...EMPTY_FILTER, status: 'yellow' })).toEqual([]);
    expect(filterRows(rows, { ...EMPTY_FILTER, model: 'SM-X200' }).map((r) => r.deviceId)).toEqual([
      2,
    ]);
    expect(filterRows(rows, { ...EMPTY_FILTER, search: ' rs2 ' }).map((r) => r.deviceId)).toEqual([
      3,
    ]);
    expect(filterRows(rows, EMPTY_FILTER).length).toBe(4);
  });

  it('counts tablets per display status', () => {
    expect(countByStatus(rows)).toEqual({ red: 1, yellow: 0, admin: 1, unknown: 1, green: 1 });
  });

  it('builds sorted, distinct filter options', () => {
    expect(configurationOptions(rows)).toEqual([
      { value: 2, viewValue: 'Beach' },
      { value: 1, viewValue: 'Post' },
    ]);
    expect(modelOptions(rows).map((o) => o.value)).toEqual(['SM-X110', 'SM-X200']);
  });

  it('round-trips filter and sort through query parameters', () => {
    const filter = { configurationId: 2, status: 'red' as const, model: 'SM-X200', search: 'T-2' };
    const sort = { key: 'lastSeen' as const, dir: 'desc' as const };
    const params = toQueryParams(filter, sort);
    expect(parseFilter(params)).toEqual(filter);
    expect(parseSort(params)).toEqual(sort);
  });

  it('omits defaults from query parameters', () => {
    expect(toQueryParams(EMPTY_FILTER, DEFAULT_SORT)).toEqual({
      configuration: null,
      status: null,
      model: null,
      q: null,
      sort: null,
      dir: null,
    });
  });

  it('ignores invalid query parameters', () => {
    expect(parseFilter({ configuration: 'abc', status: 'purple' })).toEqual(EMPTY_FILTER);
    expect(parseSort({ sort: 'nope', dir: 'desc' })).toEqual(DEFAULT_SORT);
  });

  it('toggles the sort direction on the same column and resets it on a new one', () => {
    expect(nextSort({ key: 'number', dir: 'asc' }, 'number')).toEqual({
      key: 'number',
      dir: 'desc',
    });
    expect(nextSort({ key: 'number', dir: 'desc' }, 'number')).toEqual({
      key: 'number',
      dir: 'asc',
    });
    expect(nextSort({ key: 'number', dir: 'desc' }, 'battery')).toEqual({
      key: 'battery',
      dir: 'asc',
    });
  });
});
