import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { MAT_DIALOG_DATA } from 'hmdm-ui-kit';
import { of, throwError } from 'rxjs';
import { ApplicationService } from '../../../entity/application/services/application.service';
import { baseTestProviders } from '../../../testing/test-providers';
import { ReleaseVersionDialog } from './release-version-dialog';

describe('ReleaseVersionDialog', () => {
  let fixture: ComponentFixture<ReleaseVersionDialog>;
  let applicationService: jasmine.SpyObj<ApplicationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ReleaseVersionDialog>>;

  function setup(data: Record<string, unknown> = {}): void {
    applicationService = jasmine.createSpyObj('ApplicationService', [
      'releaseApplicationVersion',
      'notifyApplicationVersion',
    ]);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    TestBed.configureTestingModule({
      imports: [ReleaseVersionDialog],
      providers: [
        ...baseTestProviders(),
        { provide: ApplicationService, useValue: applicationService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { version: { id: 12, version: '2.0' }, ...data } },
      ],
    });
    fixture = TestBed.createComponent(ReleaseVersionDialog);
    fixture.detectChanges();
  }

  beforeEach(() => setup());

  it('offers schedule and immediately, not hold, with schedule preselected', () => {
    expect(fixture.componentInstance.modeControl.value).toBe('schedule');
    expect(fixture.nativeElement.querySelectorAll('mat-button-toggle').length).toBe(2);
  });

  it('posts the schedule mode by default and closes with the summary', () => {
    const summary = { configurations: 1, devices: 3, mode: 'schedule' as const };
    applicationService.releaseApplicationVersion.and.returnValue(of(summary));

    fixture.componentInstance.onSave();

    expect(applicationService.releaseApplicationVersion).toHaveBeenCalledWith(12, 'schedule');
    expect(dialogRef.close).toHaveBeenCalledWith(summary);
  });

  it('posts immediate when chosen', () => {
    applicationService.releaseApplicationVersion.and.returnValue(
      of({ configurations: 1, devices: 3, mode: 'immediate' as const }),
    );
    fixture.componentInstance.modeControl.setValue('immediate');

    fixture.componentInstance.onSave();

    expect(applicationService.releaseApplicationVersion).toHaveBeenCalledWith(12, 'immediate');
  });

  it('stays open when the release is refused', () => {
    applicationService.releaseApplicationVersion.and.returnValue(
      throwError(() => new Error('error.version.already.released')),
    );

    fixture.componentInstance.onSave();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.saving()).toBeFalse();
  });

  it('tells the user that canaries pinned to the version follow the latest again', () => {
    expect(fixture.nativeElement.textContent).toContain('form.application.release.canary.note');
  });

  describe('notify mode', () => {
    beforeEach(() => {
      TestBed.resetTestingModule();
      setup({ notify: true });
    });

    it('posts to notify, not release, with the chosen mode', () => {
      const summary = { configurations: 2, devices: 4, mode: 'immediate' as const };
      applicationService.notifyApplicationVersion.and.returnValue(of(summary));
      fixture.componentInstance.modeControl.setValue('immediate');

      fixture.componentInstance.onSave();

      expect(applicationService.notifyApplicationVersion).toHaveBeenCalledWith(12, 'immediate');
      expect(applicationService.releaseApplicationVersion).not.toHaveBeenCalled();
      expect(dialogRef.close).toHaveBeenCalledWith(summary);
    });

    it('shows the notify help and not the canary note', () => {
      const text = fixture.nativeElement.textContent;

      expect(text).toContain('form.application.notify.help');
      expect(text).not.toContain('form.application.release.canary.note');
    });
  });
});
