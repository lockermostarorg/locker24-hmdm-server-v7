import { Component, computed, effect, input, InputSignal, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Radio, TranslatePipe } from 'hmdm-ui-kit';
import { Subscription } from 'rxjs';
import { TReleaseMode } from '../../../entity/application/types/release-mode.type';
import { RELEASE_MODE_OPTIONS } from '../../const/release-mode-options.const';

@Component({
  selector: 'core-release-mode-field',
  templateUrl: './release-mode-field.html',
  styleUrl: './release-mode-field.scss',
  imports: [ReactiveFormsModule, Radio, TranslatePipe],
})
export class ReleaseModeField {
  control: InputSignal<FormControl<TReleaseMode>> = input.required<FormControl<TReleaseMode>>();
  allowHold: InputSignal<boolean> = input(true);

  options = computed(() =>
    RELEASE_MODE_OPTIONS.filter((option) => this.allowHold() || option.value !== 'hold'),
  );

  private readonly mode = signal<TReleaseMode>('schedule');
  private subscription: Subscription | null = null;

  helpKey = computed(() => `form.application.release.mode.${this.mode()}.help`);

  constructor() {
    effect((onCleanup) => {
      const control = this.control();
      this.mode.set(control.value);
      this.subscription = control.valueChanges.subscribe((value) => this.mode.set(value));
      onCleanup(() => this.subscription?.unsubscribe());
    });

    effect(() => {
      if (!this.allowHold() && this.control().value === 'hold') {
        this.control().setValue('schedule');
      }
    });
  }
}
