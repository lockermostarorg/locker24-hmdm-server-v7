export type TReleaseMode = 'schedule' | 'immediate' | 'hold';

export type TReleaseSummary = {
  configurations: number;
  devices: number;
  mode: TReleaseMode;
  pushFailed?: number;
  notLatest?: boolean;
  requestedMode?: TReleaseMode;
};
