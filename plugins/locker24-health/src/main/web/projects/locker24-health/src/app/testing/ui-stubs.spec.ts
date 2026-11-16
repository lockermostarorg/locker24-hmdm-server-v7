import { Component, forwardRef, NO_ERRORS_SCHEMA, Pipe, PipeTransform, Type } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { RouterLink } from '@angular/router';
import { StatusBadge } from '../components/status-badge/status-badge';

@Pipe({ name: 'translate' })
export class TranslateStub implements PipeTransform {
  transform(key: string, params?: Record<string, unknown>): string {
    return params ? `${key} ${JSON.stringify(params)}` : key;
  }
}

@Component({
  selector: 'mat-select, hmdm-select-search',
  template: '<ng-content />',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ValueAccessorStub), multi: true },
  ],
})
export class ValueAccessorStub implements ControlValueAccessor {
  value: unknown = null;
  writeValue(value: unknown): void {
    this.value = value;
  }
  registerOnChange(): void {}
  registerOnTouched(): void {}
}

export function shallow(component: Type<unknown>): void {
  TestBed.overrideComponent(StatusBadge, {
    set: { imports: [TranslateStub], schemas: [NO_ERRORS_SCHEMA] },
  });
  TestBed.overrideComponent(component, {
    set: {
      imports: [ReactiveFormsModule, RouterLink, TranslateStub, ValueAccessorStub, StatusBadge],
      schemas: [NO_ERRORS_SCHEMA],
    },
  });
}
