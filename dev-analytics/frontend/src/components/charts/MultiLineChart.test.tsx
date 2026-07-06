import { describe, it, expect, beforeAll, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MultiLineChart, type LineSeriesDef } from './MultiLineChart';
import type { MetricPointDto } from '@/types';

// Recharts' ResponsiveContainer measures its parent via ResizeObserver +
// getBoundingClientRect, neither of which produce non-zero sizes in jsdom.
// Stub both so the chart measures a real (fixed) size — same approach as
// MetricBarChart.test.tsx.
beforeAll(() => {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  vi.stubGlobal('ResizeObserver', ResizeObserverStub);

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

const alice: LineSeriesDef = {
  label: 'alice',
  color: '#7c3aed',
  data: [point('2026-05-01', 3), point('2026-05-02', 5)],
};
const bob: LineSeriesDef = {
  label: 'bob',
  color: '#0ea5e9',
  dashed: true,
  data: [point('2026-05-01', 1), point('2026-05-02', 4)],
};

describe('MultiLineChart — series (comparison) mode', () => {
  it('renders one line per series when two series are provided', () => {
    const { container } = render(<MultiLineChart series={[alice, bob]} />);
    expect(container.querySelectorAll('.recharts-line')).toHaveLength(2);
  });

  it('renders a single line when only one series is provided', () => {
    const { container } = render(<MultiLineChart series={[alice]} />);
    expect(container.querySelectorAll('.recharts-line')).toHaveLength(1);
  });

  it('applies a dashed stroke to the series flagged dashed and a solid stroke otherwise', () => {
    const { container } = render(<MultiLineChart series={[alice, bob]} />);
    const curves = container.querySelectorAll('.recharts-line-curve');
    const dasharrays = [...curves].map((c) => c.getAttribute('stroke-dasharray'));
    // The comparison series (bob) is dashed; the primary (alice) is solid.
    expect(dasharrays).toContain('4 3');
    expect(dasharrays.filter((d) => d === '4 3')).toHaveLength(1);
  });

  it('shows both member usernames in the legend', () => {
    const { getByText } = render(<MultiLineChart series={[alice, bob]} />);
    expect(getByText('alice')).toBeInTheDocument();
    expect(getByText('bob')).toBeInTheDocument();
  });
});
