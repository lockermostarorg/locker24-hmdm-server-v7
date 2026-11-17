import { formatDate } from '@angular/common';
import { DestroyRef, inject, Injectable, Provider } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MatDateFormats,
  NativeDateAdapter,
} from '@angular/material/core';
import { TranslateService } from '@ngx-translate/core';

export const DATE_FORMAT = 'dd.MM.yyyy';
export const DATE_TIME_FORMAT = 'dd.MM.yyyy HH:mm';
export const DATE_TIME_SECONDS_FORMAT = 'dd.MM.yyyy HH:mm:ss';
export const DATE_INPUT_HINT = 'DD.MM.YYYY';
export const MONTH_FORMAT = 'MM.yyyy';

export function formatDisplayDate(
  value: Date | number | string,
  format = DATE_TIME_FORMAT,
): string {
  return formatDate(value, format, 'en-US');
}

export function formatMonthLabel(yearMonth: string | null | undefined): string {
  const match = /^(\d{4})-(\d{1,2})$/.exec(yearMonth ?? '');

  return match
    ? formatDisplayDate(new Date(Number(match[1]), Number(match[2]) - 1, 1), MONTH_FORMAT)
    : (yearMonth ?? '');
}

export const HMDM_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: DATE_FORMAT },
  display: {
    dateInput: DATE_FORMAT,
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
  },
};

const DAY_FIRST_DATE = /^(\d{1,2})\s*([./-])\s*(\d{1,2})(?:\s*\2\s*(\d{4}|\d{2}))?\s*\2?$/;
const ISO_DATE = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;

const CALENDAR_LOCALES: Record<string, string> = {
  bs_BA: 'sr-Latn-BA',
};

export function toIntlLocale(language: string | null | undefined): string {
  const lang = language || 'en_US';
  return CALENDAR_LOCALES[lang] ?? lang.replace(/_/g, '-');
}

@Injectable()
export class HmdmDateAdapter extends NativeDateAdapter {
  private readonly translate = inject(TranslateService, { optional: true });

  constructor() {
    super();

    if (this.translate) {
      this.followLanguage(this.translate.getCurrentLang());
      this.translate.onLangChange
        .pipe(takeUntilDestroyed(inject(DestroyRef)))
        .subscribe((event) => this.followLanguage(event.lang));
    }
  }

  override format(date: Date, displayFormat: object | string): string {
    if (typeof displayFormat === 'string') {
      if (!this.isValid(date)) {
        throw Error('HmdmDateAdapter: Cannot format invalid date.');
      }

      return formatDisplayDate(date, displayFormat);
    }

    return super.format(date, displayFormat);
  }

  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value !== 'string') {
      return super.parse(value, parseFormat);
    }

    const text = value.trim();

    if (!text) {
      return null;
    }

    const dayFirst = DAY_FIRST_DATE.exec(text);

    if (dayFirst) {
      const year = dayFirst[4]
        ? Number(dayFirst[4]) + (dayFirst[4].length === 2 ? 2000 : 0)
        : new Date().getFullYear();

      return this.calendarDate(year, Number(dayFirst[3]), Number(dayFirst[1]));
    }

    const iso = ISO_DATE.exec(text);

    if (iso) {
      return this.calendarDate(Number(iso[1]), Number(iso[2]), Number(iso[3]));
    }

    return this.invalid();
  }

  private calendarDate(year: number, month: number, day: number): Date {
    const date = new Date(year, month - 1, day);

    return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
      ? date
      : this.invalid();
  }

  private followLanguage(language: string | null | undefined): void {
    try {
      this.setLocale(new Intl.Locale(toIntlLocale(language)).toString());
    } catch {
      this.setLocale('en-US');
    }
  }
}

export function provideHmdmDateAdapter(): Provider[] {
  return [
    { provide: DateAdapter, useClass: HmdmDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: HMDM_DATE_FORMATS },
  ];
}
