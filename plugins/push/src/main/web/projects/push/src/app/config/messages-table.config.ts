import { Injectable } from '@angular/core';
import { DATE_TIME_SECONDS_FORMAT, DatetimeCell, TTableConfig } from 'hmdm-ui-kit';

@Injectable({
  providedIn: 'root',
})
export class MessagesTableConfig {
  getConfig(): TTableConfig {
    return {
      columns: [
        {
          field: 'ts',
          title: 'table.heading.plugin.push.dateTime',
          cellRenderer: DatetimeCell,
          cellRendererParams: {
            timeFormat: DATE_TIME_SECONDS_FORMAT,
          },
        },
        {
          field: 'deviceNumber',
          title: 'table.heading.plugin.push.deviceNumber',
        },
        {
          field: 'messageType',
          title: 'table.heading.plugin.push.messageType',
        },
        {
          field: 'payload',
          title: 'table.heading.plugin.push.payload',
        },
      ],
      paginator: {
        pageSizeOptions: [10, 20, 50],
        pageSize: 50,
      },
    };
  }
}
