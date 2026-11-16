import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatTree } from '@angular/material/tree';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { LicenseService } from '../../../auth/services/license.service';
import { AuthService } from '../../services/auth.service';

@Component({ selector: 'test-blank', template: '' })
class Blank {}

describe('SideMenu', () => {

  beforeEach(async () => {
    (window as any)['__DYNPLUGINS__'] = [
      { identifier: 'locked-plugin', nameLocalizationKey: 'plugin.locked', path: 'plugins/locked' },
    ];
    const { SideMenu } = await import('./side-menu');

    @Component({
      template: '<core-side-menu /><router-outlet />',
      imports: [SideMenu, RouterOutlet],
    })
    class HostComponent {}

    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        provideRouter([{ path: 'home', component: HostComponent, children: [{ path: '**', component: Blank }] }]),
        {
          provide: AuthService,
          useValue: { currentUser: () => ({ superAdmin: true }), hasPermission: () => true },
        },
        {
          provide: LicenseService,
          useValue: {
            pluginLicenses: signal({}),
            getValidPluginLicenses: () => of({ 'locked-plugin': false }),
          },
        },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
  });

  async function mount(url = '/home/dashboard') {
    const harness = await RouterTestingHarness.create(url);
    const root = harness.fixture.debugElement;
    root.query(By.directive(MatTree)).componentInstance.expandAll();
    harness.detectChanges();
    return { harness, root, el: harness.fixture.nativeElement as HTMLElement };
  }

  const links = (el: HTMLElement) =>
    Array.from<HTMLAnchorElement>(el.querySelectorAll('mat-tree-node a.menu-link'));

  it('renders items as anchors whose href is the full route under home', async () => {
    const { el } = await mount();
    const hrefs = links(el).map((a) => a.getAttribute('href'));

    expect(hrefs).toContain('/home/dashboard');
    expect(hrefs).toContain('/home/devices');
    expect(hrefs).toContain('/home/applications');
    expect(hrefs).toContain('/home/settings/users');
  });

  it('marks the item of the current route active', async () => {
    const { el } = await mount('/home/applications');
    const active = Array.from(el.querySelectorAll('mat-tree-node.active a.menu-link'));

    expect(active.map((a) => a.getAttribute('href'))).toEqual(['/home/applications']);
  });

  it('gives a locked item no href and sends a click to the licenses page only', async () => {
    const { el } = await mount();
    const locked = el.querySelector('mat-tree-node.node-locked a.menu-link') as HTMLAnchorElement;
    const spy = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    expect(locked).not.toBeNull();
    expect(locked.hasAttribute('href')).toBeFalse();

    locked.click();

    expect(spy).toHaveBeenCalledOnceWith(['/home/licenses']);
  });

  it('leaves the tree item as the only tab stop and activates the link on Enter', async () => {
    const { el } = await mount('/home/dashboard');
    const anchor = links(el).find((a) => a.getAttribute('href') === '/home/devices')!;
    const spy = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    expect(anchor.getAttribute('tabindex')).toBe('-1');

    anchor.closest('mat-tree-node')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
