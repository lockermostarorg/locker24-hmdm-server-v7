import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DatetimeCell } from 'hmdm-ui-kit';
import { baseTestProviders, useEnglish } from './test-providers';

describe('DatetimeCell', () => {
  it('follows the row when params switch to another row', async () => {
    TestBed.configureTestingModule({ imports: [DatetimeCell], providers: baseTestProviders() });
    await useEnglish(TestBed.inject(TranslateService));
    const fixture = TestBed.createComponent(DatetimeCell);
    const first = new Date(2020, 0, 1).getTime();
    const second = new Date(2024, 5, 15).getTime();
    fixture.componentRef.setInput('params', { value: first, data: {}, provided: {} });
    fixture.detectChanges();
    expect(fixture.componentInstance.date()?.getTime()).toBe(first);

    fixture.componentRef.setInput('params', { value: second, data: {}, provided: {} });
    fixture.detectChanges();

    expect(fixture.componentInstance.date()?.getTime()).toBe(second);
    expect(fixture.nativeElement.textContent).toContain('2024');
  });
});
