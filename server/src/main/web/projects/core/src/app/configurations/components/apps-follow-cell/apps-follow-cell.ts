import { Component, computed, inject, Signal } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BaseCellRenderer, TranslatePipe } from 'hmdm-ui-kit';
import { TApplicationDTO } from '../../../entity/application/types/application-dto.type';
import { AuthService } from '../../../shared/services/auth.service';
import { APP_ACTION } from '../../const/app-action.const';
import { ConfigurationAppsFacadeService } from '../../services/configuration-applications-facade.service';

@Component({
  selector: 'core-apps-follow-cell',
  templateUrl: './apps-follow-cell.html',
  styleUrl: './apps-follow-cell.scss',
  imports: [MatSlideToggleModule, MatTooltipModule, TranslatePipe],
})
export class AppsFollowCell extends BaseCellRenderer<TApplicationDTO> {
  private readonly configurationAppsFacadeService = inject(ConfigurationAppsFacadeService);
  private readonly authService = inject(AuthService);

  follows: Signal<boolean> = computed(() => this.params().data?.followLatest !== false);

  // Same rule as choosing a version: installed apps of type "app", with the "applications" permission
  canFollow: Signal<boolean> = computed(() => {
    const app = this.params().data;
    return (
      !!app &&
      !app.system &&
      app.type === 'app' &&
      app.action === APP_ACTION.ALLOW &&
      this.authService.hasPermission('applications')
    );
  });

  onFollowChange(follow: boolean): void {
    this.configurationAppsFacadeService.setFollowLatest(this.params().data, follow);
  }
}
