import { TFleetRow } from '../types/fleet.type';

export type TDisplayStatus = 'red' | 'yellow' | 'admin' | 'unknown' | 'green';

export const DISPLAY_STATUSES: TDisplayStatus[] = ['red', 'yellow', 'admin', 'unknown', 'green'];

export const STATUS_ICON: Record<TDisplayStatus, string> = {
  red: 'error',
  yellow: 'warning',
  admin: 'admin_panel_settings',
  unknown: 'help',
  green: 'check_circle',
};

export const KNOWN_REASONS = [
  'offline',
  'kiosk_not_running',
  'kiosk_admin_off',
  'battery_low',
  'not_charging',
  'temperature',
  'storage_low',
  'kiosk_version',
];

export type TKioskState = 'running' | 'not_running' | 'admin_off' | 'not_required' | 'unknown';

export function displayStatus(row: Pick<TFleetRow, 'status' | 'reasons'>): TDisplayStatus {
  switch (row.status) {
    case 'red':
    case 'green':
      return row.status;
    case 'yellow': {
      const reasons = row.reasons ?? [];
      return reasons.length > 0 && reasons.every((r) => r === 'kiosk_admin_off')
        ? 'admin'
        : 'yellow';
    }
    default:
      return 'unknown';
  }
}

export function statusRank(status: TDisplayStatus): number {
  return DISPLAY_STATUSES.indexOf(status);
}

export function kioskState(
  row: Pick<TFleetRow, 'kioskAdminUnlocked' | 'kioskRequired' | 'kioskRunning'>,
): TKioskState {
  if (row.kioskAdminUnlocked) {
    return 'admin_off';
  }
  if (row.kioskRequired === false) {
    return 'not_required';
  }
  if (row.kioskRunning === true) {
    return 'running';
  }
  if (row.kioskRunning === false) {
    return 'not_running';
  }
  return 'unknown';
}

export function reasonKey(reason: string): string {
  return KNOWN_REASONS.includes(reason)
    ? `plugin.locker24-health.reason.${reason}`
    : 'plugin.locker24-health.reason.other';
}
