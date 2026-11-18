import { DatePipe } from '@angular/common';
import { Component, computed, Signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { DATE_TIME_FORMAT } from '../../../shared/date/date-format';
import { BaseCellRenderer } from '../../base/base-cell-renderer';

@Component({
  selector: 'hmdm-datetime-cell',
  templateUrl: './datetime-cell.html',
  styleUrl: './datetime-cell.css',
  imports: [DatePipe, TranslatePipe],
})
export class DatetimeCell extends BaseCellRenderer {
  date: Signal<Date | null> = computed(() => {
    const value = this.params().value;

    return typeof value === 'number' ? new Date(value) : null;
  });

  get timeFormat(): string {
    return this.params().provided.timeFormat || DATE_TIME_FORMAT;
  }
}
