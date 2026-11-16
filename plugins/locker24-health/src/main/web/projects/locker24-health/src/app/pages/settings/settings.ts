import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  HelpTooltip,
  MatButtonModule,
  MatCardModule,
  MatFormFieldModule,
  MatIconModule,
  MatInputModule,
  SelectSearch,
  TOption,
  TranslatePipe,
} from 'hmdm-ui-kit';
import { finalize, take } from 'rxjs';
import { buildUpdate, isHttpsUrl, timeZoneOptions, TWebhookMode } from '../../logic/settings';
import { errorKey, HealthService } from '../../services/health.service';
import { THealthSettings } from '../../types/settings.type';

function httpsUrl(control: AbstractControl<string>): ValidationErrors | null {
  return !control.value || isHttpsUrl(control.value) ? null : { httpsUrl: true };
}

@Component({
  selector: 'l24-settings',
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    HelpTooltip,
    SelectSearch,
    TranslatePipe,
  ],
})
export class Settings implements OnInit {
  private readonly health = inject(HealthService);

  readonly settings = signal<THealthSettings | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);
  readonly webhookMode = signal<TWebhookMode>('keep');
  readonly showUrl = signal(false);

  readonly timeZoneControl = new FormControl<string | null>(null, Validators.required);
  readonly webhookControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [httpsUrl],
  });

  readonly editable = computed(() => !!this.settings()?.editable);
  readonly zoneOptions = signal<TOption<string>[]>([]);

  ngOnInit(): void {
    this.loading.set(true);
    this.health
      .fetchSettings()
      .pipe(
        take(1),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (settings) => this.apply(settings),
        error: (error: unknown) => this.error.set(errorKey(error)),
      });
  }

  startReplace(): void {
    this.webhookMode.set('replace');
    this.webhookControl.reset('');
    this.saved.set(false);
  }

  startClear(): void {
    this.webhookMode.set('clear');
    this.webhookControl.reset('');
    this.saved.set(false);
  }

  cancelWebhook(): void {
    this.webhookMode.set('keep');
    this.webhookControl.reset('');
  }

  canSave(): boolean {
    if (!this.editable() || this.saving() || this.timeZoneControl.invalid) {
      return false;
    }
    return this.webhookMode() !== 'replace' || isHttpsUrl(this.webhookControl.value);
  }

  save(): void {
    if (!this.canSave()) {
      this.webhookControl.markAsTouched();
      return;
    }
    const body = buildUpdate(
      this.timeZoneControl.value,
      this.webhookMode(),
      this.webhookControl.value,
    );
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    this.health
      .saveSettings(body)
      .pipe(
        take(1),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (settings) => {
          this.apply(settings);
          this.saved.set(true);
        },
        error: (error: unknown) => this.error.set(errorKey(error)),
      });
  }

  private apply(settings: THealthSettings): void {
    this.settings.set(settings);
    this.webhookMode.set('keep');
    this.webhookControl.reset('');
    this.showUrl.set(false);
    this.zoneOptions.set(
      timeZoneOptions(settings.timeZone).map((zone) => ({ value: zone, viewValue: zone })),
    );
    this.timeZoneControl.setValue(settings.timeZone);
    if (settings.editable) {
      this.timeZoneControl.enable();
    } else {
      this.timeZoneControl.disable();
    }
  }
}
