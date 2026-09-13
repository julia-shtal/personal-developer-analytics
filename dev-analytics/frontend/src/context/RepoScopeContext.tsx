import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reposApi } from '@/api/repos';
import { REPO_SCOPE_STORAGE_KEY, onRepoScopeRejected, onRepoScopeReset } from '@/lib/repoScopeSignal';

const STORAGE_KEY = REPO_SCOPE_STORAGE_KEY;

interface RepoScopeContextValue {
  repoId: number | null;
  setRepoId: (id: number | null) => void;
}

const RepoScopeContext = createContext<RepoScopeContextValue | null>(null);

function readFromStorage(): number | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : null;
}

export function RepoScopeProvider({ children }: { children: ReactNode }) {
  const [repoId, setRepoIdState] = useState<number | null>(readFromStorage);

  // Same key and staleTime as RepoSelector, so the two share one cached response
  // rather than each fetching the list.
  const { data: repos } = useQuery({
    queryKey: ['repos'],
    queryFn: () => reposApi.list().then((r) => r.data),
    staleTime: 5 * 60_000,
  });

  // A persisted id can outlive the repo it points at, leaving the selector showing
  // "all repos" while every metric request still carries the dead id and 404s. Drop the
  // scope once the repo list proves the id is gone.
  //
  // Only a loaded list counts as evidence: `repos` is undefined while in flight or failed,
  // and clearing on that would discard a valid scope. An empty array is a real answer.
  useEffect(() => {
    if (repoId != null && repos && !repos.some((r) => r.id === repoId)) {
      setRepoId(null);
    }
  }, [repoId, repos]);

  // The list is unavailable in exactly the cases that strand a dead id — a rate-limited or
  // failed fetch leaves the effect above with nothing to judge, so the scope survives and
  // every request it taints 404s. A 404 naming the scope is the evidence that list cannot give.
  useEffect(
    () => onRepoScopeRejected((rejected) => {
      if (rejected === repoId) setRepoId(null);
    }),
    [repoId]
  );

  // A new session owns its own scope. The storage entry is already gone by the time this
  // runs; this drops the copy held in memory.
  useEffect(() => onRepoScopeReset(() => setRepoIdState(null)), []);

  function setRepoId(id: number | null) {
    setRepoIdState(id);
    if (id == null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, String(id));
    }
  }

  return (
    <RepoScopeContext.Provider value={{ repoId, setRepoId }}>
      {children}
    </RepoScopeContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useRepoScope(): RepoScopeContextValue {
  const ctx = useContext(RepoScopeContext);
  if (!ctx) throw new Error('useRepoScope must be used within RepoScopeProvider');
  return ctx;
}
