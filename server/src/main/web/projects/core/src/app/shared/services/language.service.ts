import { HttpClient, HttpContext } from '@angular/common/http';
import { effect, inject, Injectable } from '@angular/core';
import { TranslateService, TranslationObject } from '@ngx-translate/core';
import { SKIP_ALERT } from 'hmdm-ui-kit';
import { catchError, EMPTY, Observable } from 'rxjs';
import { AVAILABLE_LANGUAGES, LANGUAGE_ALIASES } from '../const/available-languages.const';
import { SettingsFacadeService } from './settings-facade.service';

export const DEFAULT_LANGUAGE = 'en_US';

export function extractLanguageCode(lang: string | null | undefined): string {
  if (!lang) {
    return DEFAULT_LANGUAGE;
  }

  const normalized = lang.trim().replace(/-/g, '_');
  const exact = AVAILABLE_LANGUAGES.find((l) => l.toLowerCase() === normalized.toLowerCase());

  if (exact) {
    return exact;
  }

  const base = normalized.split('_')[0].toLowerCase();
  const alias = LANGUAGE_ALIASES[base];

  if (alias) {
    return alias;
  }

  const region = normalized.split('_').slice(1).join('_').toLowerCase();
  const sameBase = AVAILABLE_LANGUAGES.filter((l) => l.toLowerCase().startsWith(base + '_'));

  return (
    sameBase.find((l) => region && l.toLowerCase().endsWith('_' + region)) ||
    sameBase[0] ||
    DEFAULT_LANGUAGE
  );
}

@Injectable({
  providedIn: 'root',
})
export class LanguageService {
  private readonly settingsFacadeService = inject(SettingsFacadeService);
  private readonly translateService = inject(TranslateService);
  private readonly http: HttpClient = inject(HttpClient);
  private readonly defaultLanguage = DEFAULT_LANGUAGE;

  private currentLanguage: string | null = null;
  private useDefaultLanguage: boolean | null = null;

  constructor() {
    effect(() => {
      const settings = this.settingsFacadeService.settings();

      if (
        settings?.language !== this.currentLanguage ||
        settings?.useDefaultLanguage !== this.useDefaultLanguage
      ) {
        this.init();
        this.loadPluginTranslations();
      }
    });

    this.translateService.onLangChange.subscribe((event) => {
      this.currentLanguage = event.lang;

      if (!this.currentLanguage) {
        return;
      }

      this.loadPluginTranslations();
    });
  }

  loadPluginTranslations() {
    const plugins = (window as any)['__DYNPLUGINS__'] || [];

    if (!this.currentLanguage) {
      return;
    }

    plugins.forEach((plugin: any) => {
      const language = this.currentLanguage!;

      this.getPluginTranslations(plugin.baseUrl, language)
        .pipe(
          catchError(() =>
            language === this.defaultLanguage
              ? EMPTY
              : this.getPluginTranslations(plugin.baseUrl, this.defaultLanguage).pipe(
                  catchError(() => EMPTY),
                ),
          ),
        )
        .subscribe((translations) => {
          if (!this.currentLanguage) {
            return;
          }

          // Merge with existing translations (true = merge, false = replace)
          this.translateService.setTranslation(this.currentLanguage, translations, true);
        });
    });
  }

  private getPluginTranslations(baseUrl: string, language: string): Observable<TranslationObject> {
    return this.http.get<TranslationObject>(`${baseUrl}/i18n/${language}.json`, {
      context: new HttpContext().set(SKIP_ALERT, true),
    });
  }

  init(): void {
    const settings = this.settingsFacadeService.settings();

    if (!settings) {
      return;
    }

    if (settings.useDefaultLanguage) {
      const browserLang = this.getBrowserLanguage();
      const language = this.extractLanguageCode(browserLang);
      console.log('Setting language to browser language:', browserLang);

      this.useDefaultLanguage = true;
      this.currentLanguage = language;
      this.translateService.use(language);
    } else {
      const language = this.extractLanguageCode(settings.language || this.defaultLanguage);
      console.log('Setting language to configured language:', language);

      this.useDefaultLanguage = false;
      this.currentLanguage = language;
      this.translateService.use(language);
    }
  }

  private getBrowserLanguage(): string {
    const navigatorLang = navigator.language || (navigator as any).userLanguage;
    return navigatorLang ? this.extractLanguageCode(navigatorLang) : this.defaultLanguage;
  }

  private extractLanguageCode(lang: string): string {
    return extractLanguageCode(lang);
  }
}
