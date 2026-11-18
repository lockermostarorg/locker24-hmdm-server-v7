import { Component, effect, inject } from '@angular/core';
import { BaseCellRenderer, TextInputComponent } from 'hmdm-ui-kit';
import { TApplicationDTO } from '../../../entity/application/types/application-dto.type';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { APP_ACTION } from '../../const/app-action.const';
import { ConfigurationAppsFacadeService } from '../../services/configuration-applications-facade.service';

function toScreenOrder(value: string | null): number | null {
  const parsed = Number(value);

  return !value || Number.isNaN(parsed) ? null : parsed;
}

@Component({
  selector: 'core-apps-order-cell',
  templateUrl: './apps-order-cell.html',
  styleUrl: './apps-order-cell.scss',
  imports: [TextInputComponent, ReactiveFormsModule],
})
export class AppsOrderCell extends BaseCellRenderer<TApplicationDTO> {
  private readonly configurationAppsFacadeService = inject(ConfigurationAppsFacadeService);

  formControl: FormControl<string | null> = new FormControl(null);

  private readonly syncControlValue = effect(() => {
    this.formControl.setValue(this.params().data.screenOrder?.toString() ?? null, {
      emitEvent: false,
    });
  });

  constructor() {
    super();

    this.formControl.valueChanges.pipe(this.untilDestroyed()).subscribe((value) => {
      this.configurationAppsFacadeService.setAppOrder(this.params().data, toScreenOrder(value));
    });
  }

  isDisplayed(): boolean {
    const app = this.params().data;

    return app.action === APP_ACTION.ALLOW && app.showIcon;
  }
}
