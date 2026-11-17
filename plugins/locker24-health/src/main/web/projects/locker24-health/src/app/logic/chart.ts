import type { ChartConfiguration, Plugin } from 'chart.js';
import { themeToken } from 'hmdm-ui-kit';
import { TOfflinePeriod } from '../types/history.type';
import { absoluteTime, alignedTicks, tickLabel, tickStepMs } from './format';
import { TXY } from './history';

export type TThreshold = {
  value: number;
  color: string;
};

export type TOverlayOptions = {
  periods: TOfflinePeriod[];
  bandColor: string;
  thresholds: TThreshold[];
};

declare module 'chart.js' {
  interface PluginOptionsByType<TType> {
    l24Overlay?: TOverlayOptions;
  }
}

export const OVERLAY_PLUGIN: Plugin<'line', TOverlayOptions> = {
  id: 'l24Overlay',
  beforeDatasetsDraw(chart, _args, options) {
    const x = chart.scales['x'];
    const y = chart.scales['y'];
    const area = chart.chartArea;
    if (!x || !y || !area || !options) {
      return;
    }
    const ctx = chart.ctx;
    ctx.save();
    ctx.fillStyle = options.bandColor;
    ctx.globalAlpha = 0.15;
    (options.periods ?? []).forEach((period) => {
      const left = Math.max(area.left, x.getPixelForValue(period.from));
      const right = Math.min(area.right, x.getPixelForValue(period.to));
      if (right > left) {
        ctx.fillRect(left, area.top, right - left, area.bottom - area.top);
      }
    });
    ctx.globalAlpha = 0.8;
    ctx.setLineDash([6, 4]);
    ctx.lineWidth = 1;
    (options.thresholds ?? []).forEach((threshold) => {
      const py = y.getPixelForValue(threshold.value);
      if (py >= area.top && py <= area.bottom) {
        ctx.strokeStyle = threshold.color;
        ctx.beginPath();
        ctx.moveTo(area.left, py);
        ctx.lineTo(area.right, py);
        ctx.stroke();
      }
    });
    ctx.restore();
  },
};

export type TChartTheme = {
  line: string;
  band: string;
  grid: string;
  text: string;
  warn: string;
  error: string;
};

export function chartTheme(): TChartTheme {
  return {
    line: themeToken('--l24-primary'),
    band: themeToken('--l24-status-error'),
    grid: themeToken('--l24-chart-grid'),
    text: themeToken('--l24-chart-text'),
    warn: themeToken('--l24-status-warn'),
    error: themeToken('--l24-status-error'),
  };
}

export type TLineChartInput = {
  label: string;
  unit: string;
  data: TXY[];
  from: number;
  to: number;
  periods: TOfflinePeriod[];
  thresholds: TThreshold[];
  timeZone: string | null;
  yMin?: number;
  yMax?: number;
  ySuggestedMax?: number;
  theme: TChartTheme;
};

export function lineChartData(input: TLineChartInput): ChartConfiguration<'line'>['data'] {
  return {
    datasets: [
      {
        label: input.label,
        data: input.data,
        borderColor: input.theme.line,
        backgroundColor: input.theme.line,
        borderWidth: 2,
        pointRadius: input.data.length > 120 ? 0 : 2,
        pointHoverRadius: 4,
        spanGaps: false,
        tension: 0,
      },
    ],
  };
}

export function lineChartOptions(input: TLineChartInput): ChartConfiguration<'line'>['options'] {
  const step = tickStepMs(input.to - input.from);
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    parsing: false,
    normalized: true,
    interaction: { mode: 'nearest', axis: 'x', intersect: false },
    scales: {
      x: {
        type: 'linear',
        min: input.from,
        max: input.to,
        grid: { color: input.theme.grid },
        afterBuildTicks: (axis) => {
          axis.ticks = alignedTicks(input.from, input.to, input.timeZone).map((value) => ({
            value,
          }));
        },
        ticks: {
          color: input.theme.text,
          autoSkip: true,
          maxRotation: 0,
          callback: (value) => tickLabel(Number(value), input.timeZone, step),
        },
      },
      y: {
        min: input.yMin,
        max: input.yMax,
        suggestedMax: input.ySuggestedMax,
        grid: { color: input.theme.grid },
        ticks: {
          color: input.theme.text,
          callback: (value) => `${value} ${input.unit}`,
        },
      },
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          title: (items) =>
            items.length ? absoluteTime(items[0].parsed.x, input.timeZone, false) : '',
          label: (item) => `${item.parsed.y} ${input.unit}`,
        },
      },
      l24Overlay: {
        periods: input.periods,
        bandColor: input.theme.band,
        thresholds: input.thresholds,
      },
    },
  };
}
