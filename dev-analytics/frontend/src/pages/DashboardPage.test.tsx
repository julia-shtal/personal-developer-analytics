import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
    mergeToMain: vi.fn().mockResolvedValue({ data: null }),
    deepWorkStreak: vi.fn().mockResolvedValue({ data: null }),
    mergeWithoutReview: vi.fn().mockResolvedValue({ data: null }),
    prSizeComplexity: vi.fn().mockResolvedValue({ data: null }),
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
