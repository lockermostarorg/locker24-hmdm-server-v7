import { Component, computed, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BaseCellRenderer } from 'hmdm-ui-kit';
import { TDevice } from '../../types/device.type';

@Component({
  selector: 'core-configuration-cell',
  templateUrl: './configuration-cell.html',
  styleUrl: './configuration-cell.scss',
  imports: [RouterLink],
})
export class ConfigurationCell extends BaseCellRenderer<TDevice, null> {
  configurationName: Signal<string> = computed(
    () => this.params().data?.configuration?.name ?? '',
  );

  configurationId: Signal<number | undefined> = computed(
    () => this.params().data?.configuration?.id,
  );
}
