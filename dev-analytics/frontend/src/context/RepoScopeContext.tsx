import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reposApi } from '@/api/repos';

const STORAGE_KEY = 'da-repo-scope-v1';

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

  // The persisted id outlives the repo it points at: deleting a data source,
  // unsubscribing, losing team access, or recreating the database all leave a scope
  // that no longer resolves. Nothing downstream notices — RepoSelector finds no
  // matching option and falls back to displaying "all repos", while every metric
  // request still carries the dead id and comes back 404. Drop the scope as soon as
  // the repo list proves the id is gone, so the UI and the requests agree again.
  //
  // Only a loaded list is evidence: `repos` stays undefined while the query is in
  // flight or has failed, and clearing on that would discard a valid scope whenever
  // the network hiccups. An empty array is a real answer — no accessible repos — and
  // does clear.
  useEffect(() => {
    if (repoId != null && repos && !repos.some((r) => r.id === repoId)) {
      setRepoId(null);
    }
  }, [repoId, repos]);

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
