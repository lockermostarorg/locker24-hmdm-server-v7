import { Component, computed, input } from '@angular/core';
import { MatIconModule, TranslatePipe } from 'hmdm-ui-kit';
import { STATUS_ICON, TDisplayStatus } from '../../logic/status';

@Component({
  selector: 'l24-status-badge',
  templateUrl: './status-badge.html',
  styleUrl: './status-badge.scss',
  imports: [MatIconModule, TranslatePipe],
})
export class StatusBadge {
  status = input.required<TDisplayStatus>();
  count = input<number | null>(null);

  icon = computed(() => STATUS_ICON[this.status()]);
}
