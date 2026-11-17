import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LanguageService } from '../../services/language.service';
import { THEME_STORAGE } from '../../services/theme.service';
import { baseTestProviders } from '../../../testing/test-providers';
import { LanguageSwitch } from './language-switch';

describe('LanguageSwitch', () => {
  it('shows the current language and offers only Bosnian and English', async () => {
    TestBed.configureTestingModule({
      imports: [LanguageSwitch],
      providers: [
        ...baseTestProviders(),
        { provide: THEME_STORAGE, useValue: { getItem: () => null, setItem: () => undefined } },
      ],
    });
    const service = TestBed.inject(LanguageService);
    spyOn(service, 'setLanguage').and.callThrough();
    const fixture = TestBed.createComponent(LanguageSwitch);
    service.setLanguage('bs_BA');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('BS');

    fixture.debugElement.query(By.css('.language-switch')).nativeElement.click();
    fixture.detectChanges();
    const items = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-panel [mat-menu-item]'),
    );

    expect(items.map((i) => i.textContent?.trim())).toEqual(['checkBosanski', 'languageEnglish']);

    items[1].click();
    fixture.detectChanges();

    expect(service.setLanguage).toHaveBeenCalledWith('en_US');
    expect(fixture.nativeElement.textContent).toContain('EN');
  });
});
