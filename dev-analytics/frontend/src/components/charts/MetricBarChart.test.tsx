import { describe, it, expect, beforeAll, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MetricBarChart } from './MetricBarChart';
import type { MetricPointDto } from '@/types';

// Recharts' ResponsiveContainer measures its parent via ResizeObserver +
// getBoundingClientRect, neither of which produce non-zero sizes in jsdom.
// Without a size, recharts renders an empty <div> and skips Bar/Line/Legend
// entirely. Stub both so the chart measures a real (fixed) size, matching
// how real browsers report layout.
beforeAll(() => {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  vi.stubGlobal('ResizeObserver', ResizeObserverStub);

  // Only the ResponsiveContainer's own div needs a non-zero rect. If every
  // element (including the legend's internal self-measurement) reports the
  // full 600x240 rect, recharts reserves that much space for the legend and
  // the plot area collapses to zero height.
  Element.prototype.getBoundingClientRect = function (this: Element) {
    const size = this.classList.contains('recharts-responsive-container')
      ? { width: 600, height: 240 }
      : { width: 0, height: 0 };
    return {
      ...size,
      top: 0,
      left: 0,
      bottom: size.height,
      right: size.width,
      x: 0,
      y: 0,
      toJSON() {},
    } as DOMRect;
  };
});

function point(date: string, value: number): MetricPointDto {
  return { date, value, metricType: 'DAILY_COMMITS_COUNT' };
}

const primaryData = [point('2026-05-01', 3), point('2026-05-02', 5), point('2026-05-03', 2)];
const compareData = [point('2026-04-01', 1), point('2026-04-02', 4), point('2026-04-03', 3)];

describe('MetricBarChart', () => {
  it('renders a single bar series and no legend when compareData is absent', () => {
    const { container } = render(<MetricBarChart data={primaryData} label="commits/day" />);
    expect(container.querySelectorAll('.recharts-bar')).toHaveLength(1);
    expect(container.querySelector('.recharts-legend-wrapper')).toBeNull();
  });

  it('renders the comparison series as a second bar, never a line', () => {
    const { container } = render(
      <MetricBarChart
        data={primaryData}
        compareData={compareData}
        label="commits/day"
        primaryRangeLabel="1 May – 3 May"
        compareRangeLabel="1 Apr – 3 Apr"
      />,
    );
    // Two grouped bar series (this period + previous period), no spline line.
    expect(container.querySelectorAll('.recharts-bar')).toHaveLength(2);
    expect(container.querySelector('.recharts-line')).toBeNull();
  });

  it('labels the legend "This period" / "Previous period" with their date ranges', () => {
    const { getByText } = render(
      <MetricBarChart
        data={primaryData}
        compareData={compareData}
        label="commits/day"
        primaryRangeLabel="1 May – 3 May"
        compareRangeLabel="1 Apr – 3 Apr"
      />,
    );
    expect(getByText('This period — 1 May – 3 May')).toBeInTheDocument();
    expect(getByText('Previous period — 1 Apr – 3 Apr')).toBeInTheDocument();
  });

  it('shows the day-of-period subtitle only when comparing', () => {
    const { queryByText, rerender } = render(<MetricBarChart data={primaryData} label="commits/day" />);
    expect(queryByText(/day of period/i)).toBeNull();

    rerender(
      <MetricBarChart
        data={primaryData}
        compareData={compareData}
        label="commits/day"
        primaryRangeLabel="1 May – 3 May"
        compareRangeLabel="1 Apr – 3 Apr"
      />,
    );
    expect(queryByText(/day of period/i)).not.toBeNull();
  });

  it('ignores an empty compareData array and renders as non-comparing', () => {
    const { container } = render(<MetricBarChart data={primaryData} compareData={[]} label="commits/day" />);
    expect(container.querySelector('.recharts-line')).toBeNull();
  });
});
