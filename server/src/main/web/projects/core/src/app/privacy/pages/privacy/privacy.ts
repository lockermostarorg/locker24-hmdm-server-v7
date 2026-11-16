import { Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RebrandingService, TranslatePipe } from 'hmdm-ui-kit';

@Component({
  selector: 'core-privacy',
  templateUrl: './privacy.html',
  styleUrl: './privacy.scss',
  imports: [MatButton, MatIconModule, TranslatePipe],
})
export class Privacy {
  private readonly rebrandingService = inject(RebrandingService);
  private readonly location = inject(Location);

  readonly rebranding = this.rebrandingService.rebranding;
  readonly currentYear = new Date().getFullYear();
  readonly wordmarkUrl = 'images/logo-wordmark.svg';

  onBack(): void {
    this.location.back();
  }
}
