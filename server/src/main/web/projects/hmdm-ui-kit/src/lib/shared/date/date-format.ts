import { formatDate } from '@angular/common';
import { inject, Injectable, Provider } from '@angular/core';
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

export function formatDisplayDate(
  value: Date | number | string,
  format = DATE_TIME_FORMAT,
): string {
  return formatDate(value, format, 'en-US');
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

const DOTTED_DATE = /^\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\.?\s*$/;

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
      this.translate.onLangChange.subscribe((event) => this.followLanguage(event.lang));
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
    if (typeof value === 'string') {
      const match = DOTTED_DATE.exec(value);

      if (match) {
        const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
        const date = new Date(year, month - 1, day);

        return date.getFullYear() === year &&
          date.getMonth() === month - 1 &&
          date.getDate() === day
          ? date
          : this.invalid();
      }
    }

    return super.parse(value, parseFormat);
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
