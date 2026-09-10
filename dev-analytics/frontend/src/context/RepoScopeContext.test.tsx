import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { RepoDto } from '@/types';
import { RepoScopeProvider, useRepoScope } from './RepoScopeContext';

vi.mock('@/api/repos', () => ({
  reposApi: { list: vi.fn() },
}));

import { reposApi } from '@/api/repos';

const STORAGE_KEY = 'da-repo-scope-v1';
const mockedList = () => reposApi.list as unknown as ReturnType<typeof vi.fn>;

const repo = (id: number): RepoDto => ({ id, name: `repo-${id}` } as RepoDto);

function Consumer() {
  const { repoId, setRepoId } = useRepoScope();
  return (
    <div>
      <span data-testid="value">{repoId ?? 'null'}</span>
      <button onClick={() => setRepoId(42)}>set 42</button>
      <button onClick={() => setRepoId(null)}>clear</button>
    </div>
  );
}

function renderProvider() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <RepoScopeProvider><Consumer /></RepoScopeProvider>
    </QueryClientProvider>
  );
}

describe('RepoScopeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    // Default: the persisted ids used below are all accessible, so these cases
    // exercise storage behaviour without the reconcile interfering.
    mockedList().mockResolvedValue({ data: [repo(5), repo(7), repo(42)] });
  });
  afterEach(() => localStorage.clear());

  it('provides null as the initial repoId when localStorage is empty', () => {
    renderProvider();
    expect(screen.getByTestId('value').textContent).toBe('null');
  });

  it('setRepoId updates the value and persists it to localStorage', async () => {
    const user = userEvent.setup();
    renderProvider();

    await user.click(screen.getByText('set 42'));

    expect(screen.getByTestId('value').textContent).toBe('42');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('42');
  });

  it('setRepoId(null) clears the value and removes the localStorage entry', async () => {
    const user = userEvent.setup();
    localStorage.setItem(STORAGE_KEY, '7');
    renderProvider();
    expect(screen.getByTestId('value').textContent).toBe('7');

    await user.click(screen.getByText('clear'));

    expect(screen.getByTestId('value').textContent).toBe('null');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('reads a previously persisted repoId from localStorage on mount', () => {
    localStorage.setItem(STORAGE_KEY, '5');
    renderProvider();
    expect(screen.getByTestId('value').textContent).toBe('5');
  });

  it('ignores zero and non-numeric values from localStorage', () => {
    localStorage.setItem(STORAGE_KEY, '0');
    renderProvider();
    expect(screen.getByTestId('value').textContent).toBe('null');
  });

  it('useRepoScope throws when rendered outside RepoScopeProvider', () => {
    const Bad = () => { useRepoScope(); return null; };
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Bad />)).toThrow('useRepoScope must be used within RepoScopeProvider');
    spy.mockRestore();
  });
});

describe('RepoScopeContext — stale scope reconciliation', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });
  afterEach(() => localStorage.clear());

  it('clearsPersistedRepoId_whenRepoNoLongerAccessible', async () => {
    localStorage.setItem(STORAGE_KEY, '34');
    mockedList().mockResolvedValue({ data: [repo(41), repo(42)] });

    renderProvider();

    await waitFor(() => expect(screen.getByTestId('value').textContent).toBe('null'));
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('clearsPersistedRepoId_whenUserHasNoAccessibleRepos', async () => {
    localStorage.setItem(STORAGE_KEY, '34');
    mockedList().mockResolvedValue({ data: [] });

    renderProvider();

    await waitFor(() => expect(screen.getByTestId('value').textContent).toBe('null'));
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('keepsPersistedRepoId_whenRepoStillAccessible', async () => {
    localStorage.setItem(STORAGE_KEY, '42');
    mockedList().mockResolvedValue({ data: [repo(41), repo(42)] });

    renderProvider();

    await waitFor(() => expect(mockedList()).toHaveBeenCalled());
    expect(screen.getByTestId('value').textContent).toBe('42');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('42');
  });

  it('keepsPersistedRepoId_whenRepoListRequestFails', async () => {
    localStorage.setItem(STORAGE_KEY, '34');
    mockedList().mockRejectedValue(new Error('network down'));

    renderProvider();

    // An unanswered list is not evidence the repo is gone — a network failure must
    // not silently discard the user's chosen scope.
    await waitFor(() => expect(mockedList()).toHaveBeenCalled());
    expect(screen.getByTestId('value').textContent).toBe('34');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('34');
  });
});
