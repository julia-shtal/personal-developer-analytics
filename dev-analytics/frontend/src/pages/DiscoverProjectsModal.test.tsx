import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DiscoverProjectsModal } from './DiscoverProjectsModal';

const mockDiscover = vi.fn();
const mockAttach = vi.fn();

vi.mock('@/api/datasources', () => ({
  datasourcesApi: {
    projects: {
      discover: (...args: unknown[]) => mockDiscover(...args),
      attach: (...args: unknown[]) => mockAttach(...args),
    },
  },
}));

function Wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const THREE_PROJECTS = [
  { projectKey: 'PDA', projectName: 'Personal Dev Analytics', alreadyAttached: false },
  { projectKey: 'BACK', projectName: 'Backend',               alreadyAttached: false },
  { projectKey: 'DONE', projectName: 'Already Done',          alreadyAttached: true  },
];

beforeEach(() => {
  vi.clearAllMocks();
  mockDiscover.mockResolvedValue({ data: THREE_PROJECTS });
  mockAttach.mockResolvedValue({ data: {} });
});

describe('DiscoverProjectsModal', () => {
  it('renders the project list after loading', async () => {
    render(<DiscoverProjectsModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(screen.getByText('PDA')).toBeInTheDocument();
      expect(screen.getByText('Personal Dev Analytics')).toBeInTheDocument();
      expect(screen.getByText('DONE')).toBeInTheDocument();
    });

    expect(screen.getByText('✓ Attached')).toBeInTheDocument();
  });

  it('already-attached project checkbox is disabled', async () => {
    render(<DiscoverProjectsModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select DONE'));

    expect(screen.getByLabelText('Select DONE')).toBeDisabled();
    expect(screen.getByLabelText('Select PDA')).not.toBeDisabled();
  });

  it('selecting projects updates the Add button count', async () => {
    const user = userEvent.setup();
    render(<DiscoverProjectsModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select PDA'));
    await user.click(screen.getByLabelText('Select PDA'));
    await user.click(screen.getByLabelText('Select BACK'));

    expect(screen.getByRole('button', { name: /Add \(2\)/i })).toBeInTheDocument();
  });

  it('confirming calls attach for each selected project then closes', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<DiscoverProjectsModal dsId={5} onClose={onClose} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select PDA'));
    await user.click(screen.getByLabelText('Select PDA'));
    await user.click(screen.getByRole('button', { name: /Add \(1\)/i }));

    await waitFor(() => {
      expect(mockAttach).toHaveBeenCalledWith(5, 'PDA', 'Personal Dev Analytics');
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('shows error message when attach fails', async () => {
    mockAttach.mockRejectedValue({
      response: { data: { message: 'Project already exists' } },
    });
    const user = userEvent.setup();
    render(<DiscoverProjectsModal dsId={1} onClose={vi.fn()} />, { wrapper: Wrapper });

    await waitFor(() => screen.getByLabelText('Select PDA'));
    await user.click(screen.getByLabelText('Select PDA'));
    await user.click(screen.getByRole('button', { name: /Add \(1\)/i }));

    await waitFor(() =>
      expect(screen.getByText('Project already exists')).toBeInTheDocument()
    );
  });

  it('pressing Escape calls onClose', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<DiscoverProjectsModal dsId={1} onClose={onClose} />, { wrapper: Wrapper });

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });
});