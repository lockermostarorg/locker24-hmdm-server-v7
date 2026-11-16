import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { AboutDialog } from './about-dialog';

describe('AboutDialog', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [...baseTestProviders(), { provide: MatDialogRef, useValue: { close: () => {} } }],
    });
    await useEnglish(TestBed.inject(TranslateService));
  });

  it('shows the product name and the upstream credit with a link', () => {
    const fixture = TestBed.createComponent(AboutDialog);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Locker24 MDM');
    expect(el.textContent).toContain('Based on Headwind MDM (Apache License 2.0)');
    const link = el.querySelector<HTMLAnchorElement>('a.l24-upstream-credit-link');
    expect(link?.getAttribute('href')).toBe('https://h-mdm.com');
  });
});
