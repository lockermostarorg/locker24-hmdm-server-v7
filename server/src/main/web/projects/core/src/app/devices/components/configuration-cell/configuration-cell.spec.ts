import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { ConfigurationCell } from './configuration-cell';

describe('ConfigurationCell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(ConfigurationCell);
    fixture.componentRef.setInput('params', { data: { configuration: { id: 7, name: 'Kiosk' } } });
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('is a link to the configuration', () => {
    const anchor = render().querySelector('a')!;

    expect(anchor.getAttribute('href')).toBe('/home/configurations/details/7');
    expect(anchor.textContent?.trim()).toBe('Kiosk');
  });

  it('navigates once on a plain click', async () => {
    const spy = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const anchor = render().querySelector('a')!;
    anchor.addEventListener('click', (e) => e.preventDefault());

    anchor.click();

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
