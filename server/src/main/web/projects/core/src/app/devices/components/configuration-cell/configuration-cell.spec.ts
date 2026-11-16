import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ConfigurationCell } from './configuration-cell';

describe('ConfigurationCell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function render(data: object): HTMLElement {
    const fixture = TestBed.createComponent(ConfigurationCell);
    fixture.componentRef.setInput('params', { data });
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('is a link to the configuration', () => {
    const anchor = render({ configuration: { id: 7, name: 'Kiosk' } }).querySelector('a')!;

    expect(anchor.getAttribute('href')).toBe('/home/configurations/details/7');
    expect(anchor.textContent?.trim()).toBe('Kiosk');
  });

  it('navigates once on a plain click', () => {
    const spy = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const anchor = render({ configuration: { id: 7, name: 'Kiosk' } }).querySelector('a')!;

    anchor.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('renders plain text when the device has no configuration', () => {
    const el = render({});

    expect(el.querySelector('a')).toBeNull();
  });
});
