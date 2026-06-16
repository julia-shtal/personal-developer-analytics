import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RepoScopeProvider } from '@/context/RepoScopeContext';
import { RepoSelector } from './RepoSelector';

vi.mock('@/api/repos', () => ({
  reposApi: {
    list: vi.fn(),
  },
}));

import { reposApi } from '@/api/repos';

const mockList = reposApi.list as ReturnType<typeof vi.fn>;

function Wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <RepoScopeProvider>{children}</RepoScopeProvider>
    </QueryClientProvider>
  );
}

describe('RepoSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders nothing when the repo list is empty', async () => {
    mockList.mockResolvedValue({ data: [] });
    const { container } = render(<RepoSelector />, { wrapper: Wrapper });
    // wait for the query to resolve, then assert the container is empty
    await waitFor(() => expect(mockList).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('shows "all repos" option plus one entry per repo', async () => {
    mockList.mockResolvedValue({
      data: [
        { id: 1, name: 'my-app', teamId: null },
        { id: 2, name: 'shared-lib', teamId: null },
      ],
    });
    render(<RepoSelector />, { wrapper: Wrapper });
    await waitFor(() => screen.getByLabelText('Filter metrics by repository'));

    const select = screen.getByLabelText('Filter metrics by repository') as HTMLSelectElement;
    const options = Array.from(select.options).map((o) => o.text);
    expect(options).toContain('all repos');
    expect(options).toContain('my-app');
    expect(options).toContain('shared-lib');
  });

  it('groups personal and team repos with optgroups when both are present', async () => {
    mockList.mockResolvedValue({
      data: [
        { id: 1, name: 'personal-repo', teamId: null },
        { id: 2, name: 'team-repo', teamId: 99 },
      ],
    });
    render(<RepoSelector />, { wrapper: Wrapper });
    await waitFor(() => screen.getByLabelText('Filter metrics by repository'));

    const select = screen.getByLabelText('Filter metrics by repository');
    const groupLabels = Array.from(select.querySelectorAll('optgroup')).map((g) => g.label);
    expect(groupLabels).toContain('personal');
    expect(groupLabels).toContain('team');
  });

  it('selecting a repo persists the id to localStorage', async () => {
    mockList.mockResolvedValue({
      data: [{ id: 3, name: 'backend', teamId: null }],
    });
    const user = userEvent.setup();
    render(<RepoSelector />, { wrapper: Wrapper });
    await waitFor(() => screen.getByLabelText('Filter metrics by repository'));

    await user.selectOptions(screen.getByLabelText('Filter metrics by repository'), ['3']);

    expect(localStorage.getItem('da-repo-scope-v1')).toBe('3');
  });

  it('selecting "all repos" removes the id from localStorage', async () => {
    localStorage.setItem('da-repo-scope-v1', '3');
    mockList.mockResolvedValue({
      data: [{ id: 3, name: 'backend', teamId: null }],
    });
    const user = userEvent.setup();
    render(<RepoSelector />, { wrapper: Wrapper });
    await waitFor(() => screen.getByLabelText('Filter metrics by repository'));

    await user.selectOptions(screen.getByLabelText('Filter metrics by repository'), ['']);

    expect(localStorage.getItem('da-repo-scope-v1')).toBeNull();
  });
});
