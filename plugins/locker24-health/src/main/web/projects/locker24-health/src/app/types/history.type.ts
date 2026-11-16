export type THistoryPoint = {
  t: number;
  batteryPct: number | null;
  temperatureC: number | null;
  rssiDbm: number | null;
  charging: boolean | null;
  kioskRunning: boolean | null;
};

export type TOfflinePeriod = {
  from: number;
  to: number;
};

export type THistory = {
  from: number;
  to: number;
  bucketMs: number;
  truncated: boolean;
  points: THistoryPoint[];
  offlinePeriods: TOfflinePeriod[];
};
