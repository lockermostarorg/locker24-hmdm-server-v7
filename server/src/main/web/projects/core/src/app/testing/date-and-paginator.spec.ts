import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { MatPaginator, MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';
import {
  DATE_FORMAT,
  DATE_TIME_SECONDS_FORMAT,
  DateRange,
  DatetimeCell,
  formatDisplayDate,
  formatMonthLabel,
  provideHmdmDateAdapter,
} from 'hmdm-ui-kit';
import { SizeCell } from '../files/components/size-cell/size-cell';
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

  it('writes dashboard months as MM.yyyy', () => {
    expect(formatMonthLabel('2026-09')).toBe('09.2026');
    expect(formatMonthLabel('2026-11')).toBe('11.2026');
    expect(formatMonthLabel('other')).toBe('other');
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

  const ymd = (value: string): number[] | 'invalid' | null => {
    const parsed = adapter.parse(value, formats.parse.dateInput);

    if (parsed === null) {
      return null;
    }

    return adapter.isValid(parsed)
      ? [parsed.getFullYear(), parsed.getMonth() + 1, parsed.getDate()]
      : 'invalid';
  };

  const thisYear = new Date().getFullYear();
  const typed: [string, number[] | 'invalid' | null][] = [
    ['05.03.2026', [2026, 3, 5]],
    ['5.3.2026', [2026, 3, 5]],
    ['05.03.2026.', [2026, 3, 5]],
    ['05.03.26', [2026, 3, 5]],
    ['5.3.26', [2026, 3, 5]],
    ['5.3.', [thisYear, 3, 5]],
    ['05.03', [thisYear, 3, 5]],
    ['05/03/2026', [2026, 3, 5]],
    ['5/3/26', [2026, 3, 5]],
    ['05-03-2026', [2026, 3, 5]],
    ['2026-03-05', [2026, 3, 5]],
    ['31.02.2026', 'invalid'],
    ['03/15/2026', 'invalid'],
    ['05.03/2026', 'invalid'],
    ['March 5, 2026', 'invalid'],
    ['5.3.2026 10:00', 'invalid'],
    ['abc', 'invalid'],
    ['  ', null],
  ];

  for (const [input, expected] of typed) {
    it(`reads '${input}' day first`, () => {
      expect(ymd(input)).toEqual(expected);
    });
  }

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

@Component({
  template: `<hmdm-date-range label="Period" [formControl]="range" />`,
  imports: [DateRange, ReactiveFormsModule],
})
class DateRangeHost {
  range = new FormControl(null);
}

describe('date range placeholders', () => {
  it('are translated', async () => {
    TestBed.configureTestingModule({ imports: [DateRangeHost], providers: baseTestProviders() });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('bs_BA', await loadLocale('bs_BA'));
    translate.use('bs_BA');
    const fixture = TestBed.createComponent(DateRangeHost);
    fixture.detectChanges();
    const inputs = (fixture.nativeElement as HTMLElement).querySelectorAll('input');

    expect(inputs[0].getAttribute('placeholder')).toBe('Početni datum');
    expect(inputs[1].getAttribute('placeholder')).toBe('Krajnji datum');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('DD.MM.YYYY');
  });
});

describe('size cell', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('bs_BA', await loadLocale('bs_BA'));
    translate.use('bs_BA');
  });

  function render(value: unknown): string {
    const fixture = TestBed.createComponent(SizeCell);
    fixture.componentRef.setInput('params', { data: {}, value, rowIndex: 0, provided: {} });
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.trim();
  }

  it('is empty when the size is unknown', () => {
    expect(render(null)).toBe('');
    expect(render(undefined)).toBe('');
  });

  it('translates a deleted file', () => {
    expect(render(-1)).toBe('OBRISANO');
  });

  it('still shows known sizes', () => {
    expect(render(2048)).toBe('2.00 KB');
  });
});
