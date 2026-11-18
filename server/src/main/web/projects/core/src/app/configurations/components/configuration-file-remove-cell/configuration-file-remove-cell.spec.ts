import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { ConfigurationDetailsFacadeService } from '../../services/configuration-details-facade.service';
import { ConfigurationFileRemoveCell } from './configuration-file-remove-cell';

describe('ConfigurationFileRemoveCell', () => {
  it('reports the current row after params switch to another row', async () => {
    const facade = jasmine.createSpyObj('ConfigurationDetailsFacadeService', ['removeFileChange']);
    TestBed.configureTestingModule({
      imports: [ConfigurationFileRemoveCell],
      providers: [
        ...baseTestProviders(),
        { provide: ConfigurationDetailsFacadeService, useValue: facade },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
    const fixture = TestBed.createComponent(ConfigurationFileRemoveCell);
    fixture.componentRef.setInput('params', { data: { id: 1, remove: true } });
    fixture.detectChanges();
    expect(fixture.componentInstance.removeForm.value).toBe(true);

    fixture.componentRef.setInput('params', { data: { id: 2, remove: false } });
    fixture.detectChanges();

    expect(fixture.componentInstance.removeForm.value).toBe(false);
    expect(facade.removeFileChange).not.toHaveBeenCalled();

    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('input[type=checkbox]');
    checkbox.click();

    expect(facade.removeFileChange).toHaveBeenCalledOnceWith(2, true);
  });
});
