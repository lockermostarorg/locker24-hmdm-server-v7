export const AVAILABLE_LANGUAGES: string[] = [
  'ar_AE',
  'bs_BA',
  'de_DE',
  'en_US',
  'es_ES',
  'fr_FR',
  'it_IT',
  'ja_JP',
  'pt_PT',
  'ru_RU',
  'tr_TR',
  'vi_VN',
  'zh_CN',
  'zh_TW',
];

export const LANGUAGE_ALIASES: Record<string, string> = {
  bs: 'bs_BA',
  hr: 'bs_BA',
  sr: 'bs_BA',
  sh: 'bs_BA',
};

export type TPanelLanguage = { code: string; short: string; label: string; htmlLang: string };

export const PANEL_LANGUAGES: readonly TPanelLanguage[] = [
  { code: 'bs_BA', short: 'BS', label: 'Bosanski', htmlLang: 'bs' },
  { code: 'en_US', short: 'EN', label: 'English', htmlLang: 'en' },
];

export const LANGUAGE_STORAGE_KEY = 'l24-language';
