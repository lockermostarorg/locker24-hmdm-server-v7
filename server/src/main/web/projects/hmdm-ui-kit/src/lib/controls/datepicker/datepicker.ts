import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { BaseControlValueAccessor } from '../../shared/base/control-value-accessor';
import { ErrorMessage } from '../error-message/error-message';
import { HelpTooltip } from '../help-tooltip/help-tooltip.component';
import { DATE_INPUT_HINT, provideHmdmDateAdapter } from '../../shared/date/date-format';

@Component({
  selector: 'hmdm-datepicker',
  templateUrl: './datepicker.html',
  styleUrl: './datepicker.scss',
  providers: [provideHmdmDateAdapter()],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatSuffix,
    MatInputModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    HelpTooltip,
    ErrorMessage,
  ],
})
export class Datepicker extends BaseControlValueAccessor<string> {
  readonly dateInputHint = DATE_INPUT_HINT;

  onClear(): void {
    this.formControl.reset();
  }
}
