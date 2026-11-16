import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { InformDialog } from 'hmdm-ui-kit';
import { baseTestProviders, useEnglish } from '../../testing/test-providers';
import { ApplicationFacadeService } from './application-facade.service';
import { VersionDialogService } from './version-dialog.service';
import { VersionFacadeService } from './version-facade.service';

describe('VersionDialogService summary', () => {
  let service: VersionDialogService;
  let dialog: MatDialog;
  let translate: TranslateService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [MatDialogModule],
      providers: [
        ...baseTestProviders(),
        VersionDialogService,
        { provide: VersionFacadeService, useValue: { versions: () => [], initLast: () => {} } },
        { provide: ApplicationFacadeService, useValue: { searchApplications: () => {} } },
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
});
