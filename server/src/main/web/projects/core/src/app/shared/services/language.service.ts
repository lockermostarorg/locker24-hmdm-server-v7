import { HttpClient, HttpContext } from '@angular/common/http';
import { DestroyRef, effect, inject, Injectable, signal } from '@angular/core';
import { TranslateService, TranslationObject } from '@ngx-translate/core';
import { SKIP_ALERT } from 'hmdm-ui-kit';
import { catchError, EMPTY, Observable } from 'rxjs';
import {
  AVAILABLE_LANGUAGES,
  LANGUAGE_ALIASES,
  LANGUAGE_STORAGE_KEY,
  PANEL_LANGUAGES,
} from '../const/available-languages.const';
import { SettingsFacadeService } from './settings-facade.service';
import { THEME_STORAGE } from './theme.service';

export const DEFAULT_LANGUAGE = 'en_US';

export function matchLanguage(lang: string | null | undefined): string | null {
  if (!lang?.trim()) {
    return null;
  }

  const normalized = lang.trim().replace(/-/g, '_');
  const exact = AVAILABLE_LANGUAGES.find((l) => l.toLowerCase() === normalized.toLowerCase());

  if (exact) {
    return exact;
  }

  const [base, ...subtags] = normalized.toLowerCase().split('_');
  const alias = LANGUAGE_ALIASES[base];

  if (alias) {
    return alias;
  }

  const region = subtags.find((tag) => /^[a-z]{2}$/.test(tag));
  const sameBase = AVAILABLE_LANGUAGES.filter((l) => l.toLowerCase().startsWith(base + '_'));

  return (
    sameBase.find((l) => region && l.toLowerCase().endsWith('_' + region)) || sameBase[0] || null
  );
}

export function extractLanguageCode(lang: string | null | undefined): string {
  return matchLanguage(lang) ?? DEFAULT_LANGUAGE;
}

export function isPanelLanguage(lang: string | null | undefined): lang is string {
  return PANEL_LANGUAGES.some((l) => l.code === lang);
}

/** First browser language that is Bosnian/Croatian/Serbian (bs_BA) or English (en_US); en_US otherwise. */
export function detectBrowserLanguage(preferred: readonly (string | null | undefined)[]): string {
  for (const lang of preferred) {
    const match = matchLanguage(lang);

    if (isPanelLanguage(match)) {
      return match;
    }
  }

  return DEFAULT_LANGUAGE;
}

@Injectable({
  providedIn: 'root',
})
export class LanguageService {
  private readonly settingsFacadeService = inject(SettingsFacadeService);
  private readonly translateService = inject(TranslateService);
  private readonly http: HttpClient = inject(HttpClient);
  private readonly storage = inject(THEME_STORAGE);
  private readonly defaultLanguage = DEFAULT_LANGUAGE;
  private readonly languageState = signal<string>(
    this.translateService.getCurrentLang() || DEFAULT_LANGUAGE,
  );

  /** The language the panel is shown in. */
  readonly language = this.languageState.asReadonly();

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
      this.languageState.set(event.lang);

      if (!this.currentLanguage) {
        return;
      }

      this.loadPluginTranslations();
    });

    const view = typeof window === 'undefined' ? null : window;
    const onOtherTab = (event: StorageEvent) => {
      if (event.key === LANGUAGE_STORAGE_KEY || event.key === null) {
        this.init();
      }
    };
    view?.addEventListener('storage', onOtherTab);
    inject(DestroyRef).onDestroy(() => view?.removeEventListener('storage', onOtherTab));
  }

  /** Picks the panel language for this browser; it wins over the browser and the settings. */
  setLanguage(language: string): void {
    if (!isPanelLanguage(language)) {
      return;
    }

    try {
      this.storage?.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch {
      // Storage blocked: the choice lasts for this page only.
    }

    this.currentLanguage = language;
    this.translateService.use(language);
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
    const stored = this.readStoredLanguage();
    let language: string;

    if (stored) {
      language = stored;
    } else if (!settings || settings.useDefaultLanguage) {
      language = this.getBrowserLanguage();
    } else {
      language = this.extractLanguageCode(settings.language || this.defaultLanguage);
    }

    if (settings) {
      this.useDefaultLanguage = settings.useDefaultLanguage;
    } else if (language === this.currentLanguage) {
      return;
    }

    this.currentLanguage = language;
    this.translateService.use(language);
  }

  private readStoredLanguage(): string | null {
    try {
      const stored = this.storage?.getItem(LANGUAGE_STORAGE_KEY);
      return isPanelLanguage(stored) ? stored : null;
    } catch {
      return null;
    }
  }

  private getBrowserLanguage(): string {
    return detectBrowserLanguage([
      ...(navigator.languages ?? []),
      navigator.language,
      (navigator as any).userLanguage,
    ]);
  }

  private extractLanguageCode(lang: string): string {
    return extractLanguageCode(lang);
  }
}
