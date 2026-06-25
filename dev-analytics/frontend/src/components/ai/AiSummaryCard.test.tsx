import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AiSummaryCard } from './AiSummaryCard';
import type { MetricsSummaryDto } from '@/types/ai';

const mockGenerate = vi.fn();
const mockLatest = vi.fn();
const mockDownloadFile = vi.fn();

vi.mock('@/api/ai', () => ({
  aiApi: {
    generateSummary: (...args: unknown[]) => mockGenerate(...args),
    latestSummary: () => mockLatest(),
  },
}));

vi.mock('@/lib/export', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/export')>();
  return { ...actual, downloadFile: (...args: unknown[]) => mockDownloadFile(...args) };
});

const SUMMARY: MetricsSummaryDto = {
  from: '2026-06-01',
  to: '2026-06-07',
  scope: 'PERSONAL',
  headline: 'Strong week',
  overview: 'Shipped a lot.',
  insights: [{ kind: 'positive', text: 'Good momentum.', metric: 'commits' }],
  recommendations: ['Keep going.'],
  rawModelOutput: '{}',
  modelName: 'llama3.2',
  generatedAt: '2026-06-08T09:00:00Z',
};

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AiSummaryCard range={{ from: '2026-06-01', to: '2026-06-07' }} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockLatest.mockResolvedValue(null);
});

describe('AiSummaryCard', () => {
  it('shows a visibly disabled Regenerate button and a Stop button while generating, then renders the summary once it resolves', async () => {
    let resolveGenerate!: (v: MetricsSummaryDto) => void;
    mockGenerate.mockReturnValue(
      new Promise<MetricsSummaryDto>((resolve) => {
        resolveGenerate = resolve;
      }),
    );
    renderCard();

    const startButton = await screen.findByRole('button', { name: 'Generate AI Summary' });
    await userEvent.click(startButton);

    const controlsButton = await screen.findByRole('button', { name: 'Generate AI summary' });
    expect(controlsButton).toHaveClass('is-disabled');
    expect(controlsButton).toHaveAttribute('aria-disabled', 'true');
    expect(controlsButton).toBeDisabled();

    expect(screen.getByRole('button', { name: 'Stop generating summary' })).toBeInTheDocument();

    resolveGenerate(SUMMARY);
    expect(await screen.findByText('Strong week')).toBeInTheDocument();
  });

  it('returns to the idle state without showing an error banner when generation is aborted', async () => {
    const cancelError = Object.assign(new Error('canceled'), { __CANCEL__: true, name: 'CanceledError' });
    mockGenerate.mockRejectedValue(cancelError);
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));

    await waitFor(() => {
      expect(screen.queryByText(/temporarily unavailable/i)).not.toBeInTheDocument();
    });
    expect(screen.getByText('No summary generated for this period')).toBeInTheDocument();
  });

  it('clicking Stop mid-generation actually aborts the in-flight request and returns to idle without an error banner', async () => {
    let capturedSignal: AbortSignal | undefined;
    let rejectGenerate!: (err: unknown) => void;
    mockGenerate.mockImplementation((..._args: unknown[]) => {
      const args = _args as [string, string, string | undefined, AbortSignal];
      capturedSignal = args[3];
      return new Promise<MetricsSummaryDto>((_resolve, reject) => {
        rejectGenerate = reject;
      });
    });
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));

    const stopButton = await screen.findByRole('button', { name: 'Stop generating summary' });

    expect(capturedSignal).toBeInstanceOf(AbortSignal);
    expect(capturedSignal?.aborted).toBe(false);

    await userEvent.click(stopButton);

    expect(capturedSignal?.aborted).toBe(true);

    // Settle the pending promise the way Axios would on an aborted request.
    const cancelError = Object.assign(new Error('canceled'), { __CANCEL__: true, name: 'CanceledError' });
    rejectGenerate(cancelError);

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Stop generating summary' })).not.toBeInTheDocument();
    });
    expect(screen.queryByText(/temporarily unavailable/i)).not.toBeInTheDocument();
    expect(screen.getByText('No summary generated for this period')).toBeInTheDocument();
  });

  it('shows the error banner with a Retry affordance when generation fails for a reason other than cancellation', async () => {
    mockGenerate.mockRejectedValue(new Error('Ollama is unreachable'));
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));

    expect(await screen.findByText('AI summary is temporarily unavailable. Metrics remain accessible.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('renders the empty state with the call-to-action before any generation is triggered', async () => {
    renderCard();

    expect(await screen.findByText('No summary generated for this period')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Generate AI Summary' })).toBeInTheDocument();
    expect(screen.queryByText('AI summary is temporarily unavailable. Metrics remain accessible.')).not.toBeInTheDocument();
  });

  it('exports the loaded summary as Markdown via the export menu and triggers a file download', async () => {
    mockGenerate.mockResolvedValue(SUMMARY);
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));
    await screen.findByText('Strong week');

    await userEvent.click(screen.getByLabelText('Export summary'));
    await userEvent.click(await screen.findByRole('button', { name: /Markdown/i }));

    expect(mockDownloadFile).toHaveBeenCalledWith(
      expect.stringMatching(/^ai-summary-2026-06-07\.md$/),
      expect.any(String),
      'text/markdown',
    );
  });

  it('renders the explanation text beneath an anomalous insight when the field is present', async () => {
    const summaryWithExplanation: MetricsSummaryDto = {
      ...SUMMARY,
      insights: [
        {
          kind: 'risk',
          text: 'Churn ratio spiked.',
          metric: 'Churn Ratio',
          explanation: 'A large refactoring commit drove the spike.',
        },
      ],
    };
    mockGenerate.mockResolvedValue(summaryWithExplanation);
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));

    expect(await screen.findByText('Churn ratio spiked.')).toBeInTheDocument();
    expect(screen.getByText('A large refactoring commit drove the spike.')).toBeInTheDocument();
  });

  it('does not render an explanation element when the field is absent', async () => {
    mockGenerate.mockResolvedValue(SUMMARY);
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Generate AI Summary' }));

    await screen.findByText('Good momentum.');
    expect(screen.queryByText(/large refactoring/i)).not.toBeInTheDocument();
  });
});
