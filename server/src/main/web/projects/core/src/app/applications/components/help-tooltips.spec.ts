import { ComponentFixture, TestBed } from '@angular/core/testing';
import { baseTestProviders } from '../../testing/test-providers';
import { AddVersionForm } from './add-version-form/add-version-form';
import { ApkForm } from './apk-form/apk-form';
import { ApplicationIconForm } from './application-icon-form/application-icon-form';
import { EditVersionForm } from './edit-version-form/edit-version-form';
import { SystemActionForm } from './system-action-form/system-action-form';
import { WebPageForm } from './web-page-form/web-page-form';

const CONTROLS = 'hmdm-text-input, hmdm-checkbox, hmdm-file-input, hmdm-radio, hmdm-selector';

function untipped(fixture: ComponentFixture<unknown>): string[] {
  const missing: string[] = [];
  const controls = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll(CONTROLS));
  controls.forEach((el) => {
    if (!el.querySelector('hmdm-help-tooltip')) {
      missing.push(el.getAttribute('formcontrolname') ?? el.tagName.toLowerCase());
    }
  });
  expect(controls.length).toBeGreaterThan(0);
  return missing;
}

describe('help tooltips on the application forms', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: baseTestProviders() });
  });

  it('covers every field of the application form', () => {
    const fixture = TestBed.createComponent(ApkForm);
    fixture.componentInstance.fileUploaded.set(true);
    fixture.detectChanges();

    expect(untipped(fixture)).toEqual([]);
  });

  it('covers every field of the add version form', () => {
    const fixture = TestBed.createComponent(AddVersionForm);
    fixture.detectChanges();

    expect(untipped(fixture)).toEqual([]);
  });

  it('covers every field of the edit version form, plain and split', () => {
    const fixture = TestBed.createComponent(EditVersionForm);
    fixture.componentRef.setInput('initialData', { version: '1', split: false });
    fixture.detectChanges();
    expect(untipped(fixture)).toEqual([]);

    fixture.componentInstance.formGroup.controls.split.setValue(true);
    fixture.detectChanges();
    expect(untipped(fixture)).toEqual([]);
  });

  it('covers every field of the web page form', () => {
    const fixture = TestBed.createComponent(WebPageForm);
    fixture.detectChanges();

    expect(untipped(fixture)).toEqual([]);
  });

  it('covers every field of the system action form', () => {
    const fixture = TestBed.createComponent(SystemActionForm);
    fixture.detectChanges();

    expect(untipped(fixture)).toEqual([]);
  });

  it('covers every field of the icon form', () => {
    const fixture = TestBed.createComponent(ApplicationIconForm);
    fixture.componentRef.setInput('applicationType', 'app');
    fixture.detectChanges();
    fixture.componentInstance.formGroup.controls.showIcon.setValue(true);
    fixture.detectChanges();

    expect(untipped(fixture)).toEqual([]);
  });
});
