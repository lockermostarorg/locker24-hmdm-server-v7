import { DatePipe } from '@angular/common';
import { Component, computed, Signal } from '@angular/core';
import { BaseCellRenderer, DATE_TIME_FORMAT, TranslatePipe } from 'hmdm-ui-kit';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';

@Component({
  selector: 'core-version-state-cell',
  templateUrl: './version-state-cell.html',
  styleUrl: './version-state-cell.scss',
  imports: [DatePipe, TranslatePipe],
})
export class VersionStateCell extends BaseCellRenderer<TVersionDTO> {
  readonly dateTimeFormat = DATE_TIME_FORMAT;

  isHeld: Signal<boolean> = computed(() => this.params().data.releaseState === 'held');

  releasedBy: Signal<string | null> = computed(() => {
    const { releasedByName, releasedBy } = this.params().data;

    return releasedByName || (releasedBy == null ? null : `#${releasedBy}`);
  });
}
