import { createContext, useContext, useState, type ReactNode } from 'react';

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
