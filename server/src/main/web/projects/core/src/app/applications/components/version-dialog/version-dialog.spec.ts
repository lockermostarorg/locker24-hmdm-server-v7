import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { MAT_DIALOG_DATA } from 'hmdm-ui-kit';
import { ApplicationService } from '../../../entity/application/services/application.service';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';
import { TReleaseSummary } from '../../../entity/application/types/release-mode.type';
import { baseTestProviders } from '../../../testing/test-providers';
import { VersionDialog } from './version-dialog';

describe('VersionDialog', () => {
  let fixture: ComponentFixture<VersionDialog>;
  let applicationService: jasmine.SpyObj<ApplicationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<VersionDialog>>;

  const summary: TReleaseSummary = { configurations: 2, devices: 5, mode: 'schedule' };

  function setup(data: Record<string, unknown>): void {
    applicationService = jasmine.createSpyObj('ApplicationService', [
      'createApplicationVersion',
      'updateApplicationVersion',
      'uploadFile',
    ]);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    TestBed.configureTestingModule({
      imports: [VersionDialog],
      providers: [
        ...baseTestProviders(),
        { provide: ApplicationService, useValue: applicationService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });

    fixture = TestBed.createComponent(VersionDialog);
    fixture.detectChanges();
  }

  function fillUrl(): void {
    fixture.componentInstance.versionForm.formGroup.patchValue({
      version: '1.2.3',
      url: 'https://example.com/app.apk',
    });
  }

  it('sends the default mode schedule with a new version', () => {
    setup({ applicationId: 7 });
    applicationService.createApplicationVersion.and.returnValue(of(summary));
    fillUrl();

    fixture.componentInstance.onSave();

    const request = applicationService.createApplicationVersion.calls.mostRecent().args[0];
    expect(request.releaseMode).toBe('schedule');
    expect(request.applicationId).toBe(7);
    expect('autoUpdate' in request).toBeFalse();
    expect('autoUpdateDisplayed' in request).toBeFalse();
  });

  it('sends the chosen mode and closes with the summary', () => {
    setup({ applicationId: 7 });
    applicationService.createApplicationVersion.and.returnValue(of(summary));
    fillUrl();
    fixture.componentInstance.versionForm.formGroup.controls.releaseMode.setValue('immediate');

    fixture.componentInstance.onSave();

    expect(applicationService.createApplicationVersion.calls.mostRecent().args[0].releaseMode).toBe(
      'immediate',
    );
    expect(dialogRef.close).toHaveBeenCalledWith(summary);
  });

  it('stays open and stops saving when the server answers with an error', () => {
    setup({ applicationId: 7 });
    applicationService.createApplicationVersion.and.returnValue(
      throwError(() => new Error('error.concurrent.update')),
    );
    fillUrl();

    fixture.componentInstance.onSave();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.saving()).toBeFalse();
  });

  it('does not send a release mode when editing an existing version', () => {
    setup({ applicationId: 7, version: { id: 3, applicationId: 7 } });
    applicationService.updateApplicationVersion.and.returnValue(of({} as TVersionDTO));
    fillUrl();

    fixture.componentInstance.onSave();

    const request = applicationService.updateApplicationVersion.calls.mostRecent().args[0];
    expect('releaseMode' in request).toBeFalse();
  });

  it('hides hold when the application has no released version', () => {
    setup({ applicationId: 7, hasReleasedVersion: false });

    expect(fixture.componentInstance.allowHold).toBeFalse();
  });
});
