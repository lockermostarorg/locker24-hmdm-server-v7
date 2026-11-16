import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { shallow } from '../../testing/ui-stubs.spec';
import { Settings } from './settings';

const URL = 'rest/private/plugin-locker24-health/settings';

describe('Settings page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    shallow(Settings);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(editable: boolean, webhookUrlSet: boolean) {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    http.expectOne(URL).flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet, timeZone: 'Europe/Sarajevo', editable },
    });
    fixture.detectChanges();
    return fixture;
  }

  function buttons(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim() ?? '');
  }

  it('is read-only without the settings permission', () => {
    const fixture = render(false, true);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('plugin.locker24-health.settings.read_only');
    expect(el.textContent).toContain('plugin.locker24-health.settings.webhook.configured');
    expect(buttons(el).length).toBe(0);
    expect(fixture.componentInstance.timeZoneControl.disabled).toBeTrue();
    expect(el.querySelector('hmdm-select-search')).toBeNull();
    expect(el.textContent).toContain('Europe/Sarajevo');
  });

  it('sends only the time zone when the webhook is kept', () => {
    const fixture = render(true, true);
    fixture.componentInstance.timeZoneControl.setValue('UTC');
    fixture.componentInstance.save();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ timeZone: 'UTC' });
    req.flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet: true, timeZone: 'UTC', editable: true },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('plugin.locker24-health.settings.saved');
  });

  it('replaces the webhook from a password field and never shows it afterwards', () => {
    const fixture = render(true, false);
    const component = fixture.componentInstance;
    component.startReplace();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[matInput]') as HTMLInputElement;
    expect(input.type).toBe('password');

    component.webhookControl.setValue('http://insecure.example/hook');
    expect(component.canSave()).toBeFalse();
    component.webhookControl.setValue('https://hooks.example.com/secret-token');
    component.save();
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({
      timeZone: 'Europe/Sarajevo',
      webhookUrl: 'https://hooks.example.com/secret-token',
    });
    req.flush({
      status: 'OK',
      message: null,
      data: { webhookUrlSet: true, timeZone: 'Europe/Sarajevo', editable: true },
    });
    fixture.detectChanges();
    expect(component.webhookControl.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('secret-token');
    expect(fixture.nativeElement.textContent).toContain(
      'plugin.locker24-health.settings.webhook.configured',
    );
  });

  it('clears the webhook with an empty string', () => {
    const fixture = render(true, true);
    fixture.componentInstance.startClear();
    fixture.componentInstance.save();
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ timeZone: 'Europe/Sarajevo', webhookUrl: '' });
    req.flush({ status: 'ERROR', message: 'error.validation', data: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('plugin.locker24-health.error.validation');
  });
});
