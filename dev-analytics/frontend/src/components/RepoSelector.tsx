import { useQuery } from '@tanstack/react-query';
import { ChevronDown } from 'lucide-react';
import { reposApi } from '@/api/repos';
import { useRepoScope } from '@/context/RepoScopeContext';

export function RepoSelector() {
  const { repoId, setRepoId } = useRepoScope();

  const { data: allRepos } = useQuery({
    queryKey: ['repos'],
    queryFn: () => reposApi.list().then((r) => r.data),
    staleTime: 5 * 60_000,
  });

  const repos = allRepos ?? [];

  if (repos.length === 0) return null;

  const personal = repos.filter((r) => !r.teamId);
  const team = repos.filter((r) => r.teamId != null);
  return (
    <div style={{ position: 'relative', display: 'inline-block' }}>
      <select
        value={repoId ?? ''}
        onChange={(e) => setRepoId(e.target.value === '' ? null : Number(e.target.value))}
        aria-label="Filter metrics by repository"
        style={{
          appearance: 'none',
          background: 'var(--bg-2)',
          border: '1px solid var(--line)',
          borderRadius: 6,
          color: 'var(--fg)',
          fontFamily: 'var(--font-mono)',
          fontSize: 11,
          padding: '5px 28px 5px 10px',
          cursor: 'pointer',
          outline: 'none',
          minWidth: 140,
        }}
      >
        <option value="">all repos</option>
        {personal.length > 0 && team.length > 0 ? (
          <>
            <optgroup label="personal">
              {personal.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
            </optgroup>
            <optgroup label="team">
              {team.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
            </optgroup>
          </>
        ) : (
          repos.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)
        )}
      </select>
      <ChevronDown
        width={11}
        height={11}
        style={{
          position: 'absolute',
          right: 8,
          top: '50%',
          transform: 'translateY(-50%)',
          pointerEvents: 'none',
          color: 'var(--fg-3)',
        }}
      />
    </div>
  );
}