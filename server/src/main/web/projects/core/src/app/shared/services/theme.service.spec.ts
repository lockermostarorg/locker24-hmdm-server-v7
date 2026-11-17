import { TestBed } from '@angular/core/testing';
import { THEME_CHANGE_EVENT, THEME_STORAGE_KEY } from 'hmdm-ui-kit';
import { EffectiveTheme, THEME_MEDIA_QUERY, THEME_STORAGE, ThemeService } from './theme.service';

class FakeMediaQuery {
  matches: boolean;
  private listeners: ((event: { matches: boolean }) => void)[] = [];

  constructor(matches: boolean) {
    this.matches = matches;
  }

  addEventListener(_type: string, listener: (event: { matches: boolean }) => void): void {
    this.listeners.push(listener);
  }

  removeEventListener(_type: string, listener: (event: { matches: boolean }) => void): void {
    this.listeners = this.listeners.filter((l) => l !== listener);
  }

  flip(matches: boolean): void {
    this.matches = matches;
    this.listeners.forEach((l) => l({ matches }));
  }
}

class MemoryStorage {
  values = new Map<string, string>();

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
}

class ThrowingStorage {
  getItem(): string | null {
    throw new DOMException('denied', 'SecurityError');
  }

  setItem(): void {
    throw new DOMException('denied', 'SecurityError');
  }
}

describe('ThemeService', () => {
  let media: FakeMediaQuery;
  let storage: MemoryStorage | ThrowingStorage;
  const root = document.documentElement;
  let initialTheme: string | null;

  const create = (): ThemeService => {
    TestBed.configureTestingModule({
      providers: [
        { provide: THEME_MEDIA_QUERY, useValue: media },
        { provide: THEME_STORAGE, useValue: storage },
      ],
    });
    return TestBed.inject(ThemeService);
  };

  beforeEach(() => {
    initialTheme = root.getAttribute('data-theme');
    media = new FakeMediaQuery(false);
    storage = new MemoryStorage();
  });

  afterEach(() => {
    if (initialTheme === null) {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', initialTheme);
    }
  });

  it('defaults to system and follows the OS preference', () => {
    media = new FakeMediaQuery(true);
    const service = create();

    expect(service.mode()).toBe('system');
    expect(service.effective()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
  });

  it('reads a stored choice', () => {
    (storage as MemoryStorage).setItem(THEME_STORAGE_KEY, 'dark');
    const service = create();

    expect(service.mode()).toBe('dark');
    expect(service.effective()).toBe('dark');
  });

  it('ignores an unknown stored value', () => {
    (storage as MemoryStorage).setItem(THEME_STORAGE_KEY, 'sepia');
    const service = create();

    expect(service.mode()).toBe('system');
    expect(service.effective()).toBe('light');
  });

  it('persists the choice and applies it to the page', () => {
    const service = create();

    service.setMode('dark');

    expect((storage as MemoryStorage).getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(service.mode()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');

    service.setMode('system');

    expect((storage as MemoryStorage).getItem(THEME_STORAGE_KEY)).toBe('system');
    expect(root.getAttribute('data-theme')).toBe('light');
  });

  it('follows OS changes only in system mode', () => {
    const service = create();
    const emitted: EffectiveTheme[] = [];
    service.changes$.subscribe((theme) => emitted.push(theme));

    media.flip(true);
    expect(service.effective()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');

    service.setMode('light');
    media.flip(false);
    media.flip(true);

    expect(service.effective()).toBe('light');
    expect(root.getAttribute('data-theme')).toBe('light');
    expect(emitted).toEqual(['dark', 'light']);

    service.setMode('system');
    expect(service.effective()).toBe('dark');
    expect(emitted).toEqual(['dark', 'light', 'dark']);
  });

  it('announces changes to the page for plugin remotes', () => {
    const service = create();
    const events: { mode: string; effective: string }[] = [];
    const listener = (event: Event) => events.push((event as CustomEvent).detail);
    document.addEventListener(THEME_CHANGE_EVENT, listener);

    service.setMode('dark');
    service.setMode('dark');
    document.removeEventListener(THEME_CHANGE_EVENT, listener);

    expect(events).toEqual([{ mode: 'dark', effective: 'dark' }]);
  });

  it('follows a theme change made in another tab without writing storage', () => {
    const service = create();
    const writes = spyOn(storage as MemoryStorage, 'setItem').and.callThrough();

    window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'dark' }));
    expect(service.mode()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');

    window.dispatchEvent(new StorageEvent('storage', { key: 'other', newValue: 'light' }));
    expect(service.mode()).toBe('dark');

    window.dispatchEvent(new StorageEvent('storage', { key: null }));
    expect(service.mode()).toBe('system');
    expect(root.getAttribute('data-theme')).toBe('light');
    expect(writes).not.toHaveBeenCalled();
  });

  it('works when storage access throws (private mode)', () => {
    storage = new ThrowingStorage();
    const service = create();

    expect(service.mode()).toBe('system');
    expect(() => service.setMode('dark')).not.toThrow();
    expect(service.mode()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
  });

  it('works without storage or media query support', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: THEME_MEDIA_QUERY, useValue: null },
        { provide: THEME_STORAGE, useValue: null },
      ],
    });
    const service = TestBed.inject(ThemeService);

    expect(service.effective()).toBe('light');
    service.setMode('dark');
    expect(service.effective()).toBe('dark');
  });
});
