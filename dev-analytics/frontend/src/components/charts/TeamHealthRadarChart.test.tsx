import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TeamHealthRadarChart, normalize } from './TeamHealthRadarChart';

// Recharts relies on DOM layout measurements unavailable in jsdom.
// Replace layout-sensitive components with minimal implementations that
// surface the data as visible text so assertions can find it.
vi.mock('recharts', async () => {
  const recharts = await vi.importActual<typeof import('recharts')>('recharts');
  return {
    ...recharts,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    RadarChart: ({
      data,
      children,
    }: {
      data?: Array<Record<string, unknown>>;
      children?: React.ReactNode;
    }) => (
      <div>
        <ul aria-label="radar-axes">
          {data?.map((d, i) => (
            <li key={i}>{String(d.metric ?? '')}</li>
          ))}
        </ul>
        {children}
      </div>
    ),
    PolarGrid: () => null,
    PolarAngleAxis: () => null,
    Radar: ({ name }: { name?: string }) => <span>{name}</span>,
    Legend: () => null,
    Tooltip: () => null,
  };
});
import type { MemberSummaryDto } from '@/types';

const members: MemberSummaryDto[] = [
  {
    userId: 1,
    username: 'alice',
    metrics: {
      DAILY_COMMITS_COUNT: 5,
      DAILY_CHURN_RATIO: 0.1,
      PR_LEAD_TIME_HOURS_MEDIAN: 12,
      MERGE_WITHOUT_REVIEW_RATIO: 0.05,
      FOCUS_RATIO_DAYS_TASKS: 3,
      REVIEW_RESPONSE_TIME_HOURS_MEDIAN: 4,
    },
  },
  {
    userId: 2,
    username: 'bob',
    metrics: {
      DAILY_COMMITS_COUNT: 2,
      DAILY_CHURN_RATIO: 0.3,
      PR_LEAD_TIME_HOURS_MEDIAN: 48,
      MERGE_WITHOUT_REVIEW_RATIO: 0.2,
      FOCUS_RATIO_DAYS_TASKS: 1,
      REVIEW_RESPONSE_TIME_HOURS_MEDIAN: 24,
    },
  },
];

describe('TeamHealthRadarChart', () => {
  it('renders the chart container with the correct member count in the accessible label', () => {
    render(<TeamHealthRadarChart members={members} />);

    expect(screen.getByText(/2 team members/i)).toBeInTheDocument();
  });

  it('renders nothing when the members array is empty', () => {
    const { container } = render(<TeamHealthRadarChart members={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders axis labels for all 6 expected metrics', () => {
    render(<TeamHealthRadarChart members={members} />);

    expect(screen.getByText('Daily Commits')).toBeInTheDocument();
    expect(screen.getByText('Churn Ratio')).toBeInTheDocument();
    expect(screen.getByText('PR Lead Time')).toBeInTheDocument();
    expect(screen.getByText('Merge w/o Review')).toBeInTheDocument();
    expect(screen.getByText('Focus Ratio')).toBeInTheDocument();
    expect(screen.getByText('Review Response')).toBeInTheDocument();
  });

  it('renders legend entries for each member username', () => {
    render(<TeamHealthRadarChart members={members} />);

    expect(screen.getByText('alice')).toBeInTheDocument();
    expect(screen.getByText('bob')).toBeInTheDocument();
  });
});

describe('normalize()', () => {
  it('assigns 1.0 to the best performer and 0.0 to the worst on non-divergent metrics', () => {
    const result = normalize([
      { userId: 1, username: 'alice', metrics: { DAILY_COMMITS_COUNT: 10 } },
      { userId: 2, username: 'bob', metrics: { DAILY_COMMITS_COUNT: 2 } },
    ]);

    const alice = result.find((r) => r.username === 'alice')!;
    const bob = result.find((r) => r.username === 'bob')!;

    expect(alice['Daily Commits']).toBe(1);
    expect(bob['Daily Commits']).toBe(0);
  });

  it('inverts divergent metrics so 1.0 = lowest (best) raw value', () => {
    const result = normalize([
      { userId: 1, username: 'alice', metrics: { PR_LEAD_TIME_HOURS_MEDIAN: 8 } },
      { userId: 2, username: 'bob', metrics: { PR_LEAD_TIME_HOURS_MEDIAN: 48 } },
    ]);

    const alice = result.find((r) => r.username === 'alice')!;
    const bob = result.find((r) => r.username === 'bob')!;

    expect(alice['PR Lead Time']).toBe(1);
    expect(bob['PR Lead Time']).toBe(0);
  });

  it('assigns 0.5 to all members when all values are equal (no spread)', () => {
    const result = normalize([
      { userId: 1, username: 'alice', metrics: { DAILY_COMMITS_COUNT: 5 } },
      { userId: 2, username: 'bob', metrics: { DAILY_COMMITS_COUNT: 5 } },
    ]);

    expect(result.find((r) => r.username === 'alice')!['Daily Commits']).toBe(0.5);
    expect(result.find((r) => r.username === 'bob')!['Daily Commits']).toBe(0.5);
  });

  it('assigns 0 to all members when the metric is absent from all records', () => {
    const result = normalize([
      { userId: 1, username: 'alice', metrics: {} },
      { userId: 2, username: 'bob', metrics: {} },
    ]);

    expect(result.find((r) => r.username === 'alice')!['Daily Commits']).toBe(0);
    expect(result.find((r) => r.username === 'bob')!['Daily Commits']).toBe(0);
  });
});
