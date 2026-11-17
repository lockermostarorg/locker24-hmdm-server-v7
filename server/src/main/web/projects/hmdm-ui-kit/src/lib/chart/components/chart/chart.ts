import { DOCUMENT } from '@angular/common';
import { Component, computed, DestroyRef, inject, input, InputSignal, signal } from '@angular/core';
import { ChartConfiguration, ChartType, Plugin } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { THEME_CHANGE_EVENT } from '../../../shared/theme/theme';
import { withThemeColours } from './chart-theme';

/**
 * A reusable chart component that supports different chart types from Chart.js
 *
 * @example
 * <hmdm-chart
 *   [chartType]="'bar'"
 *   [data]="myChartData"
 *   [options]="myChartOptions"
 *   [showLegend]="true">
 * </hmdm-chart>
 */
@Component({
  selector: 'hmdm-chart',
  imports: [BaseChartDirective],
  templateUrl: './chart.html',
  styleUrl: './chart.scss',
})
export class Chart {
  /** Type of chart to display (bar, line, pie, doughnut, etc.) */
  chartType: InputSignal<ChartType> = input.required<ChartType>();

  /** Chart data including labels and datasets */
  data: InputSignal<ChartConfiguration['data']> = input.required<ChartConfiguration['data']>();

  /** Chart configuration options */
  options: InputSignal<ChartConfiguration['options']> = input<ChartConfiguration['options']>({
    responsive: true,
    maintainAspectRatio: false,
  });

  /** Whether to show the legend */
  showLegend: InputSignal<boolean> = input<boolean>(true);

  /** Chart plugins to use */
  plugins: InputSignal<Plugin[]> = input<Plugin[]>([]);

  height: InputSignal<number | string> = input<number | string>('400px');

  private readonly themeChanges = signal(0);

  /** Options with the axis, legend and border colours of the current theme */
  themedOptions = computed(() => {
    this.themeChanges();
    return withThemeColours(this.options(), this.chartType());
  });

  constructor() {
    const document = inject(DOCUMENT);
    const onThemeChange = () => this.themeChanges.update((count) => count + 1);
    document.addEventListener(THEME_CHANGE_EVENT, onThemeChange);
    inject(DestroyRef).onDestroy(() =>
      document.removeEventListener(THEME_CHANGE_EVENT, onThemeChange),
    );
  }
}
