import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TApplicationDTO } from '../../../entity/application/types/application-dto.type';
import { AuthService } from '../../../shared/services/auth.service';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { ConfigurationAppsFacadeService } from '../../services/configuration-applications-facade.service';
import { AppsFollowCell } from './apps-follow-cell';

describe('AppsFollowCell', () => {
  let facade: jasmine.SpyObj<ConfigurationAppsFacadeService>;

  const app = (patch: Partial<TApplicationDTO>): TApplicationDTO =>
    ({
      id: 1,
      type: 'app',
      system: false,
      action: 1,
      version: '2.0',
      usedVersionId: 11,
      latestVersion: 11,
      outdated: false,
      followLatest: true,
      ...patch,
    }) as TApplicationDTO;

  async function render(data: TApplicationDTO) {
    facade = jasmine.createSpyObj('ConfigurationAppsFacadeService', ['setFollowLatest']);
    TestBed.configureTestingModule({
      imports: [AppsFollowCell],
      providers: [
        ...baseTestProviders(),
        { provide: ConfigurationAppsFacadeService, useValue: facade },
        {
          provide: AuthService,
          useValue: { hasPermission: () => true, currentUser: signal(null) },
        },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
    const fixture = TestBed.createComponent(AppsFollowCell);
    fixture.componentRef.setInput('params', { data });
    fixture.detectChanges();
    return fixture;
  }

  it('shows Follows latest for a following row', async () => {
    const fixture = await render(app({ followLatest: true }));

    expect(fixture.nativeElement.textContent).toContain('Follows latest');
  });

  it('shows Pinned to the version for a pinned row', async () => {
    const fixture = await render(app({ followLatest: false, version: '1.0' }));

    expect(fixture.nativeElement.textContent).toContain('Pinned to 1.0');
  });

  it('turns following on through the facade when the toggle is switched on', async () => {
    const data = app({ followLatest: false });
    const fixture = await render(data);

    fixture.componentInstance.onFollowChange(true);

    expect(facade.setFollowLatest).toHaveBeenCalledWith(data, true);
  });

  it('has no toggle for rows that are not installed', async () => {
    const fixture = await render(app({ action: 0 }));

    expect(fixture.nativeElement.querySelector('mat-slide-toggle')).toBeNull();
  });

  it('has no toggle for system apps', async () => {
    const fixture = await render(app({ system: true }));

    expect(fixture.nativeElement.querySelector('mat-slide-toggle')).toBeNull();
  });
});
