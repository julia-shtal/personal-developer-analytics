import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DiscoverReposModal } from './DiscoverReposModal';

// ------------------------------------------------------------------
// Mock datasourcesApi so tests never hit the network
// ------------------------------------------------------------------
const mockDiscover = vi.fn();
const mockAttach = vi.fn();

vi.mock('@/api/datasources', () => ({
  datasourcesApi: {
    repos: {
      discover: (...args: unknown[]) => mockDiscover(...args),
      attach: (...args: unknown[]) => mockAttach(...args),
    },
  },
}));

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={makeClient()}>
      {children}
    </QueryClientProvider>
  );
}

const TWO_REPOS = [
  { fullName: 'owner/repo-a', private: false, defaultBranch: 'main', alreadyAttached: false },
  { fullName: 'owner/repo-b', private: true,  defaultBranch: 'main', alreadyAttached: false }, // private, not yet attached
  { fullName: 'owner/repo-c', private: false, defaultBranch: 'main', alreadyAttached: true  }, // already attached
];

beforeEach(() => {
  vi.clearAllMocks();
  mockDiscover.mockResolvedValue({ data: TWO_REPOS, headers: {} });
  mockAttach.mockResolvedValue({ data: {} });
});

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('DiscoverReposModal', () => {
  it('renders the repo list after loading', async () => {
    render(<DiscoverReposModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(screen.getByText('owner/repo-a')).toBeInTheDocument();
      expect(screen.getByText('owner/repo-b')).toBeInTheDocument();
      expect(screen.getByText('owner/repo-c')).toBeInTheDocument();
    });

    expect(screen.getByText('✓ Attached')).toBeInTheDocument();
    expect(screen.getByText('private')).toBeInTheDocument();
  });

  it('already-attached repo checkbox is disabled', async () => {
    render(<DiscoverReposModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select owner/repo-c'));

    expect(screen.getByLabelText('Select owner/repo-c')).toBeDisabled();
    expect(screen.getByLabelText('Select owner/repo-a')).not.toBeDisabled();
  });

  it('selecting a repo updates the count on the Add button', async () => {
    const user = userEvent.setup();
    render(<DiscoverReposModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select owner/repo-a'));
    await user.click(screen.getByLabelText('Select owner/repo-a'));

    expect(screen.getByRole('button', { name: /Add \(1\)/i })).toBeInTheDocument();
  });

  it('confirming N selections fires N attach calls then closes', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();

    // Add a second unattached repo so we can select 2
    mockDiscover.mockResolvedValue({
      data: [
        { fullName: 'owner/repo-a', private: false, defaultBranch: 'main', alreadyAttached: false },
        { fullName: 'owner/repo-c', private: false, defaultBranch: 'main', alreadyAttached: false },
      ],
      headers: {},
    });

    render(<DiscoverReposModal dsId={1} onClose={onClose} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select owner/repo-a'));
    await user.click(screen.getByLabelText('Select owner/repo-a'));
    await user.click(screen.getByLabelText('Select owner/repo-c'));
    await user.click(screen.getByRole('button', { name: /Add \(2\)/i }));

    await waitFor(() => {
      expect(mockAttach).toHaveBeenCalledTimes(2);
      expect(mockAttach).toHaveBeenCalledWith(1, 'owner/repo-a', false);
      expect(mockAttach).toHaveBeenCalledWith(1, 'owner/repo-c', false);
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('shows error banner when attach fails', async () => {
    mockAttach.mockRejectedValue({
      response: { data: { message: 'Repo limit reached' } },
    });
    const user = userEvent.setup();
    render(<DiscoverReposModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select owner/repo-a'));
    await user.click(screen.getByLabelText('Select owner/repo-a'));
    await user.click(screen.getByRole('button', { name: /Add \(1\)/i }));

    await waitFor(() =>
      expect(screen.getByText('Repo limit reached')).toBeInTheDocument()
    );
  });

  it('pressing Escape calls onClose', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<DiscoverReposModal dsId={1} onClose={onClose} />, { wrapper: Wrapper });

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('shows rate-limit warning banner when X-Discovery-Truncated is true', async () => {
    mockDiscover.mockResolvedValue({
      data: TWO_REPOS,
      headers: { 'x-discovery-truncated': 'true' },
    });
    render(<DiscoverReposModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() =>
      expect(screen.getByText(/Rate limit reached/)).toBeInTheDocument()
    );
  });
});