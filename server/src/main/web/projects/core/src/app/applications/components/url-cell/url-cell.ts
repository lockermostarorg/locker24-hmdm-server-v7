import { Component, computed, Signal } from '@angular/core';
import { BaseCellRenderer } from 'hmdm-ui-kit';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';
import { TApplicationDTO } from '../../../entity/application/types/application-dto.type';

@Component({
  selector: 'core-url-cell',
  templateUrl: './url-cell.html',
  styleUrl: './url-cell.scss',
  imports: [],
})
export class UrlCell extends BaseCellRenderer<TVersionDTO | TApplicationDTO> {
  urlArm64: Signal<string | null> = computed(() => this.params().data.urlArm64 || null);
  urlArmeabi: Signal<string | null> = computed(() => this.params().data.urlArmeabi || null);
  url: Signal<string | null> = computed(() => this.params().data.url || null);

  isSplitApk(): boolean {
    return this.params().data.split;
  }
}
