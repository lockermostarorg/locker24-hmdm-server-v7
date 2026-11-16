import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, Signal } from '@angular/core';
import { BaseCellRenderer, TranslatePipe } from 'hmdm-ui-kit';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';
import { ReleaseUserNamesService } from '../../services/release-user-names.service';

@Component({
  selector: 'core-version-state-cell',
  templateUrl: './version-state-cell.html',
  styleUrl: './version-state-cell.scss',
  imports: [DatePipe, TranslatePipe],
})
export class VersionStateCell extends BaseCellRenderer<TVersionDTO> implements OnInit {
  private readonly userNames = inject(ReleaseUserNamesService);

  isHeld: Signal<boolean> = computed(() => this.params().data.releaseState === 'held');

  releasedBy: Signal<string | null> = computed(() => {
    const userId = this.params().data.releasedBy;
    if (userId == null) {
      return null;
    }

    return this.userNames.nameOf(userId) ?? `#${userId}`;
  });

  ngOnInit(): void {
    this.userNames.load();
  }
}
