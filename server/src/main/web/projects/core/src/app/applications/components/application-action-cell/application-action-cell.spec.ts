import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { baseTestProviders } from '../../../testing/test-providers';
import { AuthService } from '../../../shared/services/auth.service';
import { ApplicationActionCell } from './application-action-cell';

describe('ApplicationActionCell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationActionCell],
      providers: [
        ...baseTestProviders(),
        provideRouter([]),
        { provide: AuthService, useValue: { hasPermission: () => true, currentUser: () => ({}) } },
      ],
    });
  });

  function versions(): HTMLAnchorElement {
    const fixture = TestBed.createComponent(ApplicationActionCell);
    fixture.componentRef.setInput('params', { data: { id: 9, type: 'app', system: false } });
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('a');
  }

  it('renders the versions icon as a link to the versions list', () => {
    expect(versions().getAttribute('href')).toBe('/home/applications/9/versions');
  });

  it('navigates once on a plain click', () => {
    const spy = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    versions().click();

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
