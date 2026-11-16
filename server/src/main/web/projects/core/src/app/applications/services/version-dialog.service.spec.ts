import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { InformDialog } from 'hmdm-ui-kit';
import { baseTestProviders, useEnglish } from '../../testing/test-providers';
import { of } from 'rxjs';
import { ApplicationService } from '../../entity/application/services/application.service';
import { ApplicationFacadeService } from './application-facade.service';
import { VersionDialogService } from './version-dialog.service';
import { VersionFacadeService } from './version-facade.service';

describe('VersionDialogService summary', () => {
  let service: VersionDialogService;
  let dialog: MatDialog;
  let translate: TranslateService;

  let versionFacade: { versions: () => never[]; initLast: jasmine.Spy };
  let applicationFacade: { searchApplications: jasmine.Spy };
  let applicationService: jasmine.SpyObj<ApplicationService>;

  beforeEach(async () => {
    versionFacade = { versions: () => [], initLast: jasmine.createSpy('initLast') };
    applicationFacade = { searchApplications: jasmine.createSpy('searchApplications') };
    applicationService = jasmine.createSpyObj('ApplicationService', ['releaseApplicationVersion']);
    TestBed.configureTestingModule({
      imports: [MatDialogModule],
      providers: [
        ...baseTestProviders(),
        VersionDialogService,
        { provide: VersionFacadeService, useValue: versionFacade },
        { provide: ApplicationFacadeService, useValue: applicationFacade },
        { provide: ApplicationService, useValue: applicationService },
      ],
    });
    service = TestBed.inject(VersionDialogService);
    dialog = TestBed.inject(MatDialog);
    translate = TestBed.inject(TranslateService);
    await useEnglish(translate);
    spyOn(dialog, 'open').and.callThrough();
  });

  afterEach(() => dialog.closeAll());

  function shown(): string {
    const data = (dialog.open as jasmine.Spy).calls.mostRecent().args[1].data;
    return translate.instant(data.message, data.params);
  }

  it('says how many configurations and tablets a release reached', () => {
    service.showSummary({ configurations: 2, devices: 5, mode: 'schedule' });

    expect((dialog.open as jasmine.Spy).calls.mostRecent().args[0]).toBe(InformDialog);
    expect(shown()).toBe('Released to 2 configurations (5 tablets)');
  });

  it('says a held version was not applied', () => {
    service.showSummary({ configurations: 0, devices: 0, mode: 'hold' });

    expect(shown()).toBe('Held; not applied');
  });

  it('uses singular wording for one configuration and one tablet', () => {
    service.showSummary({ configurations: 1, devices: 1, mode: 'immediate' });

    expect(shown()).toBe('Released to 1 configuration (1 tablet)');
  });

  it('refreshes the versions and applications lists after a release', () => {
    const summary = { configurations: 2, devices: 5, mode: 'schedule' as const };
    applicationService.releaseApplicationVersion.and.returnValue(of(summary));
    (dialog.open as jasmine.Spy).and.returnValue({ afterClosed: () => of(summary) });

    service.openReleaseVersionDialog({ id: 3, version: '2' } as never);

    expect(versionFacade.initLast).toHaveBeenCalled();
    expect(applicationFacade.searchApplications).toHaveBeenCalled();
  });

  it('refreshes both lists after an upload', () => {
    const summary = { configurations: 0, devices: 0, mode: 'hold' as const };
    (dialog.open as jasmine.Spy).and.returnValue({ afterClosed: () => of(summary) });

    service.openAddVersionDialog(7);

    expect(versionFacade.initLast).toHaveBeenCalled();
    expect(applicationFacade.searchApplications).toHaveBeenCalled();
  });
});
