import { Component, inject, signal } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import {
  DialogBase,
  DialogCommonButtons,
  DialogTemplate,
  MAT_DIALOG_DATA,
  TranslatePipe,
} from 'hmdm-ui-kit';
import { ApplicationService } from '../../../entity/application/services/application.service';
import { TReleaseMode } from '../../../entity/application/types/release-mode.type';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';
import { ReleaseModeField } from '../release-mode-field/release-mode-field';

@Component({
  selector: 'core-release-version-dialog',
  templateUrl: './release-version-dialog.html',
  styleUrl: './release-version-dialog.scss',
  imports: [DialogTemplate, DialogCommonButtons, ReleaseModeField, TranslatePipe],
})
export class ReleaseVersionDialog extends DialogBase {
  private readonly applicationService = inject(ApplicationService);

  private readonly data = inject(MAT_DIALOG_DATA);
  readonly version: TVersionDTO = this.data.version;
  readonly notify: boolean = !!this.data.notify;
  readonly modeControl = new FormControl<TReleaseMode>('schedule', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly saving = signal(false);

  override onSave(): void {
    if (this.saving()) {
      return;
    }

    this.saving.set(true);
    const mode = this.modeControl.value === 'immediate' ? 'immediate' : 'schedule';
    const call = this.notify
      ? this.applicationService.notifyApplicationVersion(this.version.id, mode)
      : this.applicationService.releaseApplicationVersion(this.version.id, mode);

    call.subscribe({
      next: (summary) => this.dialogRef.close(summary),
      error: () => this.saving.set(false),
    });
  }
}
