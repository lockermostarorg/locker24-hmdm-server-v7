import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TApplicationDTO } from '../../../entity/application/types/application-dto.type';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { ConfigurationAppsFacadeService } from '../../services/configuration-applications-facade.service';
import { AppsOrderCell } from './apps-order-cell';

describe('AppsOrderCell', () => {
  const app = (id: number, screenOrder: number | null) =>
    ({ id, version: '1', action: 1, showIcon: true, screenOrder }) as unknown as TApplicationDTO;

  it('follows the row when params switch to another row', async () => {
    const facade = jasmine.createSpyObj('ConfigurationAppsFacadeService', ['setAppOrder']);
    TestBed.configureTestingModule({
      imports: [AppsOrderCell],
      providers: [...baseTestProviders(), { provide: ConfigurationAppsFacadeService, useValue: facade }],
    });
    await useEnglish(TestBed.inject(TranslateService));
    const fixture = TestBed.createComponent(AppsOrderCell);
    fixture.componentRef.setInput('params', { data: app(1, 3) });
    fixture.detectChanges();
    expect(fixture.componentInstance.formControl.value).toBe('3');

    const rowB = app(2, 7);
    fixture.componentRef.setInput('params', { data: rowB });
    fixture.detectChanges();

    expect(fixture.componentInstance.formControl.value).toBe('7');
    expect(facade.setAppOrder).not.toHaveBeenCalled();
    fixture.componentInstance.formControl.setValue('9');
    expect(facade.setAppOrder).toHaveBeenCalledWith(rowB, 9);
  });
});
