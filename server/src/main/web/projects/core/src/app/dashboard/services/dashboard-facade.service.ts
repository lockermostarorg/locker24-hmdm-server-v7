import { computed, inject, Injectable, signal, WritableSignal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ChartConfiguration } from 'chart.js';
import { themeToken } from 'hmdm-ui-kit';
import { SummaryService } from '../../entity/summary/services/summary.service';
import { TSummaryResponse } from '../../entity/summary/types/summary-response.type';
import { ThemeService } from '../../shared/services/theme.service';
import { arraySum } from '../../shared/utils/array-sum';

type ChartColours = { blue: string; blueLight: string; red: string; yellow: string };

@Injectable({ providedIn: 'root' })
export class DashboardFacadeService {
  private summaryService = inject(SummaryService);
  private translateService = inject(TranslateService);
  private themeService = inject(ThemeService);

  private _summary: WritableSignal<TSummaryResponse | null> = signal<TSummaryResponse | null>(null);

  private colours = computed<ChartColours>(() => {
    this.themeService.effective();
    return {
      blue: themeToken('--l24-chart-blue'),
      blueLight: themeToken('--l24-chart-blue-light'),
      red: themeToken('--l24-chart-red'),
      yellow: themeToken('--l24-chart-yellow'),
    };
  });

  enrollmentCircleData = this.chart((s, c) => this.buildEnrollmentCircle(s, c));
  enrollmentChartData = this.chart((s, c) => this.buildEnrollmentChart(s, c));
  deviceCircleData = this.chart((s, c) => this.buildDeviceCircle(s, c));
  deviceChartData = this.chart((s, c) => this.buildDeviceChart(s, c));
  applicationCircleData = this.chart((s, c) => this.buildApplicationCircle(s, c));
  applicationChartData = this.chart((s, c) => this.buildApplicationChart(s, c));
  summary = this._summary.asReadonly();

  constructor() {
    this.summaryService.getSummary().subscribe((summary) => {
      this._summary.set(summary);
    });
  }

  private chart(
    build: (summary: TSummaryResponse, colours: ChartColours) => ChartConfiguration['data'],
  ) {
    return computed<ChartConfiguration['data']>(() => {
      const summary = this._summary();
      return summary ? build(summary, this.colours()) : { datasets: [] };
    });
  }

  private buildEnrollmentCircle(
    summary: TSummaryResponse,
    c: ChartColours,
  ): ChartConfiguration['data'] {
    return {
      labels: [
        this.translateService.instant('summary.devices.enrolled.total'),
        this.translateService.instant('summary.devices.enrolled.earlier'),
      ],
      datasets: [
        {
          data: [summary.devicesEnrolled, summary.devicesEnrolledLastMonth],
          backgroundColor: [c.blue, c.blueLight],
        },
      ],
    };
  }

  private buildEnrollmentChart(
    summary: TSummaryResponse,
    c: ChartColours,
  ): ChartConfiguration['data'] {
    return {
      labels: summary.deviceEnrolledMonthly?.map((item) => item.stringAttr),
      datasets: [
        {
          data: summary.deviceEnrolledMonthly?.map((item) => item.intAttr),
          backgroundColor: c.blue,
        },
      ],
    };
  }

  private buildDeviceCircle(
    summary: TSummaryResponse,
    c: ChartColours,
  ): ChartConfiguration['data'] {
    return {
      labels: [
        this.translateService.instant('summary.devices.active'),
        this.translateService.instant('summary.devices.offline'),
        this.translateService.instant('summary.devices.idle'),
      ],
      datasets: [
        {
          data: [
            arraySum(summary.statusOnlineByConfig),
            arraySum(summary.statusOfflineByConfig),
            arraySum(summary.statusIdleByConfig),
          ],
          backgroundColor: [c.blue, c.red, c.yellow],
        },
      ],
    };
  }

  private buildDeviceChart(summary: TSummaryResponse, c: ChartColours): ChartConfiguration['data'] {
    const datasets = [
      {
        label: this.translateService.instant('summary.devices.active'),
        data: summary.statusOnlineByConfig,
        backgroundColor: c.blue,
      },
      {
        label: this.translateService.instant('summary.devices.offline'),
        data: summary.statusOfflineByConfig,
        backgroundColor: c.red,
      },
      {
        label: this.translateService.instant('summary.devices.idle'),
        data: summary.statusIdleByConfig,
        backgroundColor: c.yellow,
      },
    ];

    return {
      labels: summary.topConfigs,
      datasets: datasets,
    };
  }

  private buildApplicationCircle(
    summary: TSummaryResponse,
    c: ChartColours,
  ): ChartConfiguration['data'] {
    return {
      labels: [
        this.translateService.instant('summary.devices.installation.failed'),
        this.translateService.instant('summary.devices.installation.mismatch'),
        this.translateService.instant('summary.devices.installation.completed'),
      ],
      datasets: [
        {
          data: [
            arraySum(summary.appFailureByConfig),
            arraySum(summary.appMismatchByConfig),
            arraySum(summary.appSuccessByConfig),
          ],
          backgroundColor: [c.red, c.yellow, c.blue],
        },
      ],
    };
  }

  private buildApplicationChart(
    summary: TSummaryResponse,
    c: ChartColours,
  ): ChartConfiguration['data'] {
    const datasets = [
      {
        label: this.translateService.instant('summary.devices.installation.failed'),
        data: summary.appFailureByConfig,
        backgroundColor: c.red,
      },
      {
        label: this.translateService.instant('summary.devices.installation.mismatch'),
        data: summary.appMismatchByConfig,
        backgroundColor: c.yellow,
      },
      {
        label: this.translateService.instant('summary.devices.installation.completed'),
        data: summary.appSuccessByConfig,
        backgroundColor: c.blue,
      },
    ];

    return {
      labels: summary.topConfigs,
      datasets: datasets,
    };
  }
}
