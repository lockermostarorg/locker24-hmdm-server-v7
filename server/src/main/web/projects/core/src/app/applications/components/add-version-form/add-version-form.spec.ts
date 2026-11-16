import { ComponentFixture, TestBed } from '@angular/core/testing';
import { baseTestProviders } from '../../../testing/test-providers';
import { AddVersionForm } from './add-version-form';

describe('AddVersionForm', () => {
  let fixture: ComponentFixture<AddVersionForm>;
  let component: AddVersionForm;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddVersionForm],
      providers: baseTestProviders(),
    }).compileComponents();

    fixture = TestBed.createComponent(AddVersionForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('defaults the release mode to schedule', () => {
    expect(component.formGroup.controls.releaseMode.value).toBe('schedule');
  });

  it('requires a release mode', () => {
    component.formGroup.controls.releaseMode.setValue(null as never);

    expect(component.formGroup.controls.releaseMode.valid).toBeFalse();
    expect(component.getFormValue()).toBeNull();
  });

  it('returns the chosen release mode with the form value', () => {
    component.formGroup.patchValue({ version: '1.2', releaseMode: 'immediate' });

    expect(component.getFormValue()?.releaseMode).toBe('immediate');
  });

  it('has no Auto Update checkbox any more', () => {
    expect('autoUpdate' in component.formGroup.controls).toBeFalse();
    expect(fixture.nativeElement.querySelector('hmdm-checkbox')).toBeNull();
  });

  it('offers the three modes when the app already has a released version', () => {
    const labels = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('mat-button-toggle'),
    ).map((el) => el.textContent?.trim());

    expect(labels.length).toBe(3);
  });

  it('hides the hold mode and resets it when the app has no released version yet', () => {
    component.formGroup.controls.releaseMode.setValue('hold');
    fixture.componentRef.setInput('allowHold', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('mat-button-toggle').length).toBe(2);
    expect(component.formGroup.controls.releaseMode.value).toBe('schedule');
  });
});
