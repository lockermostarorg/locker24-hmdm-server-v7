import { TestBed } from '@angular/core/testing';
import { UrlCell } from './url-cell';

describe('UrlCell', () => {
  function render(data: object): HTMLElement {
    const fixture = TestBed.createComponent(UrlCell);
    fixture.componentRef.setInput('params', { data });
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('renders the URL as a link that opens in a new tab', () => {
    const anchor = render({ url: 'https://x.test/a.apk', split: false }).querySelector('a')!;

    expect(anchor.getAttribute('href')).toBe('https://x.test/a.apk');
    expect(anchor.getAttribute('target')).toBe('_blank');
    expect(anchor.getAttribute('rel')).toBe('noopener');
    expect(anchor.textContent?.trim()).toBe('https://x.test/a.apk');
  });

  it('renders both split URLs as links', () => {
    const anchors = Array.from(
      render({ split: true, urlArm64: 'https://x.test/64.apk', urlArmeabi: 'https://x.test/32.apk' })
        .querySelectorAll('a'),
    );

    expect(anchors.map((a) => a.getAttribute('href'))).toEqual([
      'https://x.test/64.apk',
      'https://x.test/32.apk',
    ]);
    anchors.forEach((a) => {
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener');
    });
  });

  it('renders nothing when there is no URL', () => {
    expect(render({ split: false }).querySelector('a')).toBeNull();
  });
});
