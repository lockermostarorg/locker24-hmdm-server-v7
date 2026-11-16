import { chartTheme, lineChartOptions, OVERLAY_PLUGIN, TOverlayOptions } from './chart';

describe('chart', () => {
  function fakeChart() {
    const ctx = jasmine.createSpyObj('ctx', [
      'save',
      'restore',
      'fillRect',
      'setLineDash',
      'beginPath',
      'moveTo',
      'lineTo',
      'stroke',
    ]);
    return {
      ctx,
      chartArea: { left: 10, right: 110, top: 0, bottom: 100 },
      scales: {
        x: { getPixelForValue: (v: number) => v },
        y: { getPixelForValue: (v: number) => 100 - v },
      },
    };
  }

  function draw(chart: ReturnType<typeof fakeChart>, options: TOverlayOptions) {
    (OVERLAY_PLUGIN.beforeDatasetsDraw as (c: unknown, a: unknown, o: unknown) => void)(
      chart,
      {},
      options,
    );
  }

  it('shades offline periods clipped to the chart area', () => {
    const chart = fakeChart();
    draw(chart, {
      periods: [
        { from: 0, to: 30 },
        { from: 50, to: 60 },
        { from: 200, to: 300 },
      ],
      bandColor: 'red',
      thresholds: [],
    });
    expect(chart.ctx.fillRect.calls.allArgs()).toEqual([
      [10, 0, 20, 100],
      [50, 0, 10, 100],
    ]);
  });

  it('draws threshold lines that fall inside the chart area', () => {
    const chart = fakeChart();
    draw(chart, {
      periods: [],
      bandColor: 'red',
      thresholds: [
        { value: 20, color: 'orange' },
        { value: 500, color: 'red' },
      ],
    });
    expect(chart.ctx.moveTo).toHaveBeenCalledOnceWith(10, 80);
    expect(chart.ctx.lineTo).toHaveBeenCalledOnceWith(110, 80);
  });

  it('pins the x axis to the requested range and passes the overlay options', () => {
    const theme = chartTheme();
    const options = lineChartOptions({
      label: 'Battery',
      unit: '%',
      data: [],
      from: 1000,
      to: 5000,
      periods: [{ from: 2000, to: 3000 }],
      thresholds: [],
      timeZone: 'UTC',
      yMin: 0,
      yMax: 100,
      theme,
    })!;
    const x = options.scales!['x'] as { min: number; max: number; type: string };
    expect(x.type).toBe('linear');
    expect(x.min).toBe(1000);
    expect(x.max).toBe(5000);
    expect(options.plugins!.l24Overlay!.periods).toEqual([{ from: 2000, to: 3000 }]);
  });
});
