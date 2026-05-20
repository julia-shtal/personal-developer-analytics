import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { X, BookOpen, AlertTriangle } from 'lucide-react';
import { clsx } from 'clsx';
import { datasourcesApi } from '@/api/datasources';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
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
      next.has(fullName) ? next.delete(fullName) : next.add(fullName);
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
      className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="discover-repos-title"
        className="bg-white rounded-xl shadow-2xl w-full max-w-md max-h-[80vh] flex flex-col"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h2 id="discover-repos-title" className="text-sm font-semibold text-gray-900">
            Add repositories
          </h2>
          <button
            ref={closeRef}
            onClick={onClose}
            className="p-1 rounded text-gray-400 hover:text-gray-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-500"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-3 min-h-0">
          {isLoading && (
            <div className="flex justify-center py-8">
              <Spinner size="md" />
            </div>
          )}
          {isError && (
            <p className="text-sm text-red-500 py-4 text-center">
              Failed to load repositories. Check your token or network.
            </p>
          )}
          {data?.truncated && (
            <div className="flex items-center gap-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
              Rate limit reached — showing partial results only.
            </div>
          )}
          {data && data.repos.length === 0 && (
            <p className="text-sm text-gray-400 py-4 text-center">
              No repositories found for this token.
            </p>
          )}
          {data && data.repos.length > 0 && (
            <ul className="space-y-1.5">
              {data.repos.map((repo: DiscoveredRepoDto) => (
                <li
                  key={repo.fullName}
                  className={clsx(
                    'rounded-md px-3 py-2.5 flex items-center gap-3 transition-colors',
                    repo.alreadyAttached
                      ? 'bg-gray-50 opacity-60'
                      : clsx(
                          'border cursor-pointer',
                          selected.has(repo.fullName)
                            ? 'border-violet-400 bg-violet-50'
                            : 'border-gray-100 bg-white hover:border-violet-200'
                        )
                  )}
                  onClick={() => !repo.alreadyAttached && toggle(repo.fullName)}
                >
                  <input
                    type="checkbox"
                    checked={repo.alreadyAttached || selected.has(repo.fullName)}
                    disabled={repo.alreadyAttached}
                    onChange={() => !repo.alreadyAttached && toggle(repo.fullName)}
                    onClick={(e) => e.stopPropagation()}
                    className="h-4 w-4 rounded accent-violet-600 flex-shrink-0"
                    aria-label={`Select ${repo.fullName}`}
                  />
                  <BookOpen className="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
                  <span className="text-xs font-medium text-gray-800 truncate flex-1">
                    {repo.fullName}
                  </span>
                  {repo.alreadyAttached ? (
                    <Badge color="emerald" className="text-[10px] px-1.5 py-0 flex-shrink-0">
                      ✓ Attached
                    </Badge>
                  ) : repo.private ? (
                    <Badge color="gray" className="text-[10px] px-1.5 py-0 flex-shrink-0">
                      private
                    </Badge>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-t border-gray-100">
          <div className="flex-1 min-w-0">
            {error ? (
              <p className="text-xs text-red-500 truncate">{error}</p>
            ) : (
              <span className="text-xs text-gray-400">
                {selected.size > 0 ? `${selected.size} selected` : 'Select repositories to add'}
              </span>
            )}
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <Button variant="secondary" size="sm" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button
              size="sm"
              onClick={handleConfirm}
              disabled={selected.size === 0 || isSubmitting}
              loading={isSubmitting}
            >
              {selected.size > 0 ? `Add (${selected.size})` : 'Add'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}