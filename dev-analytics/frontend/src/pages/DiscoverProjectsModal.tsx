import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { clsx } from 'clsx';
import { datasourcesApi } from '@/api/datasources';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import type { DiscoveredProjectDto } from '@/types';

interface Props {
  dsId: number;
  onClose: () => void;
}

export function DiscoverProjectsModal({ dsId, onClose }: Props) {
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

  const { data: projects, isLoading, isError } = useQuery({
    queryKey: ['discover-projects', dsId],
    queryFn: () => datasourcesApi.projects.discover(dsId).then((r) => r.data),
  });

  function toggle(projectKey: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(projectKey) ? next.delete(projectKey) : next.add(projectKey);
      return next;
    });
  }

  async function handleConfirm() {
    if (selected.size === 0 || isSubmitting) return;
    setIsSubmitting(true);
    setError('');
    try {
      for (const key of [...selected]) {
        const proj = projects?.find((p) => p.projectKey === key);
        await datasourcesApi.projects.attach(dsId, key, proj?.projectName);
      }
      qc.invalidateQueries({ queryKey: ['jira-projects', dsId] });
      onClose();
    } catch (err) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Failed to attach one or more projects.');
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
        aria-labelledby="discover-projects-title"
        className="bg-white rounded-xl shadow-2xl w-full max-w-md max-h-[80vh] flex flex-col"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h2 id="discover-projects-title" className="text-sm font-semibold text-gray-900">
            Add Jira projects
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
              Failed to load projects. Check your token or network.
            </p>
          )}
          {projects && projects.length === 0 && (
            <p className="text-sm text-gray-400 py-4 text-center">
              No projects found for this token.
            </p>
          )}
          {projects && projects.length > 0 && (
            <ul className="space-y-1.5">
              {projects.map((proj: DiscoveredProjectDto) => (
                <li
                  key={proj.projectKey}
                  className={clsx(
                    'rounded-md px-3 py-2.5 flex items-center gap-3 transition-colors',
                    proj.alreadyAttached
                      ? 'bg-gray-50 opacity-60'
                      : clsx(
                          'border cursor-pointer',
                          selected.has(proj.projectKey)
                            ? 'border-blue-400 bg-blue-50'
                            : 'border-gray-100 bg-white hover:border-blue-200'
                        )
                  )}
                  onClick={() => !proj.alreadyAttached && toggle(proj.projectKey)}
                >
                  <input
                    type="checkbox"
                    checked={proj.alreadyAttached || selected.has(proj.projectKey)}
                    disabled={proj.alreadyAttached}
                    onChange={() => !proj.alreadyAttached && toggle(proj.projectKey)}
                    onClick={(e) => e.stopPropagation()}
                    className="h-4 w-4 rounded accent-blue-600 flex-shrink-0"
                    aria-label={`Select ${proj.projectKey}`}
                  />
                  <span className="text-xs font-mono font-semibold text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded flex-shrink-0">
                    {proj.projectKey}
                  </span>
                  <span className="text-xs text-gray-700 truncate flex-1">
                    {proj.projectName}
                  </span>
                  {proj.alreadyAttached && (
                    <Badge color="emerald" className="text-[10px] px-1.5 py-0 flex-shrink-0">
                      ✓ Attached
                    </Badge>
                  )}
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
                {selected.size > 0 ? `${selected.size} selected` : 'Select projects to add'}
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
