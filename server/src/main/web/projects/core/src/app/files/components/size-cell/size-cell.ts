import { Component, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BaseCellRenderer } from 'hmdm-ui-kit';

@Component({
  selector: 'core-size-cell',
  templateUrl: './size-cell.html',
  styleUrl: './size-cell.scss',
  imports: [],
})
/**
 * Renders a file size in bytes as a human-readable value (B, KB, MB, GB).
 * cellRendererParams.showZero(row) - optional, returns true to display '0' instead of an empty cell for zero size.
 */
export class SizeCell extends BaseCellRenderer {
  private readonly translate = inject(TranslateService);

  getValue(): string {
    const size = this.params().value;

    if (typeof size !== 'number' || Number.isNaN(size)) {
      return '';
    }

    if (size === -1) {
      return this.translate.instant('form.file.deleted');
    }

    if (size === 0) {
      return this.params().provided?.['showZero']?.(this.params().data) ? '0' : '';
    }

    if (size < 1024) {
      return `${size} B`;
    } else if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(2)} KB`;
    } else if (size < 1024 * 1024 * 1024) {
      return `${(size / (1024 * 1024)).toFixed(2)} MB`;
    } else {
      return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }
  }
}
