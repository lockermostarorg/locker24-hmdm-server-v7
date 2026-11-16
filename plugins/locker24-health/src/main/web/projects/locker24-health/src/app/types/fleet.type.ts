export type THealthStatus = 'green' | 'yellow' | 'red' | 'unknown';

export type TReasonCode =
  | 'offline'
  | 'kiosk_not_running'
  | 'kiosk_admin_off'
  | 'battery_low'
  | 'not_charging'
  | 'temperature'
  | 'storage_low'
  | 'kiosk_version';

export type TFleetRow = {
  deviceId: number;
  number: string;
  description: string | null;
  configurationId: number | null;
  configurationName: string | null;
  groups: string | null;
  lockerShortUid: string | null;
  lastSeen: number | null;
  status: THealthStatus;
  reasons: string[] | null;
  batteryPct: number | null;
  charging: boolean | null;
  temperatureC: number | null;
  freeStorageMb: number | null;
  kioskAppVersion: string | null;
  agentVersion: string | null;
  kioskRequired: boolean | null;
  kioskRunning: boolean | null;
  kioskAdminUnlocked: boolean | null;
  model: string | null;
};

export type TFleetResponse = {
  devices: TFleetRow[];
  truncated: boolean;
};
