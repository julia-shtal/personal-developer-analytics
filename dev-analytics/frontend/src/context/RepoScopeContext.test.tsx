import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RepoScopeProvider, useRepoScope } from './RepoScopeContext';

const STORAGE_KEY = 'da-repo-scope-v1';

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

describe('RepoScopeContext', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('provides null as the initial repoId when localStorage is empty', () => {
    render(<RepoScopeProvider><Consumer /></RepoScopeProvider>);
    expect(screen.getByTestId('value').textContent).toBe('null');
  });

  it('setRepoId updates the value and persists it to localStorage', async () => {
    const user = userEvent.setup();
    render(<RepoScopeProvider><Consumer /></RepoScopeProvider>);

    await user.click(screen.getByText('set 42'));

    expect(screen.getByTestId('value').textContent).toBe('42');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('42');
  });

  it('setRepoId(null) clears the value and removes the localStorage entry', async () => {
    const user = userEvent.setup();
    localStorage.setItem(STORAGE_KEY, '7');
    render(<RepoScopeProvider><Consumer /></RepoScopeProvider>);
    expect(screen.getByTestId('value').textContent).toBe('7');

    await user.click(screen.getByText('clear'));

    expect(screen.getByTestId('value').textContent).toBe('null');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('reads a previously persisted repoId from localStorage on mount', () => {
    localStorage.setItem(STORAGE_KEY, '5');
    render(<RepoScopeProvider><Consumer /></RepoScopeProvider>);
    expect(screen.getByTestId('value').textContent).toBe('5');
  });

  it('ignores zero and non-numeric values from localStorage', () => {
    localStorage.setItem(STORAGE_KEY, '0');
    render(<RepoScopeProvider><Consumer /></RepoScopeProvider>);
    expect(screen.getByTestId('value').textContent).toBe('null');
  });

  it('useRepoScope throws when rendered outside RepoScopeProvider', () => {
    const Bad = () => { useRepoScope(); return null; };
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Bad />)).toThrow('useRepoScope must be used within RepoScopeProvider');
    spy.mockRestore();
  });
});
