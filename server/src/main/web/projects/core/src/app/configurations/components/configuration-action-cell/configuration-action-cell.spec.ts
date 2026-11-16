import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders } from '../../../testing/test-providers';
import { AuthService } from '../../../shared/services/auth.service';
import { ConfigurationActionCell } from './configuration-action-cell';

describe('ConfigurationActionCell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConfigurationActionCell],
      providers: [
        ...baseTestProviders(),
        provideRouter([]),
        { provide: AuthService, useValue: { hasPermission: () => true } },
      ],
    });
    TestBed.inject(TranslateService);
  });

  function edit(): HTMLAnchorElement {
    const fixture = TestBed.createComponent(ConfigurationActionCell);
    fixture.componentRef.setInput('params', { data: { id: 5 } });
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('a');
  }

  it('renders the edit icon as a link to the configuration', () => {
    expect(edit().getAttribute('href')).toBe('/home/configurations/details/5');
  });

  it('navigates once on a plain click', () => {
    const spy = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    edit().click();

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
