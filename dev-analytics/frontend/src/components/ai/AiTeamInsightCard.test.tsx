import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AiTeamInsightCard } from './AiTeamInsightCard';
import { aiApi } from '@/api/ai';
import type { MetricsSummaryDto } from '@/types/ai';
import type { MemberSummaryDto } from '@/types';

vi.mock('@/api/ai');

const range = { from: '2026-06-01', to: '2026-06-29' };

const mockSummary: MetricsSummaryDto = {
  headline: 'Team insight headline',
  overview: 'Overview text.',
  insights: [],
  recommendations: [],
  modelName: 'llama3.2',
  from: '2026-06-01',
  to: '2026-06-29',
  generatedAt: new Date().toISOString(),
  scope: 'TEAM',
  rawModelOutput: '{}',
};

const members: MemberSummaryDto[] = [
  { userId: 1, username: 'alice', metrics: { DAILY_COMMITS_COUNT: 5 } },
];

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(aiApi.latestTeamSummary).mockResolvedValue(null);
  return render(
    <QueryClientProvider client={qc}>
      <AiTeamInsightCard range={range} teamId={1} memberSummary={members} />
    </QueryClientProvider>,
  );
}

describe('AiTeamInsightCard — generation controls', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows Generate button when no summary exists', async () => {
    renderCard();
    expect(await screen.findByRole('button', { name: /generate/i })).toBeInTheDocument();
  });

  it('disables the generate button while loading', async () => {
    vi.mocked(aiApi.generateTeamSummary).mockImplementation(
      () => new Promise(() => {}), // never resolves
    );
    renderCard();
    const btn = await screen.findByRole('button', { name: /generate/i });
    fireEvent.click(btn);
    await waitFor(() => expect(btn).toBeDisabled());
  });

  it('shows Stop button while loading', async () => {
    vi.mocked(aiApi.generateTeamSummary).mockImplementation(
      () => new Promise(() => {}),
    );
    renderCard();
    fireEvent.click(await screen.findByRole('button', { name: /generate/i }));
    expect(await screen.findByRole('button', { name: /stop/i })).toBeInTheDocument();
  });

  it('hides Stop button after generation completes', async () => {
    vi.mocked(aiApi.generateTeamSummary).mockResolvedValue(mockSummary);
    renderCard();
    fireEvent.click(await screen.findByRole('button', { name: /generate/i }));
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /stop/i })).not.toBeInTheDocument(),
    );
  });
});

describe('AiTeamInsightCard — export menu', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows export button once summary is loaded', async () => {
    vi.mocked(aiApi.generateTeamSummary).mockResolvedValue(mockSummary);
    renderCard();
    fireEvent.click(await screen.findByRole('button', { name: /generate/i }));
    expect(await screen.findByRole('button', { name: /export insight/i })).toBeInTheDocument();
  });

  it('does not show export button before summary exists', async () => {
    renderCard();
    await screen.findByRole('button', { name: /generate/i });
    expect(screen.queryByRole('button', { name: /export insight/i })).not.toBeInTheDocument();
  });
});
