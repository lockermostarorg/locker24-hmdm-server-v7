import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { homeRedirectGuard } from './home-redirect.guard';

describe('homeRedirectGuard', () => {
  let hasPermission: jasmine.Spy;

  function run(): string {
    const result = TestBed.runInInjectionContext(() =>
      homeRedirectGuard({} as any, {} as any),
    ) as UrlTree;
    return TestBed.inject(Router).serializeUrl(result);
  }

  beforeEach(() => {
    hasPermission = jasmine.createSpy('hasPermission').and.returnValue(true);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { hasPermission } }],
    });
  });

  afterEach(() => delete (window as any)['__DYNPLUGINS__']);

  it('opens fleet health when the plugin is loaded and allowed', () => {
    (window as any)['__DYNPLUGINS__'] = [
      { identifier: 'locker24-health', path: 'plugins/locker24-health', functionsPermission: 'plugin_locker24_health_access' },
    ];

    expect(run()).toBe('/home/plugins/locker24-health');
    expect(hasPermission).toHaveBeenCalledWith('plugin_locker24_health_access');
  });

  it('falls back to devices without the permission', () => {
    (window as any)['__DYNPLUGINS__'] = [
      { identifier: 'locker24-health', path: 'plugins/locker24-health', functionsPermission: 'plugin_locker24_health_access' },
    ];
    hasPermission.and.returnValue(false);

    expect(run()).toBe('/home/devices');
  });

  it('falls back to devices when the plugin is not loaded', () => {
    (window as any)['__DYNPLUGINS__'] = [];

    expect(run()).toBe('/home/devices');
  });
});
