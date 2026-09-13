import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  REPO_SCOPE_STORAGE_KEY,
  clearRepoScope,
  notifyRepoScopeRejected,
  onRepoScopeRejected,
  onRepoScopeReset,
  rejectedRepoScope,
} from './repoScopeSignal';

describe('rejectedRepoScope', () => {
  it('reports the repo id when a repo-scoped request is answered with 404', () => {
    expect(rejectedRepoScope({ response: { status: 404 }, config: { params: { repoId: 35 } } }))
      .toBe(35);
  });

  it('reports nothing when a repo-scoped request is rate limited', () => {
    // A 429 says the budget ran out, not that the repository is gone. Treating it as
    // evidence would discard a valid scope during exactly the storm that provoked it.
    expect(rejectedRepoScope({ response: { status: 429 }, config: { params: { repoId: 35 } } }))
      .toBeNull();
  });

  it('reports nothing when an unscoped request is answered with 404', () => {
    expect(rejectedRepoScope({ response: { status: 404 }, config: { params: {} } })).toBeNull();
  });

  it('reports nothing when the request failed without a response', () => {
    expect(rejectedRepoScope({ config: { params: { repoId: 35 } } })).toBeNull();
  });
});

describe('onRepoScopeRejected', () => {
  it('delivers the rejected repo id to subscribers', () => {
    const seen = vi.fn();
    const unsubscribe = onRepoScopeRejected(seen);

    notifyRepoScopeRejected(35);

    expect(seen).toHaveBeenCalledWith(35);
    unsubscribe();
  });

  it('stops delivering once the subscriber unsubscribes', () => {
    const seen = vi.fn();
    onRepoScopeRejected(seen)();

    notifyRepoScopeRejected(35);

    expect(seen).not.toHaveBeenCalled();
  });
});

describe('clearRepoScope', () => {
  afterEach(() => localStorage.clear());

  it('removes the persisted scope', () => {
    localStorage.setItem(REPO_SCOPE_STORAGE_KEY, '35');

    clearRepoScope();

    expect(localStorage.getItem(REPO_SCOPE_STORAGE_KEY)).toBeNull();
  });

  it('notifies subscribers so a mounted provider forgets the scope too', () => {
    // Storage is only half the state: a provider already mounted holds the id in memory
    // and would keep sending it until something told it to let go.
    const seen = vi.fn();
    const unsubscribe = onRepoScopeReset(seen);

    clearRepoScope();

    expect(seen).toHaveBeenCalled();
    unsubscribe();
  });

  it('stops notifying once the subscriber unsubscribes', () => {
    const seen = vi.fn();
    onRepoScopeReset(seen)();

    clearRepoScope();

    expect(seen).not.toHaveBeenCalled();
  });
});
