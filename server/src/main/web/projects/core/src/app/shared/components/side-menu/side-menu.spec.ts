import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { AuthService } from '../../services/auth.service';
import type { SideMenu } from './side-menu';

describe('SideMenu', () => {
  let SideMenuClass: typeof SideMenu;

  beforeEach(async () => {
    (window as any)['__DYNPLUGINS__'] = [];
    SideMenuClass = (await import('./side-menu')).SideMenu;
    TestBed.configureTestingModule({
      imports: [SideMenuClass],
      providers: [
        ...baseTestProviders(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { currentUser: () => ({ superAdmin: true }), hasPermission: () => true },
        },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
  });

  it('renders every leaf item as an anchor with a real href', async () => {
    const fixture = TestBed.createComponent(SideMenuClass);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const anchors = Array.from<HTMLAnchorElement>(
      fixture.nativeElement.querySelectorAll('mat-tree-node a.menu-link'),
    );
    const hrefs = anchors.map((a) => a.getAttribute('href'));

    expect(anchors.length).toBeGreaterThan(3);
    expect(hrefs).toContain('/home/dashboard');
    expect(hrefs).toContain('/home/devices');
    hrefs.forEach((href) => expect(href).toMatch(/^\//));
  });
});
