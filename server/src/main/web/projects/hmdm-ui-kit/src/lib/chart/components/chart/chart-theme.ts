import { ChartConfiguration, ChartType } from 'chart.js';
import { themeToken } from '../../../shared/theme/theme';

type ChartOptions = ChartConfiguration['options'];
type Scale = { ticks?: { color?: unknown }; grid?: { color?: unknown } };

const AXIS_CHARTS: ChartType[] = ['bar', 'line', 'scatter', 'bubble'];
const ARC_CHARTS: ChartType[] = ['doughnut', 'pie', 'polarArea'];

export function withThemeColours(options: ChartOptions, type: ChartType): ChartOptions {
  const text = themeToken('--l24-chart-text');
  const grid = themeToken('--l24-chart-grid');
  const surface = themeToken('--l24-surface');

  if (!text || !grid || !surface) {
    return options;
  }

  const base = (options ?? {}) as Record<string, any>;
  const themed: Record<string, any> = {
    ...base,
    color: base['color'] ?? text,
    borderColor: base['borderColor'] ?? grid,
    plugins: {
      ...base['plugins'],
      legend: {
        ...base['plugins']?.legend,
        labels: { color: text, ...base['plugins']?.legend?.labels },
      },
    },
  };

  if (AXIS_CHARTS.includes(type)) {
    const scales: Record<string, Scale> = { x: {}, y: {}, ...base['scales'] };
    themed['scales'] = Object.fromEntries(
      Object.entries(scales).map(([axis, scale]) => [
        axis,
        {
          ...scale,
          ticks: { color: text, ...scale.ticks },
          grid: { color: grid, ...scale.grid },
        },
      ]),
    );
  }

  if (ARC_CHARTS.includes(type)) {
    themed['elements'] = {
      ...base['elements'],
      arc: { borderColor: surface, ...base['elements']?.arc },
    };
  }

  return themed as ChartOptions;
}
