import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { InformDialog } from 'hmdm-ui-kit';
import { NEVER, of, Subject } from 'rxjs';
import { TApplicationDTO } from '../../entity/application/types/application-dto.type';
import { ApplicationService } from '../../entity/application/services/application.service';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders, useEnglish } from '../../testing/test-providers';
import { ApplicationConfigDialog } from '../components/application-config-dialog/application-config-dialog';
import { ApplicationDialog } from '../components/application-dialog/application-dialog';
import { DuplicatePkgDialog } from '../components/duplicate-pkg-dialog/duplicate-pkg-dialog';
import { ApplicationDialogService } from './application-dialog.service';
import { ApplicationFacadeService } from './application-facade.service';

describe('ApplicationDialogService add application', () => {
  let service: ApplicationDialogService;
  let applicationService: jasmine.SpyObj<ApplicationService>;
  let saveTrigger$: Subject<unknown>;
  let opened: { component: unknown; config: { data?: Record<string, unknown> } }[];
  let duplicateAnswer: unknown;

  const appValue = (releaseMode: string) => ({
    type: 'app',
    name: 'N',
    pkg: 'p.q',
    version: '2.0',
    versionCode: 2,
    arch: '',
    filePath: '/tmp/x.apk',
    releaseMode,
    showIcon: true,
  });

  beforeEach(async () => {
    saveTrigger$ = new Subject();
    opened = [];
    duplicateAnswer = null;
    applicationService = jasmine.createSpyObj('ApplicationService', [
      'validatePkg',
      'createApplication',
      'createApplicationVersion',
    ]);
    const dialog = {
      open: (component: unknown, config: { data?: Record<string, unknown> } = {}) => {
        opened.push({ component, config });
        if (component === ApplicationDialog) {
          return {
            componentInstance: {
              saveTrigger$,
              isSaving: signal(false),
              nameError: signal<string | null>(null),
            },
            beforeClosed: () => NEVER,
            close: () => undefined,
          };
        }
        return { afterClosed: () => of(component === DuplicatePkgDialog ? duplicateAnswer : null) };
      },
    };

    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        ApplicationDialogService,
        { provide: MatDialog, useValue: dialog },
        { provide: ApplicationService, useValue: applicationService },
        {
          provide: ApplicationFacadeService,
          useValue: { searchApplications: jasmine.createSpy('search'), formData: null },
        },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
    service = TestBed.inject(ApplicationDialogService);
    service.openAddApplicationDialog();
  });

  const informs = () => opened.filter((o) => o.component === InformDialog);
  const configDialogs = () => opened.filter((o) => o.component === ApplicationConfigDialog);

  it('uploads a new version of a known app with the chosen mode and shows the summary', () => {
    applicationService.validatePkg.and.returnValue(
      of([{ id: 9, name: 'N', versionCode: 1 }] as unknown as TApplicationDTO[]),
    );
    applicationService.createApplicationVersion.and.returnValue(
      of({ configurations: 2, devices: 5, mode: 'immediate' }),
    );

    saveTrigger$.next(appValue('immediate'));

    const request = applicationService.createApplicationVersion.calls.mostRecent().args[0];
    expect(request.applicationId).toBe(9);
    expect(request.releaseMode).toBe('immediate');
    expect('autoUpdateDisplayed' in request).toBeFalse();
    expect(informs().length).toBe(1);
    expect(informs()[0].config.data?.['message']).toBe('release.summary.released');
    expect(informs()[0].config.data?.['params']).toEqual({
      configurations: '2 configurations',
      devices: '5 tablets',
    });
    expect(configDialogs()[0].config.data).toEqual({ applicationId: 9, versionId: undefined });
  });

  it('says a held version was not applied', () => {
    applicationService.validatePkg.and.returnValue(
      of([{ id: 9, name: 'N', versionCode: 1 }] as unknown as TApplicationDTO[]),
    );
    applicationService.createApplicationVersion.and.returnValue(
      of({ configurations: 0, devices: 0, mode: 'hold' }),
    );

    saveTrigger$.next(appValue('hold'));

    expect(informs()[0].config.data?.['message']).toBe('release.summary.held');
  });

  it('explains that hold became a scheduled release when a new app is created', () => {
    applicationService.validatePkg.and.returnValue(
      of([{ id: 9, name: 'Other', versionCode: 5 }] as unknown as TApplicationDTO[]),
    );
    duplicateAnswer = { action: 'new-app' };
    applicationService.createApplication.and.returnValue(
      of({ id: 20, usedVersionId: 30, system: false } as unknown as TApplicationDTO),
    );

    saveTrigger$.next(appValue('hold'));

    expect(applicationService.createApplicationVersion).not.toHaveBeenCalled();
    expect(informs()[0].config.data?.['message']).toBe('release.summary.first.version');
    expect(configDialogs()[0].config.data).toEqual({ applicationId: 20, versionId: 30 });
  });

  it('shows no first-version note when the mode was not hold', () => {
    applicationService.validatePkg.and.returnValue(of([]));
    applicationService.createApplication.and.returnValue(
      of({ id: 20, usedVersionId: 30, system: false } as unknown as TApplicationDTO),
    );

    saveTrigger$.next(appValue('schedule'));

    expect(informs().length).toBe(0);
    expect(configDialogs().length).toBe(1);
  });
});
