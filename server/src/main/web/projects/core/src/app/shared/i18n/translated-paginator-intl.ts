import { inject, Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';

const LABEL_KEYS = {
  itemsPerPageLabel: 'paginator.items.per.page',
  nextPageLabel: 'paginator.next.page',
  previousPageLabel: 'paginator.previous.page',
  firstPageLabel: 'paginator.first.page',
  lastPageLabel: 'paginator.last.page',
} as const;

@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl {
  private readonly translate = inject(TranslateService);

  constructor() {
    super();

    this.translate.stream(Object.values(LABEL_KEYS)).subscribe((labels: Record<string, string>) => {
      for (const [field, key] of Object.entries(LABEL_KEYS)) {
        this[field as keyof typeof LABEL_KEYS] = labels[key];
      }

      this.changes.next();
    });
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length == 0 || pageSize == 0) {
      return this.translate.instant('paginator.range.empty', { length });
    }

    length = Math.max(length, 0);
    const start = page * pageSize;
    const end = start < length ? Math.min(start + pageSize, length) : start + pageSize;

    return this.translate.instant('paginator.range', { start: start + 1, end, length });
  };
}
