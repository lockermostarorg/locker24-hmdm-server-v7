import { ComponentFixture, TestBed } from '@angular/core/testing';
import { baseTestProviders } from '../../../testing/test-providers';
import { ApkForm } from './apk-form';

describe('ApkForm release choice for a new application', () => {
  let fixture: ComponentFixture<ApkForm>;
  let component: ApkForm;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ApkForm], providers: baseTestProviders() });
    fixture = TestBed.createComponent(ApkForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const toggles = (): number =>
    fixture.nativeElement.querySelectorAll('core-release-mode-field mat-button-toggle').length;

  it('defaults to schedule and shows no choice before a file is uploaded', () => {
    expect(component.formGroup.controls.releaseMode.value).toBe('schedule');
    expect(toggles()).toBe(0);
  });

  it('offers schedule and immediately, not hold, for an application that is new', () => {
    component.fileUploaded.set(true);
    fixture.detectChanges();

    expect(toggles()).toBe(2);
  });

  it('also offers hold when the uploaded package already belongs to an application', () => {
    component.fileUploaded.set(true);
    component.existingApplication.set(true);
    fixture.detectChanges();

    expect(toggles()).toBe(3);
  });

  it('carries the chosen mode in the emitted value', () => {
    component.formGroup.patchValue({ name: 'n', pkg: 'p', version: '1', releaseMode: 'immediate' });
    let emitted: { releaseMode?: string } | null = null;
    component.formChange.subscribe((value) => (emitted = value));

    component.formGroup.patchValue({ name: 'n2' });

    expect(emitted).not.toBeNull();
    expect(emitted!.releaseMode).toBe('immediate');
  });
});
