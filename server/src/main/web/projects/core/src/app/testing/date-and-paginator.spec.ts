import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { MatPaginator, MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';
import {
  DATE_FORMAT,
  DATE_TIME_SECONDS_FORMAT,
  DatetimeCell,
  formatDisplayDate,
  provideHmdmDateAdapter,
} from 'hmdm-ui-kit';
import { TranslatedPaginatorIntl } from '../shared/i18n/translated-paginator-intl';
import { baseTestProviders, loadLocale } from './test-providers';

const at = new Date(2026, 10, 7, 9, 5, 7).getTime();

describe('panel date format', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: baseTestProviders() });
  });

  function renderCell(provided: Record<string, unknown>, value: unknown = at): string {
    const fixture = TestBed.createComponent(DatetimeCell);
    fixture.componentRef.setInput('params', { data: {}, value, rowIndex: 0, provided });
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.trim();
  }

  it('shows list dates as dd.MM.yyyy HH:mm by default', () => {
    expect(renderCell({})).toBe('07.11.2026 09:05');
  });

  it('shows seconds where a list asks for them', () => {
    expect(renderCell({ timeFormat: DATE_TIME_SECONDS_FORMAT })).toBe('07.11.2026 09:05:07');
  });

  it('formats a date-only value', () => {
    expect(formatDisplayDate(at, DATE_FORMAT)).toBe('07.11.2026');
  });
});

describe('panel datepicker adapter', () => {
  let adapter: DateAdapter<Date>;
  let formats: MatDateFormats;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...baseTestProviders(), provideHmdmDateAdapter()],
    });
    adapter = TestBed.inject(DateAdapter);
    formats = TestBed.inject(MAT_DATE_FORMATS);
  });

  it('displays the input as dd.MM.yyyy', () => {
    expect(adapter.format(new Date(2026, 0, 5), formats.display.dateInput)).toBe('05.01.2026');
  });

  it('parses dd.MM.yyyy typed by the user', () => {
    const parsed = adapter.parse('5.1.2026', formats.parse.dateInput)!;

    expect([parsed.getFullYear(), parsed.getMonth(), parsed.getDate()]).toEqual([2026, 0, 5]);
    expect(adapter.isValid(adapter.parse('31.02.2026', formats.parse.dateInput)!)).toBeFalse();
  });

  it('follows the panel language for month names', async () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('bs_BA', await loadLocale('bs_BA'));
    translate.use('bs_BA');

    expect(adapter.getMonthNames('long')[10].toLowerCase()).toBe('novembar');
    expect(adapter.getFirstDayOfWeek()).toBe(1);
  });
});

@Component({
  template: `<mat-paginator
    [length]="50"
    [pageSize]="10"
    [pageIndex]="1"
    [pageSizeOptions]="[10, 25]"
  />`,
  imports: [MatPaginator],
})
class PaginatorHost {}

describe('translated paginator', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [PaginatorHost],
      providers: [
        ...baseTestProviders(),
        { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en_US', await loadLocale('en_US'));
    translate.setTranslation('bs_BA', await loadLocale('bs_BA'));
    translate.use('en_US');
  });

  it('shows Bosnian labels and range', () => {
    TestBed.inject(TranslateService).use('bs_BA');
    const fixture = TestBed.createComponent(PaginatorHost);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.textContent).toContain('Stavki po stranici:');
    expect(element.textContent).toContain('11 – 20 od 50');
    expect(
      element.querySelector('.mat-mdc-paginator-navigation-next')?.getAttribute('aria-label'),
    ).toBe('Sljedeća stranica');
  });

  it('updates the labels when the language changes', () => {
    const fixture = TestBed.createComponent(PaginatorHost);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Items per page:');

    TestBed.inject(TranslateService).use('bs_BA');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Stavki po stranici:');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('11 – 20 od 50');
  });

  it('keeps the English range for the empty list', () => {
    const intl = TestBed.inject(MatPaginatorIntl);

    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 of 0');
  });
});
