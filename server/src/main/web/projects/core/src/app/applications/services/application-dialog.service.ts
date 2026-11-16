import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialog, InformDialog } from 'hmdm-ui-kit';
import {
  catchError,
  filter,
  finalize,
  map,
  Observable,
  of,
  switchMap,
  take,
  takeUntil,
} from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { releaseSummaryDialogData } from '../utils/release-summary';
import { SnackBarService } from '../../shared/services/snack-bar.service';
import { EApplicationType } from '../../entity/application/enum/application-type.enum';
import { ApplicationService } from '../../entity/application/services/application.service';
import { TApplicationDTO } from '../../entity/application/types/application-dto.type';
import { TApplicationFormEmitValue } from '../types/application-form.type';
import { ApplicationConfigDialog } from '../components/application-config-dialog/application-config-dialog';
import { ReleaseSummaryDialog } from '../components/release-summary-dialog/release-summary-dialog';
import { ApplicationDialog } from '../components/application-dialog/application-dialog';
import {
  DuplicatePkgDialog,
  TDuplicatePkgDialogResult,
} from '../components/duplicate-pkg-dialog/duplicate-pkg-dialog';
import { TReleaseMode, TReleaseSummary } from '../../entity/application/types/release-mode.type';
import { ApplicationFacadeService } from './application-facade.service';

type TSaveResult =
  | { outcome: 'saved'; entity: TApplicationDTO | null; heldAsFirstVersion: boolean }
  | { outcome: 'version-saved'; applicationId: number; summary: TReleaseSummary }
  | { outcome: 'name-taken' }
  | { outcome: 'cancelled' };

const saved = (entity: TApplicationDTO | null, heldAsFirstVersion = false): TSaveResult => ({
  outcome: 'saved',
  entity,
  heldAsFirstVersion,
});

const versionSaved = (
  applicationId: number,
  summary: TReleaseSummary,
  requestedMode?: TReleaseMode,
): TSaveResult => ({
  outcome: 'version-saved',
  applicationId,
  summary: { ...summary, requestedMode },
});

@Injectable({ providedIn: 'root' })
export class ApplicationDialogService {
  private readonly dialog: MatDialog = inject(MatDialog);
  private readonly applicationService = inject(ApplicationService);
  private readonly applicationFacadeService = inject(ApplicationFacadeService);
  private readonly snackBarService = inject(SnackBarService);
  private readonly translate = inject(TranslateService);

  openAddApplicationDialog(): void {
    const dialogRef = this.dialog.open(ApplicationDialog, { minWidth: '600px' });
    const instance = dialogRef.componentInstance;

    instance.saveTrigger$
      .pipe(
        takeUntil(dialogRef.beforeClosed()),
        switchMap((data) => {
          instance.isSaving.set(true);
          return this.resolveAndSave(data, true).pipe(
            catchError(() => of<TSaveResult>({ outcome: 'cancelled' })),
            finalize(() => instance.isSaving.set(false)),
          );
        }),
      )
      .subscribe((result) => {
        if (result.outcome === 'name-taken') {
          instance.nameError.set('error.app.name.exists');
          return;
        }

        if (result.outcome === 'version-saved') {
          dialogRef.close();
          this.applicationFacadeService.searchApplications();
          this.dialog
            .open(ReleaseSummaryDialog, {
              data: releaseSummaryDialogData(result.summary, this.translate),
            })
            .afterClosed()
            .pipe(take(1))
            .subscribe(() => this.openSelectConfigurationDialog(result.applicationId));
          return;
        }

        if (result.outcome !== 'saved' || !result.entity) return;

        const res = result.entity;

        dialogRef.close();
        this.applicationFacadeService.searchApplications();

        if (!this.applicationFacadeService.formData?.showSystem && res.system) {
          this.openSystemAppMessageDialog();
        }

        if (result.heldAsFirstVersion) {
          this.dialog
            .open(InformDialog, { data: { message: 'release.summary.first.version' } })
            .afterClosed()
            .pipe(take(1))
            .subscribe(() => this.openSelectConfigurationDialog(res.id, res.usedVersionId));
          return;
        }

        this.openSelectConfigurationDialog(res.id, res.usedVersionId);
      });
  }

  openEditApplicationDialog(application: TApplicationDTO): void {
    const dialogRef = this.dialog.open(ApplicationDialog, { minWidth: '600px', data: application });
    const instance = dialogRef.componentInstance;

    instance.saveTrigger$
      .pipe(
        takeUntil(dialogRef.beforeClosed()),
        switchMap((data) => {
          instance.isSaving.set(true);
          // The id decides insert vs update on the backend, so pin it here
          // rather than relying on the form to carry it through.
          return this.resolveAndSave({ ...data, id: application.id }, false).pipe(
            catchError(() => of<TSaveResult>({ outcome: 'cancelled' })),
            finalize(() => instance.isSaving.set(false)),
          );
        }),
      )
      .subscribe((result) => {
        if (result.outcome !== 'saved') return;

        dialogRef.close();
        this.applicationFacadeService.searchApplications();
      });
  }

  private resolveAndSave(
    data: TApplicationFormEmitValue,
    isNewApp: boolean,
  ): Observable<TSaveResult> {
    if (data.type !== EApplicationType.APP || !data.filePath) {
      return this.applicationService.createApplication(data).pipe(map((entity) => saved(entity)));
    }

    const appData = data;
    const validateBody = {
      arch: appData.arch || null,
      type: appData.type,
      pkg: appData.pkg,
      name: appData.name,
      version: appData.version,
      versionCode: appData.versionCode ?? 0,
      filePath: appData.filePath,
      runAtBoot: appData.runAtBoot,
      runAfterInstall: appData.runAfterInstall,
      showIcon: appData.showIcon,
      system: appData.system,
    };

    return this.applicationService.validatePkg(validateBody).pipe(
      switchMap((duplicates) => {
        if (!duplicates?.length) {
          return this.applicationService
            .createApplication(data)
            .pipe(map((entity) => saved(entity, appData.releaseMode === 'hold')));
        }

        // Single match, same name and a newer version → silently add a new version.
        // Otherwise (existing or older version, renamed app) ask the user.
        const existing = duplicates[0];
        const versionCode = appData.versionCode ?? 0;
        if (
          duplicates.length === 1 &&
          !appData.versionExists &&
          existing.versionCode <= versionCode &&
          existing.name === appData.name
        ) {
          return this.applicationService
            .createApplicationVersion({
              applicationId: duplicates[0].id,
              version: appData.version,
              arch: appData.arch || null,
              filePath: appData.filePath,
              versionCode: appData.versionCode,
              pkg: appData.pkg,
              name: appData.name,
              type: appData.type,
              runAtBoot: appData.runAtBoot,
              runAfterInstall: appData.runAfterInstall,
              showIcon: appData.showIcon,
              system: appData.system,
              useKiosk: appData.useKiosk,
              releaseMode: appData.releaseMode,
            })
            .pipe(map((summary) => versionSaved(duplicates[0].id, summary, appData.releaseMode)));
        }

        return this.dialog
          .open(DuplicatePkgDialog, {
            minWidth: '480px',
            data: {
              duplicateApps: duplicates,
              pkg: appData.pkg,
              appName: appData.name,
              isNewApp,
            },
          })
          .afterClosed()
          .pipe(
            switchMap((result: TDuplicatePkgDialogResult | null): Observable<TSaveResult> => {
              if (!result) return of({ outcome: 'cancelled' });

              if (result.action === 'name-taken') {
                return of({ outcome: 'name-taken' });
              }

              if (result.action === 'new-version') {
                return this.applicationService
                  .createApplicationVersion({
                    applicationId: result.applicationId,
                    version: appData.version,
                    arch: appData.arch || null,
                    filePath: appData.filePath,
                    versionCode: appData.versionCode,
                    pkg: appData.pkg,
                    name: appData.name,
                    type: appData.type,
                    runAtBoot: appData.runAtBoot,
                    runAfterInstall: appData.runAfterInstall,
                    showIcon: appData.showIcon,
                    system: appData.system,
                    useKiosk: appData.useKiosk,
                    releaseMode: appData.releaseMode,
                  })
                  .pipe(
                    map((summary) =>
                      versionSaved(result.applicationId, summary, appData.releaseMode),
                    ),
                  );
              }

              // 'new-app' or 'change-pkg' — proceed with normal application creation
              return this.applicationService
                .createApplication(data)
                .pipe(map((entity) => saved(entity, appData.releaseMode === 'hold')));
            }),
          );
      }),
    );
  }

  openSelectConfigurationDialog(applicationId: number, versionId?: number): void {
    this.dialog
      .open(ApplicationConfigDialog, { data: { applicationId, versionId } })
      .afterClosed()
      .pipe(take(1), filter(Boolean))
      .subscribe(() => {
        this.applicationFacadeService.searchApplications();
      });
  }

  openDeleteApplicationDialog(application: TApplicationDTO): void {
    this.dialog
      .open(ConfirmDialog, {
        data: {
          title: '',
          message: 'question.delete.application',
          confirmButtonText: 'button.delete',
          cancelButtonText: 'button.cancel',
          params: {
            applicationName: application.name,
          },
        },
      })
      .afterClosed()
      .pipe(
        take(1),
        filter(Boolean),
        switchMap(() => this.applicationService.deleteApplication(application.id)),
      )
      .subscribe(() => {
        this.applicationFacadeService.searchApplications();
      });
  }

  openSharedApplicationDialog(): void {
    this.dialog.open(InformDialog, {
      data: {
        message: 'alerts.application.shared',
      },
    });
  }

  openSystemAppMessageDialog(): void {
    this.dialog.open(InformDialog, {
      data: {
        message: 'alerts.system.apps.filter',
      },
    });
  }
}
