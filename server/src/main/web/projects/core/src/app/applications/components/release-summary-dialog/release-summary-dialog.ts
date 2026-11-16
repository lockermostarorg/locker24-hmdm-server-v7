import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DialogTemplate, TranslatePipe } from 'hmdm-ui-kit';
import { TReleaseSummaryDialogData } from '../../utils/release-summary';

@Component({
  selector: 'core-release-summary-dialog',
  templateUrl: './release-summary-dialog.html',
  styleUrl: './release-summary-dialog.scss',
  imports: [DialogTemplate, MatButtonModule, TranslatePipe],
})
export class ReleaseSummaryDialog {
  private readonly dialogRef = inject(MatDialogRef<ReleaseSummaryDialog>);
  readonly data: TReleaseSummaryDialogData = inject(MAT_DIALOG_DATA);

  onOk(): void {
    this.dialogRef.close();
  }
}
