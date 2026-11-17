import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

export const baseTestProviders = (): (Provider | EnvironmentProviders)[] => [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideNoopAnimations(),
  provideTranslateService({ fallbackLang: 'en_US' }),
];

export const LOCALES = [
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

export async function loadLocale(locale: string): Promise<Record<string, string>> {
  const response = await fetch(`/i18n/${locale}.json`);
  return response.json();
}

export async function useEnglish(translate: TranslateService): Promise<void> {
  translate.setTranslation('en_US', await loadLocale('en_US'));
  translate.use('en_US');
}
