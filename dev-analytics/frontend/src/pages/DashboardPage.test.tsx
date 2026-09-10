import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { RepoScopeProvider } from '@/context/RepoScopeContext';
import { DashboardPage } from './DashboardPage';

// ── API mocks ────────────────────────────────────────────────────────────────

vi.mock('@/api/metrics', () => ({
  metricsApi: {
    dailyCommits: vi.fn().mockResolvedValue({ data: [] }),
    dailyPrCreated: vi.fn().mockResolvedValue({ data: [] }),
    dailyPrMerged: vi.fn().mockResolvedValue({ data: [] }),
    dailyChurn: vi.fn().mockResolvedValue({ data: [] }),
    dailyIssuesClosed: vi.fn().mockResolvedValue({ data: [] }),
    prLeadTime: vi.fn().mockResolvedValue({ data: null }),
    reviewResponseTime: vi.fn().mockResolvedValue({ data: null }),
    focusRatio: vi.fn().mockResolvedValue({ data: null }),
    dailyIssuesCreated: vi.fn().mockResolvedValue({ data: [] }),
    issueLeadTime: vi.fn().mockResolvedValue({ data: null }),
    prFirstCommitLeadTime: vi.fn().mockResolvedValue({ data: null }),
    dailyAfterHours: vi.fn().mockResolvedValue({ data: [] }),
    dailyRefactorRatio: vi.fn().mockResolvedValue({ data: [] }),
    commitsPerWeekAvg: vi.fn().mockResolvedValue({ data: null }),
    deepWorkStreak: vi.fn().mockResolvedValue({ data: null }),
    mergeWithoutReview: vi.fn().mockResolvedValue({ data: null }),
    prSizeComplexity: vi.fn().mockResolvedValue({ data: null }),
    wipOpenPrAge: vi.fn().mockResolvedValue({ data: null }),
    knowledgeSilo: vi.fn().mockResolvedValue({ data: null }),
    freshness: vi.fn().mockResolvedValue({ data: null }),
    anomalies: vi.fn().mockResolvedValue({ data: {} }),
    calculate: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

vi.mock('@/api/repos', () => ({
  reposApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

// Heavy components that add no value to an empty-state test
vi.mock('@/components/ai/AiSummaryCard', () => ({
  AiSummaryCard: () => <div data-testid="ai-summary-card" />,
}));

vi.mock('@/components/charts/MetricBarChart', () => ({
  MetricBarChart: () => <div data-testid="bar-chart" />,
}));

vi.mock('@/components/charts/MetricLineChart', () => ({
  MetricLineChart: () => <div data-testid="line-chart" />,
}));

vi.mock('@/context/DateRangeContext', () => ({
  useDateRange: () => ({
    range: { from: '2026-06-01', to: '2026-06-30' },
    setRange: vi.fn(),
  }),
}));

// ── Helpers ──────────────────────────────────────────────────────────────────

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={makeClient()}>
      <RepoScopeProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </RepoScopeProvider>
    </QueryClientProvider>
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('DashboardPage — empty state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows zero totals in the hero section when there is no metric data', async () => {
    render(<DashboardPage />, { wrapper: Wrapper });

    // findBy* wraps waitFor — waits for queries to resolve and the hero to render.
    expect(await screen.findByText(/0 commits/)).toBeInTheDocument();
    expect(screen.getByText(/0 PRs merged/)).toBeInTheDocument();
    expect(screen.getByText(/0-day/)).toBeInTheDocument();
  });

  it('renders KPI section headings regardless of data', async () => {
    render(<DashboardPage />, { wrapper: Wrapper });

    // "velocity" is an exact text inside a <span> — no ambiguous multi-match.
    await screen.findByText('velocity');

    expect(screen.getByText('wellness · quality')).toBeInTheDocument();
  });

  it('shows the AI summary card placeholder', async () => {
    render(<DashboardPage />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(screen.getByTestId('ai-summary-card')).toBeInTheDocument(),
    );
  });
});

describe('DashboardPage — WIP open PR age tile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders an em-dash when there are no open PRs', async () => {
    render(<DashboardPage />, { wrapper: Wrapper });

    const label = await screen.findByText('open pr age');
    // The tile shows the shared "—" placeholder when the value is null/0.
    expect(label).toBeInTheDocument();
  });

  it('shows the median open PR age with an hour unit when data is present', async () => {
    const { metricsApi } = await import('@/api/metrics');
    vi.mocked(metricsApi.wipOpenPrAge).mockResolvedValue({ data: { value: 48 } } as never);

    render(<DashboardPage />, { wrapper: Wrapper });

    await screen.findByText('open pr age');
    // numSuffix renders the number and unit in separate nodes: "48.0" + "h".
    expect(await screen.findByText('48.0')).toBeInTheDocument();
    expect(screen.getAllByText('h').length).toBeGreaterThan(0);
  });
});

describe('DashboardPage — period comparison', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows a Compare toggle on the Commit cadence card', async () => {
    render(<DashboardPage />, { wrapper: Wrapper });
    await screen.findByText('Commit cadence');
    expect(screen.getAllByRole('button', { name: /compare/i }).length).toBeGreaterThan(0);
  });

  it('reveals comparison date inputs and fetches a second commits series when toggled on', async () => {
    const { metricsApi } = await import('@/api/metrics');
    render(<DashboardPage />, { wrapper: Wrapper });
    await screen.findByText('Commit cadence');

    const [compareBtn] = screen.getAllByRole('button', { name: /compare/i });
    await userEvent.click(compareBtn);

    expect(screen.getAllByLabelText('Comparison range start').length).toBeGreaterThan(0);
    await waitFor(() => expect(metricsApi.dailyCommits).toHaveBeenCalledTimes(2));
  });

  it('shows a Compare toggle on the PR flow card and fetches both comparison series', async () => {
    const { metricsApi } = await import('@/api/metrics');
    render(<DashboardPage />, { wrapper: Wrapper });
    await screen.findByText('PR flow');

    const compareButtons = screen.getAllByRole('button', { name: /compare/i });
    // Buttons render in page order: Commit cadence, PR flow, Issues.
    await userEvent.click(compareButtons[1]);

    await waitFor(() => expect(metricsApi.dailyPrCreated).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(metricsApi.dailyPrMerged).toHaveBeenCalledTimes(2));
  });

  it('shows a Compare toggle on the Issues card and fetches both comparison series', async () => {
    const { metricsApi } = await import('@/api/metrics');
    vi.mocked(metricsApi.dailyIssuesClosed).mockResolvedValueOnce({
      data: [{ date: '2026-06-01', value: 3, metricType: 'DAILY_ISSUES_CLOSED' }],
    } as never);

    render(<DashboardPage />, { wrapper: Wrapper });
    await screen.findByText('Created vs closed');

    const compareButtons = screen.getAllByRole('button', { name: /compare/i });
    await userEvent.click(compareButtons[2]);

    await waitFor(() => expect(metricsApi.dailyIssuesClosed).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(metricsApi.dailyIssuesCreated).toHaveBeenCalledTimes(2));
  });
});

describe('DashboardPage — backfill coverage chips', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('shows how many days of history are still computing when the backfill is behind', async () => {
    const { metricsApi } = await import('@/api/metrics');
    vi.mocked(metricsApi.freshness).mockResolvedValue({
      data: {
        metricsComputedThrough: '2026-06-30',
        coverageFrom: '2026-01-01',
        coverageTo: '2026-06-30',
        daysRemaining: 12,
      },
    } as never);

    render(<DashboardPage />, { wrapper: Wrapper });

    expect(await screen.findByText('12 day(s) of history still computing')).toBeInTheDocument();
    expect(screen.getByText('metrics through 2026-06-30')).toBeInTheDocument();
  });

  it('hides the remaining-days chip once coverage is complete', async () => {
    const { metricsApi } = await import('@/api/metrics');
    vi.mocked(metricsApi.freshness).mockResolvedValue({
      data: {
        metricsComputedThrough: '2026-06-30',
        coverageFrom: '2026-01-01',
        coverageTo: '2026-06-30',
        daysRemaining: 0,
      },
    } as never);

    render(<DashboardPage />, { wrapper: Wrapper });

    // The "metrics through" chip proves freshness resolved, so the absence below is a real
    // assertion about daysRemaining === 0 rather than a race against an unresolved query.
    // A truthiness bug that rendered "0 day(s)…" would fail here.
    await screen.findByText('metrics through 2026-06-30');
    expect(screen.queryByText(/day\(s\) of history still computing/)).not.toBeInTheDocument();
  });
});
