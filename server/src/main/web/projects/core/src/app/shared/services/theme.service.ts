import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import {
  EffectiveTheme,
  isThemeMode,
  THEME_CHANGE_EVENT,
  THEME_STORAGE_KEY,
  ThemeChangeDetail,
  ThemeMode,
} from 'hmdm-ui-kit';
import { Observable, Subject } from 'rxjs';

export type { EffectiveTheme, ThemeMode } from 'hmdm-ui-kit';

export type ThemeStorage = Pick<Storage, 'getItem' | 'setItem'>;

export type ThemeMediaQuery = Pick<MediaQueryList, 'matches'> & {
  addEventListener(type: 'change', listener: (event: { matches: boolean }) => void): void;
  removeEventListener(type: 'change', listener: (event: { matches: boolean }) => void): void;
};

export const THEME_STORAGE = new InjectionToken<ThemeStorage | null>('THEME_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return inject(DOCUMENT).defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  },
});

export const THEME_MEDIA_QUERY = new InjectionToken<ThemeMediaQuery | null>('THEME_MEDIA_QUERY', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).defaultView?.matchMedia?.('(prefers-color-scheme: dark)') ?? null,
});

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly storage = inject(THEME_STORAGE);
  private readonly query = inject(THEME_MEDIA_QUERY);

  private readonly modeState = signal<ThemeMode>(this.readStoredMode());
  private readonly systemDark = signal(!!this.query?.matches);
  private readonly changesSubject = new Subject<EffectiveTheme>();

  readonly mode = this.modeState.asReadonly();
  readonly effective = computed<EffectiveTheme>(() => {
    const mode = this.modeState();
    return mode === 'system' ? (this.systemDark() ? 'dark' : 'light') : mode;
  });
  readonly changes$: Observable<EffectiveTheme> = this.changesSubject.asObservable();

  constructor() {
    this.applyToPage();

    const onSystemChange = (event: { matches: boolean }) =>
      this.update(() => this.systemDark.set(event.matches));
    this.query?.addEventListener('change', onSystemChange);
    inject(DestroyRef).onDestroy(() => {
      this.query?.removeEventListener('change', onSystemChange);
      this.changesSubject.complete();
    });
  }

  setMode(mode: ThemeMode): void {
    if (!isThemeMode(mode)) {
      return;
    }

    this.persist(mode);
    this.update(() => this.modeState.set(mode));
  }

  private persist(mode: ThemeMode): void {
    try {
      this.storage?.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      return;
    }
  }

  private update(change: () => void): void {
    const before = this.effective();
    change();
    const after = this.effective();

    if (after === before) {
      return;
    }

    this.applyToPage();
    this.changesSubject.next(after);
    const detail: ThemeChangeDetail = { mode: this.modeState(), effective: after };
    this.document.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT, { detail }));
  }

  private applyToPage(): void {
    this.document.documentElement.setAttribute('data-theme', this.effective());
  }

  private readStoredMode(): ThemeMode {
    try {
      const stored = this.storage?.getItem(THEME_STORAGE_KEY);
      return isThemeMode(stored) ? stored : 'system';
    } catch {
      return 'system';
    }
  }
}
