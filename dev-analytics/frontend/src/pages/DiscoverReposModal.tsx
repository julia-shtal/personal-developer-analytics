import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { X, BookOpen, AlertTriangle } from 'lucide-react';
import { datasourcesApi } from '@/api/datasources';
import { Chip } from '@/components/ui/Chip';
import type { DiscoveredRepoDto } from '@/types';

interface Props {
  dsId: number;
  onClose: () => void;
}

export function DiscoverReposModal({ dsId, onClose }: Props) {
  const qc = useQueryClient();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    closeRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['discover-repos', dsId],
    queryFn: async () => {
      const res = await datasourcesApi.repos.discover(dsId);
      return {
        repos: res.data,
        truncated: res.headers['x-discovery-truncated'] === 'true',
      };
    },
  });

  function toggle(fullName: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(fullName)) { next.delete(fullName); } else { next.add(fullName); }
      return next;
    });
  }

  async function handleConfirm() {
    if (selected.size === 0 || isSubmitting) return;
    setIsSubmitting(true);
    setError('');
    try {
      for (const name of [...selected]) {
        await datasourcesApi.repos.attach(dsId, name, false);
      }
      qc.invalidateQueries({ queryKey: ['repos', dsId] });
      qc.invalidateQueries({ queryKey: ['datasources'] });
      onClose();
    } catch (err) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Failed to attach one or more repositories.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 40,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 16,
      }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="discover-repos-title"
        style={{
          background: 'var(--bg-card)',
          border: '1px solid var(--line)',
          borderRadius: 12,
          width: '100%', maxWidth: 480,
          maxHeight: '80vh',
          display: 'flex', flexDirection: 'column',
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
        }}
      >
        {/* Header */}
        <div className="row" style={{ padding: '16px 20px', borderBottom: '1px solid var(--line-2)', justifyContent: 'space-between' }}>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 2 }}>── github</div>
            <h2 id="discover-repos-title" style={{ fontWeight: 500, fontSize: 15, color: 'var(--fg)', margin: 0 }}>
              Add repositories
            </h2>
          </div>
          <button ref={closeRef} onClick={onClose} className="btn btn-ghost btn-icon" aria-label="Close">
            <X width={14} height={14} />
          </button>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px 20px', minHeight: 0 }}>
          {isLoading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
              <span className="t-label" style={{ color: 'var(--violet)' }}>Discovering repositories…</span>
            </div>
          )}
          {isError && (
            <p className="t-label" style={{ color: 'var(--coral)', padding: '16px 0', textAlign: 'center' }}>
              Failed to load repositories. Check your token or network.
            </p>
          )}
          {data?.truncated && (
            <div className="row gap-2" style={{
              fontSize: 11, color: 'var(--amber)',
              background: 'var(--amber-bg)',
              border: '1px solid color-mix(in oklab, var(--amber) 25%, var(--line))',
              borderRadius: 6, padding: '8px 12px', marginBottom: 10,
            }}>
              <AlertTriangle width={12} height={12} style={{ flexShrink: 0 }} />
              Rate limit reached — showing partial results only.
            </div>
          )}
          {data && data.repos.length === 0 && (
            <p className="t-label" style={{ padding: '16px 0', textAlign: 'center' }}>
              No repositories found for this token.
            </p>
          )}
          {data && data.repos.length > 0 && (
            <div className="col gap-1">
              {data.repos.map((repo: DiscoveredRepoDto) => {
                const isAttached = repo.alreadyAttached;
                const isSelected = selected.has(repo.fullName);
                return (
                  <div
                    key={repo.fullName}
                    style={{
                      padding: '8px 12px', borderRadius: 6,
                      border: `1px solid ${isSelected ? 'var(--violet)' : 'var(--line-2)'}`,
                      background: isSelected ? 'var(--violet-bg)' : isAttached ? 'var(--bg-2)' : 'var(--bg-card)',
                      opacity: isAttached ? 0.6 : 1,
                      cursor: isAttached ? 'default' : 'pointer',
                      display: 'flex', alignItems: 'center', gap: 10,
                    }}
                    onClick={() => !isAttached && toggle(repo.fullName)}
                  >
                    <input
                      type="checkbox"
                      checked={isAttached || isSelected}
                      disabled={isAttached}
                      onChange={() => !isAttached && toggle(repo.fullName)}
                      onClick={(e) => e.stopPropagation()}
                      style={{ flexShrink: 0, accentColor: 'var(--violet)', width: 14, height: 14 }}
                      aria-label={`Select ${repo.fullName}`}
                    />
                    <BookOpen width={12} height={12} style={{ color: 'var(--fg-3)', flexShrink: 0 }} />
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--fg)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {repo.fullName}
                    </span>
                    {isAttached ? (
                      <Chip color="emerald">attached</Chip>
                    ) : repo.private ? (
                      <span className="t-label" style={{ fontSize: 10, flexShrink: 0 }}>private</span>
                    ) : null}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="row" style={{ padding: '14px 20px', borderTop: '1px solid var(--line-2)', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            {error ? (
              <span className="t-label" style={{ color: 'var(--coral)', fontSize: 11 }}>{error}</span>
            ) : (
              <span className="t-label" style={{ fontSize: 11 }}>
                {selected.size > 0 ? `${selected.size} selected` : 'Select repositories to add'}
              </span>
            )}
          </div>
          <div className="row gap-2" style={{ flexShrink: 0 }}>
            <button className="btn btn-ghost" onClick={onClose} disabled={isSubmitting}>cancel</button>
            <button
              className="btn btn-accent"
              onClick={handleConfirm}
              disabled={selected.size === 0 || isSubmitting}
            >
              {isSubmitting ? 'adding…' : selected.size > 0 ? `add (${selected.size})` : 'add'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
