type Listener = (repoId: number) => void;

/** Where the chosen repo scope is persisted. Shared so only one module defines it. */
export const REPO_SCOPE_STORAGE_KEY = 'da-repo-scope-v1';

const listeners = new Set<Listener>();
const resetListeners = new Set<() => void>();

interface FailedRequest {
  response?: { status?: number };
  config?: { params?: Record<string, unknown> };
}

/**
 * The repo scope a failed request carried, when the answer proves that scope is gone.
 *
 * Only 404 qualifies. A rate-limited or unanswered request reports the state of the
 * connection, not of the repository, and acting on it would discard a valid scope
 * during the very outage that produced it.
 */
export function rejectedRepoScope(error: FailedRequest): number | null {
  if (error?.response?.status !== 404) return null;
  const scoped = Number(error.config?.params?.repoId);
  return Number.isFinite(scoped) && scoped > 0 ? scoped : null;
}

/** Subscribe to repo scopes the server has rejected. Returns an unsubscribe function. */
export function onRepoScopeRejected(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function notifyRepoScopeRejected(repoId: number): void {
  for (const listener of listeners) listener(repoId);
}

/** Subscribe to the scope being reset for a new session. Returns an unsubscribe function. */
export function onRepoScopeReset(listener: () => void): () => void {
  resetListeners.add(listener);
  return () => {
    resetListeners.delete(listener);
  };
}

/**
 * Drop the persisted scope and tell any mounted provider to forget it.
 *
 * Storage is only half the state — a provider already mounted holds the id in memory and
 * would keep sending it. Both are cleared so this works whether or not one is mounted.
 */
export function clearRepoScope(): void {
  localStorage.removeItem(REPO_SCOPE_STORAGE_KEY);
  for (const listener of resetListeners) listener();
}
