import { Component, computed, effect, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import {
  BaseComponent,
  Checkbox,
  Selector,
  SelectSearch,
  TextAreaInputComponent,
  TextInputComponent,
  TranslatePipe,
} from 'hmdm-ui-kit';
import { AuthService } from '../../../shared/services/auth.service';
import { ConfigurationMdmFormConfig } from '../../configs/configuration-mdm-form.config';
import { ConfigurationDetailsFacadeService } from '../../services/configuration-details-facade.service';
import { TConfigurationMDMForm } from '../../types/configuration-mdm-form.type';
import { WIFI_SECURITY_OPTIONS } from '../../const/wifi-security-options.const';
import { ConfigurationAppsFacadeService } from '../../services/configuration-applications-facade.service';

@Component({
  selector: 'core-configuration-mdm',
  templateUrl: './configuration-mdm.html',
  styleUrl: './configuration-mdm.scss',
  imports: [
    Checkbox,
    SelectSearch,
    TextInputComponent,
    TranslatePipe,
    Selector,
    TextAreaInputComponent,
    FormsModule,
    ReactiveFormsModule,
    MatSlideToggleModule,
  ],
})
export class ConfigurationMdm extends BaseComponent implements OnInit {
  private readonly configurationMdmFormConfig: ConfigurationMdmFormConfig = inject(
    ConfigurationMdmFormConfig,
  );
  private readonly configurationDetailsFacadeService: ConfigurationDetailsFacadeService = inject(
    ConfigurationDetailsFacadeService,
  );
  private readonly configurationApplicationsFacadeService: ConfigurationAppsFacadeService = inject(
    ConfigurationAppsFacadeService,
  );

  private readonly authService = inject(AuthService);

  private selectedMainAppId: number | null = null;
  private selectedContentAppId: number | null = null;

  canFollowLatest = this.authService.hasPermission('applications');
  wifiSecurityOptions = WIFI_SECURITY_OPTIONS;
  applicationOptions = this.configurationApplicationsFacadeService.applicationsVersionIdOptions;
  formGroup: FormGroup<TConfigurationMDMForm> = this.configurationMdmFormConfig.getFormGroup();
  isKioskMode: WritableSignal<boolean> = signal(false);

  followsMainApp = computed(() =>
    this.configurationApplicationsFacadeService.followsLatestMainApp(),
  );
  followsContentApp = computed(() =>
    this.configurationApplicationsFacadeService.followsLatestContentApp(),
  );

  constructor() {
    super();

    effect(() => {
      const currentConfig = this.configurationDetailsFacadeService.currentConfiguration();
      if (!currentConfig) {
        return;
      }

      this.formGroup.patchValue(
        {
          kioskMode: currentConfig?.kioskMode || false,
          mainAppId: currentConfig?.mainAppId || null,
          contentAppId: currentConfig?.contentAppId || null,
          eventReceivingComponent: currentConfig?.eventReceivingComponent || '',

          kioskHome: currentConfig?.kioskHome || false,
          kioskRecents: currentConfig?.kioskRecents || false,
          kioskNotifications: currentConfig?.kioskNotifications || false,
          kioskSystemInfo: currentConfig?.kioskSystemInfo || false,
          kioskKeyguard: currentConfig?.kioskKeyguard || false,
          kioskLockButtons: currentConfig?.kioskLockButtons || false,
          kioskExit: currentConfig?.kioskExit || false,
          kioskScreenOn: currentConfig?.kioskScreenOn || false,

          permissive: currentConfig.permissive || false,
          lockSafeSettings: currentConfig.lockSafeSettings || false,
          allowedClasses: currentConfig.allowedClasses || '',

          wifiSSID: currentConfig.wifiSSID || '',
          wifiPassword: currentConfig.wifiPassword || '',
          wifiSecurityType: currentConfig.wifiSecurityType || '',

          launcherUrl: currentConfig.launcherUrl || '',
          qrParameters: currentConfig.qrParameters || '',
          adminExtras: currentConfig.adminExtras || '',
          mobileEnrollment: currentConfig.mobileEnrollment || false,
          encryptDevice: currentConfig.encryptDevice || false,

          restrictions: currentConfig.restrictions || '',
          newServerUrl: currentConfig.newServerUrl || '',
        },
        { emitEvent: false },
      );

      this.selectedMainAppId = this.formGroup.controls.mainAppId.value;
      this.selectedContentAppId = this.formGroup.controls.contentAppId.value;
      this.isKioskMode.set(this.formGroup.controls.kioskMode.value);
    });
  }

  onMainFollowChange(follow: boolean): void {
    this.configurationApplicationsFacadeService.setMainAppFollowsLatest(follow);
  }

  onContentFollowChange(follow: boolean): void {
    this.configurationApplicationsFacadeService.setContentAppFollowsLatest(follow);
  }

  ngOnInit(): void {
    this.formGroup.valueChanges.pipe(this.untilDestroyed()).subscribe(() => {
      this.configurationDetailsFacadeService.setMDMSettings(this.formGroup.getRawValue());

      const newMainAppId = this.formGroup.controls.mainAppId.value;
      if (newMainAppId !== this.selectedMainAppId) {
        this.selectedMainAppId = newMainAppId;
        this.configurationApplicationsFacadeService.syncFollowLatestFlagWithSelection(
          'main',
          newMainAppId,
        );
      }

      const newContentAppId = this.formGroup.controls.contentAppId.value;
      if (newContentAppId !== this.selectedContentAppId) {
        this.selectedContentAppId = newContentAppId;
        this.configurationApplicationsFacadeService.syncFollowLatestFlagWithSelection(
          'content',
          newContentAppId,
        );
      }

      this.isKioskMode.set(this.formGroup.controls.kioskMode.value);
    });

    this.isKioskMode.set(this.formGroup.controls.kioskMode.value);
  }
}
