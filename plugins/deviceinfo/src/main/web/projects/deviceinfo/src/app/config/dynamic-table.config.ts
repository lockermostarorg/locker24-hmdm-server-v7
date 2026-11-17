import { Injectable } from '@angular/core';
import { DATE_TIME_SECONDS_FORMAT, DatetimeCell, TTableConfig } from 'hmdm-ui-kit';

@Injectable({
  providedIn: 'root',
})
export class DynamicTableConfig {
  getConfig(): TTableConfig {
    return {
      columns: [
        {
          field: 'latestUpdateTime',
          title: 'plugin.deviceinfo.title.time',
          cellRenderer: DatetimeCell,
          cellRendererParams: {
            timeFormat: DATE_TIME_SECONDS_FORMAT,
          },
        },
      ],
      paginator: {
        pageSizeOptions: [10, 20, 50],
        pageSize: 50,
      },
    };
  }
}
