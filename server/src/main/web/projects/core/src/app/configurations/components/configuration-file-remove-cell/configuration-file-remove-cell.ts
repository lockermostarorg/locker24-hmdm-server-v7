import { Component, effect, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { BaseCellRenderer, Checkbox } from 'hmdm-ui-kit';
import { ConfigurationDetailsFacadeService } from '../../services/configuration-details-facade.service';

@Component({
  selector: 'core-configuration-file-remove-cell',
  templateUrl: './configuration-file-remove-cell.html',
  styleUrl: './configuration-file-remove-cell.scss',
  imports: [Checkbox, ReactiveFormsModule],
})
export class ConfigurationFileRemoveCell extends BaseCellRenderer {
  private configurationDetailsFacadeService = inject(ConfigurationDetailsFacadeService);

  removeForm: FormControl<boolean> = new FormControl(false, { nonNullable: true });

  private readonly syncControlValue = effect(() => {
    this.removeForm.setValue(!!this.params().data.remove, { emitEvent: false });
  });

  constructor() {
    super();

    this.removeForm.valueChanges.pipe(this.untilDestroyed()).subscribe((value) => {
      this.configurationDetailsFacadeService.removeFileChange(this.params().data.id, value);
    });
  }
}
