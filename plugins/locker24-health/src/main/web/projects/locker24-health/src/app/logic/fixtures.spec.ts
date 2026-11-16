import { TFleetRow } from '../types/fleet.type';

export function fleetRow(overrides: Partial<TFleetRow> = {}): TFleetRow {
  return {
    deviceId: 1,
    number: 'T-1',
    description: null,
    configurationId: 10,
    configurationName: 'Post',
    groups: null,
    lockerShortUid: null,
    lastSeen: null,
    status: 'green',
    reasons: [],
    batteryPct: 80,
    charging: true,
    temperatureC: 30,
    freeStorageMb: 4096,
    kioskAppVersion: '1.3.2',
    agentVersion: '6.40-l24.5',
    kioskRequired: true,
    kioskRunning: true,
    kioskAdminUnlocked: false,
    model: 'SM-X110',
    ...overrides,
  };
}
