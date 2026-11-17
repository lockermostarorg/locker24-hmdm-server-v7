import { onThemeChange } from './theme';

describe('onThemeChange', () => {
  const root = document.documentElement;
  const original = root.getAttribute('data-theme');
  let mediaListener: (() => void) | null;
  let media: jasmine.SpyObj<MediaQueryList>;

  const flush = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    mediaListener = null;
    media = jasmine.createSpyObj<MediaQueryList>('media', [
      'addEventListener',
      'removeEventListener',
    ]);
    media.addEventListener.and.callFake((_type: string, listener: unknown) => {
      mediaListener = listener as () => void;
    });
    spyOn(window, 'matchMedia').and.returnValue(media);
  });

  afterEach(() => {
    if (original === null) {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', original);
    }
  });

  it('calls back when the panel switches data-theme on <html>', async () => {
    const callback = jasmine.createSpy('callback');
    const stop = onThemeChange(callback);
    root.setAttribute('data-theme', 'dark');
    await flush();
    root.setAttribute('data-theme', 'light');
    await flush();
    root.setAttribute('lang', root.getAttribute('lang') ?? 'en');
    await flush();
    expect(callback).toHaveBeenCalledTimes(2);
    stop();
  });

  it('calls back when the OS colour scheme changes', () => {
    const callback = jasmine.createSpy('callback');
    const stop = onThemeChange(callback);
    expect(window.matchMedia).toHaveBeenCalledWith('(prefers-color-scheme: dark)');
    mediaListener!();
    expect(callback).toHaveBeenCalledTimes(1);
    stop();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', mediaListener!);
  });

  it('stops calling back once stopped', async () => {
    const callback = jasmine.createSpy('callback');
    onThemeChange(callback)();
    root.setAttribute('data-theme', 'dark');
    await flush();
    expect(callback).not.toHaveBeenCalled();
  });
});
