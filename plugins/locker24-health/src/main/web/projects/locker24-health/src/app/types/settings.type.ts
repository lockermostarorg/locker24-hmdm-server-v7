export type THealthSettings = {
  webhookUrlSet: boolean;
  timeZone: string;
  editable: boolean;
};

export type THealthSettingsUpdate = {
  timeZone?: string;
  webhookUrl?: string;
};
