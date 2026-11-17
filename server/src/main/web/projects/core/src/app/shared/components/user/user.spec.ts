import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { AuthService } from '../../services/auth.service';
import { IdleService } from '../../services/idle.service';
import { THEME_MEDIA_QUERY, THEME_STORAGE, ThemeService } from '../../services/theme.service';
import { User } from './user';

describe('User menu theme switch', () => {
  let root: string | null;

  beforeEach(async () => {
    root = document.documentElement.getAttribute('data-theme');
    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { currentUser: signal({ name: 'admin' }), hasPermission: () => false },
        },
        { provide: IdleService, useValue: { clearTimeout: () => {} } },
        { provide: THEME_MEDIA_QUERY, useValue: null },
        { provide: THEME_STORAGE, useValue: null },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
  });

  afterEach(() => {
    if (root === null) {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', root);
    }
  });

  const openThemeMenu = async () => {
    const fixture = TestBed.createComponent(User);
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const menu = await loader.getHarness(MatMenuHarness);
    await menu.open();
    const items = await menu.getItems({ text: /Theme/ });
    expect(items.length).toBe(1);
    const submenu = await items[0].getSubmenu();
    await submenu!.open();
    return submenu!;
  };

  it('offers Light, Dark and System and marks the current choice', async () => {
    const submenu = await openThemeMenu();
    const items = await submenu.getItems();
    const labels = await Promise.all(items.map((item) => item.getText()));

    expect(labels.length).toBe(3);
    expect(labels[0]).toMatch(/Light$/);
    expect(labels[1]).toMatch(/Dark$/);
    expect(labels[2]).toMatch(/System$/);
    const checked = await Promise.all(
      items.map(async (item) => (await item.host()).getAttribute('aria-checked')),
    );
    expect(checked).toEqual(['false', 'false', 'true']);
  });

  it('applies the chosen theme', async () => {
    const theme = TestBed.inject(ThemeService);
    const submenu = await openThemeMenu();

    await submenu.clickItem({ text: /Dark/ });

    expect(theme.mode()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
