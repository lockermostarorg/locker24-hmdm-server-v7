export type ThemeMode = 'light' | 'dark' | 'system';
export type EffectiveTheme = 'light' | 'dark';

export type ThemeChangeDetail = {
  mode: ThemeMode;
  effective: EffectiveTheme;
};

export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark', 'system'];
export const THEME_STORAGE_KEY = 'l24-theme';
export const THEME_CHANGE_EVENT = 'l24-theme-change';

export function isThemeMode(value: unknown): value is ThemeMode {
  return typeof value === 'string' && (THEME_MODES as readonly string[]).includes(value);
}

export function themeToken(name: string, element: Element = document.documentElement): string {
  return getComputedStyle(element).getPropertyValue(name).trim();
}
