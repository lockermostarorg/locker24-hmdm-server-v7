import { Component } from '@angular/core';
import { DialogBase, DialogTemplate, MatButtonModule, RebrandingService, TranslatePipe } from 'hmdm-ui-kit';
import { environment } from '../../../../environments/environment';
import { MatDivider } from '@angular/material/divider';
import { inject } from '@angular/core';

@Component({
  selector: 'core-about-dialog',
  templateUrl: './about-dialog.html',
  styleUrl: './about-dialog.scss',
  imports: [DialogTemplate, TranslatePipe, MatButtonModule, MatDivider],
})
export class AboutDialog extends DialogBase {
  plugins = (window as any)['__DYNPLUGINS__']
  readonly rebranding = inject(RebrandingService).rebranding;
  version = environment.version;

  override onSave(): void {}
}
