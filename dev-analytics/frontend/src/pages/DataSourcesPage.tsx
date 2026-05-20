import { useState, useEffect, type FormEvent } from 'react';
import { clsx } from 'clsx';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Trash2, Database, GitBranch, Layers, AlertCircle,
  ChevronDown, ChevronRight, BookOpen, ExternalLink, UserCheck, UserMinus, Play,
  Eye, EyeOff, MessageSquare,
} from 'lucide-react';
import { datasourcesApi, type SyncStatus } from '@/api/datasources';
import { issuesApi } from '@/api/issues';
import { reposApi } from '@/api/repos';
import { DiscoverReposModal } from './DiscoverReposModal';
import { DiscoverProjectsModal } from './DiscoverProjectsModal';
import { teamsApi } from '@/api/teams';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { PageSpinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import type { DataSourceType, CreateDataSourceRequest, RepoDto, Team, TrackedJiraProjectDto } from '@/types';

// ─── Type helpers ─────────────────────────────────────────────────────────────

const TYPE_LABELS: Record<DataSourceType, string> = {
  GIT_LOCAL: 'Local Git',
  GITHUB: 'GitHub',
  JIRA: 'Jira',
};

const TYPE_COLORS: Record<DataSourceType, 'gray' | 'violet' | 'blue'> = {
  GIT_LOCAL: 'gray',
  GITHUB: 'violet',
  JIRA: 'blue',
};

function TypeIcon({ type }: { type: DataSourceType }) {
  if (type === 'GITHUB') return <GitBranch className="h-5 w-5" />;
  if (type === 'GIT_LOCAL') return <Layers className="h-5 w-5" />;
  return <Database className="h-5 w-5" />;
}

const TYPE_OPTIONS = [
  { value: 'GIT_LOCAL', label: 'Local Git repository' },
  { value: 'GITHUB',    label: 'GitHub' },
  { value: 'JIRA',      label: 'Jira' },
];

// Types that need a base URL (the remote API address)
const NEEDS_BASEURL: DataSourceType[] = ['GITHUB', 'JIRA'];
// Types that need a local filesystem path
const NEEDS_PATH: DataSourceType[] = ['GIT_LOCAL'];
// Types that need an API token
const NEEDS_TOKEN: DataSourceType[] = ['GITHUB', 'JIRA'];
// Types that support repoFullName auto-registration
const NEEDS_REPO_FULLNAME: DataSourceType[] = ['GITHUB'];

// ─── Sync progress display ────────────────────────────────────────────────────

function fmtSeconds(s: number): string {
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60), sec = s % 60;
  return sec > 0 ? `${m}m ${sec}s` : `${m}m`;
}

function fmtEta(eta: number | null): string {
  if (eta === null || eta < 0) return '';
  if (eta === 0) return 'almost done';
  if (eta < 60) return `~${eta}s left`;
  if (eta < 3600) return `~${Math.ceil(eta / 60)} min left`;
  return `~${(eta / 3600).toFixed(1)} hr left`;
}

function SyncProgressLine({ status }: { status: SyncStatus | undefined }) {
  if (!status || status.phase === 'starting') {
    return <span className="text-xs text-violet-500 animate-pulse">Syncing — starting…</span>;
  }

  const isKnownPhase = status.phase && status.phase !== 'starting';
  const phaseLabel = isKnownPhase
    ? status.totalPhases > 1
      ? `${status.phase} (phase ${status.phaseNumber}/${status.totalPhases})`
      : status.phase
    : null;

  const countStr = status.phaseProcessed > 0
    ? status.phaseTotal > 0
      ? `${status.phaseProcessed.toLocaleString()} / ~${status.phaseTotal.toLocaleString()}`
      : status.phaseProcessed.toLocaleString()
    : null;

  // Prefer overall ETA (which accounts for future phases), fall back to phase ETA.
  const etaStr = fmtEta(status.overallEtaSeconds ?? status.phaseEtaSeconds);

  // Completed phases history — shown as "commits: 5,432 (2m 10s)"
  const history = status.completedPhases.map(p =>
    `${p.name}: ${p.itemsSaved.toLocaleString()} (${fmtSeconds(p.durationSeconds)})`
  ).join(' → ');

  const elapsed = status.elapsedSeconds > 0 ? fmtSeconds(status.elapsedSeconds) : null;

  const mainLine = [phaseLabel, countStr].filter(Boolean).join(' — ');
  const timeLine = etaStr || (elapsed ? `${elapsed} elapsed` : '');

  return (
    <span className="text-xs text-violet-500 animate-pulse">
      {`Syncing: ${mainLine}${timeLine ? ` (${timeLine})` : ''}`}
      {history && (
        <span className="block text-xs text-gray-400 mt-0.5 not-italic" style={{ animationName: 'none' }}>
          Done: {history}
        </span>
      )}
    </span>
  );
}

// ─── Sync age formatter ───────────────────────────────────────────────────────

function formatSyncAge(dateStr?: string): string {
  if (!dateStr) return 'Never synced';
  const date = new Date(dateStr);
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'Just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h ago`;
  return `${Math.floor(diffH / 24)}d ago`;
}

function syncDotClass(dateStr?: string): string {
  if (!dateStr) return 'bg-red-400';
  const diffH = (Date.now() - new Date(dateStr).getTime()) / 3600000;
  if (diffH < 24) return 'bg-emerald-400';
  if (diffH < 168) return 'bg-amber-400'; // 7 days
  return 'bg-red-400';
}

function SyncStatusLine({ dateStr }: { dateStr?: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={clsx('inline-block w-1.5 h-1.5 rounded-full flex-shrink-0', syncDotClass(dateStr))} />
      {dateStr ? `Synced ${formatSyncAge(dateStr)}` : 'Never synced'}
    </span>
  );
}

// ─── Repos sub-panel ──────────────────────────────────────────────────────────

function RepoIssuesSection({ repo, isGitHub }: { repo: RepoDto; isGitHub: boolean }) {
  const qc = useQueryClient();
  const [enabled, setEnabled] = useState(repo.collectIssues);

  // Keep local state in sync when the server value changes (e.g. after refetch).
  useEffect(() => {
    setEnabled(repo.collectIssues);
  }, [repo.collectIssues]);

  const { data: counts } = useQuery({
    queryKey: ['issue-count', repo.id],
    queryFn: () => issuesApi.getCount(repo.id).then((r) => r.data),
    enabled: isGitHub && enabled,
    // Poll while issues are enabled but haven't been synced yet, so the count
    // updates automatically once the async collection finishes.
    refetchInterval: enabled && !repo.issuesLastSyncedAt ? 5_000 : false,
  });

  const toggleMutation = useMutation({
    mutationFn: (next: boolean) => reposApi.setCollectIssues(repo.id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos'] });
      qc.invalidateQueries({ queryKey: ['issue-count', repo.id] });
    },
    onError: () => setEnabled(repo.collectIssues), // roll back on failure
  });

  if (!isGitHub) return null;

  // `owner/repo` → `https://github.com/owner/repo/issues`
  const issuesUrl = repo.repoUrl ? `${repo.repoUrl}/issues` : undefined;
  // True when tracking just started and no sync has completed yet.
  const isInitialSync = enabled && !repo.issuesLastSyncedAt;

  function handleToggle() {
    const next = !enabled;
    setEnabled(next);
    toggleMutation.mutate(next);
  }

  return (
    <div className="mt-2 pt-2 border-t border-gray-100 flex items-center justify-between gap-3">
      {/* Left: icon + label + count / status */}
      <div className="flex items-center gap-1.5 text-xs min-w-0">
        <MessageSquare className="h-3.5 w-3.5 flex-shrink-0 text-gray-400" />
        <span className="text-gray-500">Issues</span>

        {enabled && (
          isInitialSync && !counts ? (
            <span className="text-violet-400 animate-pulse">— syncing…</span>
          ) : counts ? (
            <span className="text-gray-400">
              {'— '}
              <span className="text-emerald-600 font-medium">{counts.open.toLocaleString()}</span>
              <span> open</span>
              <span className="mx-0.5 text-gray-300">/</span>
              <span>{counts.closed.toLocaleString()} closed</span>
            </span>
          ) : null
        )}
      </div>

      {/* Right: issues link + toggle switch */}
      <div className="flex items-center gap-1 flex-shrink-0">
        <button
          role="switch"
          aria-checked={enabled}
          aria-label={enabled ? 'Disable issue collection' : 'Enable issue collection'}
          onClick={handleToggle}
          disabled={toggleMutation.isPending}
          className={clsx(
            'relative inline-flex h-4 w-7 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent',
            'transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-500 focus-visible:ring-offset-1',
            enabled ? 'bg-violet-500' : 'bg-gray-200',
            toggleMutation.isPending && 'opacity-50 cursor-not-allowed'
          )}
        >
          <span
            className={clsx(
              'inline-block h-3 w-3 transform rounded-full bg-white shadow transition duration-200',
              enabled ? 'translate-x-3' : 'translate-x-0'
            )}
          />
        </button>
        {issuesUrl && (
          <a
            href={issuesUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="p-1 rounded text-gray-400 hover:text-violet-600 transition-colors"
            title="Open issues in browser"
          >
            <ExternalLink className="h-3.5 w-3.5" />
          </a>
        )}
      </div>
    </div>
  );
}

function ReposPanel({ dataSourceId, sourceType }: { dataSourceId: number; sourceType: DataSourceType }) {
  const qc = useQueryClient();
  const isGitHub = sourceType === 'GITHUB';
  const [showModal, setShowModal] = useState(false);
  const [confirmDetachId, setConfirmDetachId] = useState<number | null>(null);
  const [detachError, setDetachError] = useState<string | null>(null);

  const { data: repos, isLoading } = useQuery({
    queryKey: ['repos', dataSourceId],
    queryFn: () => datasourcesApi.repos.list(dataSourceId).then((r) => r.data),
  });

  const subscribeMutation = useMutation({
    mutationFn: (repoId: number) => reposApi.subscribe(repoId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos', dataSourceId] });
      qc.invalidateQueries({ queryKey: ['repos'] });
    },
  });

  const unsubscribeMutation = useMutation({
    mutationFn: (repoId: number) => reposApi.unsubscribe(repoId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos', dataSourceId] });
      qc.invalidateQueries({ queryKey: ['repos'] });
    },
  });

  const detachMutation = useMutation({
    mutationFn: (repoId: number) => datasourcesApi.repos.detach(dataSourceId, repoId),
    onSuccess: () => {
      setConfirmDetachId(null);
      setDetachError(null);
      qc.invalidateQueries({ queryKey: ['repos', dataSourceId] });
      qc.invalidateQueries({ queryKey: ['datasources'] });
    },
    onError: (err) => {
      setConfirmDetachId(null);
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setDetachError(msg ?? 'Failed to remove repository.');
    },
  });

  if (isLoading) return <p className="text-xs text-gray-400 py-2">Loading repos…</p>;

  return (
    <>
      {isGitHub && (
        <div className="flex justify-end mb-2">
          <Button variant="secondary" size="sm" onClick={() => setShowModal(true)}>
            <Plus className="h-3.5 w-3.5" />
            Add repository
          </Button>
        </div>
      )}

      {detachError && (
        <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2 mb-2">
          <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
          {detachError}
        </div>
      )}

      {!repos?.length ? (
        <p className="text-xs text-gray-400 py-2">
          {isGitHub
            ? 'No repositories attached. Use "Add repository" to get started.'
            : 'No repositories registered under this data source.'}
        </p>
      ) : (
        <ul className="space-y-1.5">
          {repos.map((repo: RepoDto) => (
            <li key={repo.id} className="rounded-md bg-gray-50 px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <BookOpen className="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
                  <span className="text-xs font-medium text-gray-800 truncate">
                    {repo.repoFullName ?? repo.name}
                  </span>
                  {repo.subscribed && (
                    <Badge color="violet" className="text-[10px] px-1.5 py-0">subscribed</Badge>
                  )}
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  {repo.subscribed ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => unsubscribeMutation.mutate(repo.id)}
                      loading={unsubscribeMutation.isPending}
                      className="flex-shrink-0 text-violet-500 hover:text-red-600 text-xs"
                      title="Unsubscribe"
                    >
                      <UserMinus className="h-3.5 w-3.5" />
                    </Button>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => subscribeMutation.mutate(repo.id)}
                      loading={subscribeMutation.isPending}
                      className="flex-shrink-0 text-gray-400 hover:text-violet-600 text-xs"
                      title="Subscribe"
                    >
                      <UserCheck className="h-3.5 w-3.5" />
                    </Button>
                  )}
                  {repo.repoUrl && (
                    <a
                      href={repo.repoUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-1 rounded text-gray-400 hover:text-violet-600 transition-colors"
                      title="Open in browser"
                    >
                      <ExternalLink className="h-3.5 w-3.5" />
                    </a>
                  )}
                  {confirmDetachId === repo.id ? (
                    <div className="flex items-center gap-1">
                      <span className="text-[11px] text-red-500">Remove?</span>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => detachMutation.mutate(repo.id)}
                        loading={detachMutation.isPending}
                        className="text-red-600 hover:text-red-700 text-[11px] px-1.5"
                      >
                        Yes
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setConfirmDetachId(null)}
                        className="text-gray-400 text-[11px] px-1.5"
                      >
                        No
                      </Button>
                    </div>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => { setDetachError(null); setConfirmDetachId(repo.id); }}
                      className="text-gray-400 hover:text-red-500"
                      title="Remove repository"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </div>
              <RepoIssuesSection repo={repo} isGitHub={isGitHub} />
            </li>
          ))}
        </ul>
      )}

      {showModal && (
        <DiscoverReposModal dsId={dataSourceId} onClose={() => setShowModal(false)} />
      )}
    </>
  );
}

// ─── Jira projects sub-panel ──────────────────────────────────────────────────

function JiraProjectRow({
  p, syncing, onRequestDetach, isConfirming, onConfirmDetach, onCancelDetach, isDetaching,
}: {
  p: TrackedJiraProjectDto;
  syncing: boolean;
  onRequestDetach: () => void;
  isConfirming: boolean;
  onConfirmDetach: () => void;
  onCancelDetach: () => void;
  isDetaching: boolean;
}) {
  const { data: counts } = useQuery({
    queryKey: ['jira-issue-count', p.id],
    queryFn: () => issuesApi.getJiraProjectCount(p.id).then((r) => r.data),
    refetchInterval: syncing ? 3_000 : false,
  });

  const projectUrl = p.dataSourceBaseUrl
    ? `${p.dataSourceBaseUrl}/browse/${p.projectKey}`
    : undefined;

  return (
    <li className="rounded-md bg-gray-50 px-3 py-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xs font-mono font-semibold text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded flex-shrink-0">
            {p.projectKey}
          </span>
          <span className="text-xs text-gray-700 truncate">{p.projectName ?? p.projectKey}</span>
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          {counts && (counts.open > 0 || counts.closed > 0) && (
            <span className="text-xs text-gray-400 mr-1">
              <span className="text-emerald-600 font-medium">{counts.open.toLocaleString()}</span>
              <span> open</span>
              <span className="mx-0.5 text-gray-300">/</span>
              <span>{counts.closed.toLocaleString()} closed</span>
            </span>
          )}
          {projectUrl && (
            <a
              href={projectUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="p-1 rounded text-gray-400 hover:text-blue-600 transition-colors"
              title="Open project in Jira"
            >
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
          )}
          {isConfirming ? (
            <div className="flex items-center gap-1">
              <span className="text-[11px] text-red-500">Remove?</span>
              <Button
                variant="ghost"
                size="sm"
                onClick={onConfirmDetach}
                loading={isDetaching}
                className="text-red-600 hover:text-red-700 text-[11px] px-1.5"
              >
                Yes
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={onCancelDetach}
                className="text-gray-400 text-[11px] px-1.5"
              >
                No
              </Button>
            </div>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={onRequestDetach}
              className="text-gray-400 hover:text-red-500"
              title="Remove project"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          )}
        </div>
      </div>
    </li>
  );
}

function JiraProjectsPanel({ dataSourceId, syncing }: { dataSourceId: number; syncing: boolean }) {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [confirmDetachId, setConfirmDetachId] = useState<number | null>(null);
  const [detachError, setDetachError] = useState<string | null>(null);

  const { data: projects, isLoading, isError } = useQuery({
    queryKey: ['jira-projects', dataSourceId],
    queryFn: () => datasourcesApi.projects.list(dataSourceId).then((r) => r.data),
    retry: false,
  });

  const detachMutation = useMutation({
    mutationFn: (projectId: number) => datasourcesApi.projects.detach(dataSourceId, projectId),
    onSuccess: () => {
      setConfirmDetachId(null);
      setDetachError(null);
      qc.invalidateQueries({ queryKey: ['jira-projects', dataSourceId] });
    },
    onError: (err) => {
      setConfirmDetachId(null);
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setDetachError(msg ?? 'Failed to remove project.');
    },
  });

  if (isLoading) return <p className="text-xs text-gray-400 py-2">Loading projects…</p>;
  if (isError) return <p className="text-xs text-red-400 py-2">Could not load Jira projects.</p>;

  return (
    <>
      <div className="flex justify-end mb-2">
        <Button variant="secondary" size="sm" onClick={() => setShowModal(true)}>
          <Plus className="h-3.5 w-3.5" />
          Add project
        </Button>
      </div>

      {detachError && (
        <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2 mb-2">
          <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
          {detachError}
        </div>
      )}

      {!projects?.length ? (
        <p className="text-xs text-gray-400 py-2">
          No tracked Jira projects. Use "Add project" to get started.
        </p>
      ) : (
        <ul className="space-y-1.5">
          {projects.map((p: TrackedJiraProjectDto) => (
            <JiraProjectRow
              key={p.id}
              p={p}
              syncing={syncing}
              onRequestDetach={() => { setDetachError(null); setConfirmDetachId(p.id); }}
              isConfirming={confirmDetachId === p.id}
              onConfirmDetach={() => detachMutation.mutate(p.id)}
              onCancelDetach={() => setConfirmDetachId(null)}
              isDetaching={detachMutation.isPending && confirmDetachId === p.id}
            />
          ))}
        </ul>
      )}

      {showModal && (
        <DiscoverProjectsModal dsId={dataSourceId} onClose={() => setShowModal(false)} />
      )}
    </>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

type FormState = {
  type: DataSourceType;
  name: string;
  baseUrl: string;
  path: string;
  apiToken: string;
  teamId: string;
  repoFullName: string;
  projectKey: string;
};

export function DataSourcesPage() {
  const qc = useQueryClient();
  const { isManager, isAdmin } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<FormState>({
    type: 'GITHUB',
    name: '',
    baseUrl: 'https://api.github.com',
    path: '',
    apiToken: '',
    teamId: '',
    repoFullName: '',
    projectKey: '',
  });
  const [formError, setFormError] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  // Live sync status per data-source ID, populated by the polling loop.
  const [syncStatuses, setSyncStatuses] = useState<Record<number, SyncStatus>>({});

  const { data: sources, isLoading } = useQuery({
    queryKey: ['datasources'],
    queryFn: () => datasourcesApi.list().then((r) => r.data),
  });

  const { data: availableTeams } = useQuery<Team[]>({
    queryKey: ['teams'],
    queryFn: () => teamsApi.list().then((r) => r.data),
    enabled: isManager || isAdmin,
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateDataSourceRequest) => datasourcesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['datasources'] });
      setShowForm(false);
      setShowToken(false);
      setForm({ type: 'GITHUB', name: '', baseUrl: 'https://api.github.com', path: '', apiToken: '', teamId: '', repoFullName: '', projectKey: '' });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setFormError(msg ?? 'Failed to create data source.');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => datasourcesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['datasources'] }),
  });

  const collectMutation = useMutation({
    mutationFn: (id: number) => datasourcesApi.collect(id),
    onSuccess: (_, id) => {
      // Seed a "starting" status so the UI reacts immediately (202 returns fast).
      setSyncStatuses(prev => ({
        ...prev,
        [id]: {
          running: true, phaseNumber: 0, totalPhases: 1,
          phase: 'starting', phaseProcessed: 0, phaseTotal: -1,
          totalProcessed: 0, elapsedSeconds: 0,
          phaseEtaSeconds: null, overallEtaSeconds: null,
          completedPhases: [], result: null, error: null,
        },
      }));
    },
  });

  // Derived: IDs that are actively syncing.
  const syncingIds = new Set(
    Object.entries(syncStatuses)
      .filter(([, s]) => s.running)
      .map(([id]) => Number(id))
  );

  // On mount: restore any in-progress jobs from the server so navigation
  // doesn't lose progress state.
  useEffect(() => {
    datasourcesApi.activeCollectStatuses().then(({ data }) => {
      if (Object.keys(data).length > 0) {
        setSyncStatuses(prev => {
          const merged = { ...prev };
          for (const [key, status] of Object.entries(data)) {
            merged[Number(key)] = status;
          }
          return merged;
        });
      }
    }).catch(() => { /* server may not have any active jobs */ });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Poll every 3 s for every actively-syncing source.
  useEffect(() => {
    if (syncingIds.size === 0) return;
    const timer = setInterval(async () => {
      for (const id of syncingIds) {
        try {
          const { data } = await datasourcesApi.collectStatus(id);
          setSyncStatuses(prev => ({ ...prev, [id]: data }));
          if (!data.running) {
            qc.invalidateQueries({ queryKey: ['datasources'] });
            qc.invalidateQueries({ queryKey: ['jira-issue-count'] });
          }
        } catch {
          // 404 = server restarted, lost in-memory state; treat as done.
          setSyncStatuses(prev => { const n = { ...prev }; delete n[id]; return n; });
          qc.invalidateQueries({ queryKey: ['datasources'] });
        }
      }
    }, 3_000);
    return () => clearInterval(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify([...syncingIds])]);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError('');
    const req: CreateDataSourceRequest = { type: form.type, name: form.name };
    if (NEEDS_BASEURL.includes(form.type) && form.baseUrl) req.baseUrl = form.baseUrl;
    if (NEEDS_PATH.includes(form.type) && form.path) req.path = form.path;
    if (NEEDS_TOKEN.includes(form.type) && form.apiToken) req.apiToken = form.apiToken;
    if (form.teamId) req.teamId = Number(form.teamId);
    if (NEEDS_REPO_FULLNAME.includes(form.type) && form.repoFullName) req.repoFullName = form.repoFullName;
    if (form.type === 'JIRA' && form.projectKey) req.projectKey = form.projectKey;
    createMutation.mutate(req);
  }

  function toggleExpanded(id: number) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  if (isLoading) return <PageSpinner />;

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Data Sources</h1>
          <p className="text-sm text-gray-500 mt-0.5">Connect your repositories and trackers</p>
        </div>
        <Button onClick={() => setShowForm((v) => !v)} size="sm">
          <Plus className="h-4 w-4" />
          Add source
        </Button>
      </div>

      {/* Add form */}
      {showForm && (
        <Card>
          <CardHeader>
            <h2 className="text-sm font-semibold text-gray-900">New data source</h2>
          </CardHeader>
          <CardBody>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Select
                  label="Type"
                  value={form.type}
                  options={TYPE_OPTIONS}
                  onChange={(e) => {
                    const t = e.target.value as DataSourceType;
                    const defaultBaseUrl = t === 'GITHUB' ? 'https://api.github.com' : '';
                    setShowToken(false);
                    setForm({ ...form, type: t, baseUrl: defaultBaseUrl, path: '', repoFullName: '', projectKey: '' });
                  }}
                />
                <Input
                  label="Name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. Work GitHub, Personal Jira"
                  required
                />
              </div>

              {NEEDS_BASEURL.includes(form.type) && (
                <Input
                  label="Base URL"
                  value={form.baseUrl}
                  onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                  placeholder={
                    form.type === 'JIRA'
                      ? 'https://yourcompany.atlassian.net'
                      : 'https://api.github.com'
                  }
                  required
                />
              )}

              {NEEDS_PATH.includes(form.type) && (
                <Input
                  label="Repository path"
                  value={form.path}
                  onChange={(e) => setForm({ ...form, path: e.target.value })}
                  placeholder="C:\Projects\my-repo or /home/user/my-project"
                  required
                />
              )}

              {NEEDS_TOKEN.includes(form.type) && (
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium text-gray-700">API token</label>
                  <div className="relative">
                    <input
                      type={showToken ? 'text' : 'password'}
                      value={form.apiToken}
                      onChange={(e) => setForm({ ...form, apiToken: e.target.value })}
                      placeholder="••••••••••••"
                      className="block w-full px-3 py-2 pr-10 text-sm rounded-lg border border-gray-300 bg-white text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-violet-500 transition-colors duration-150"
                    />
                    <button
                      type="button"
                      onClick={() => setShowToken((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-gray-400 hover:text-gray-600 transition-colors"
                      tabIndex={-1}
                      aria-label={showToken ? 'Hide token' : 'Show token'}
                    >
                      {showToken ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>
              )}

              {NEEDS_REPO_FULLNAME.includes(form.type) && (
                <Input
                  label="GitHub repository (owner/repo)"
                  value={form.repoFullName}
                  onChange={(e) => setForm({ ...form, repoFullName: e.target.value })}
                  placeholder="e.g. acme/backend-api"
                />
              )}

              {form.type === 'JIRA' && (
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium text-gray-700">
                    Jira project key{' '}
                    <span className="text-xs text-gray-400 font-normal">(optional — leave blank to collect all projects)</span>
                  </label>
                  <Input
                    value={form.projectKey}
                    onChange={(e) => setForm({ ...form, projectKey: e.target.value.toUpperCase() })}
                    placeholder="e.g. PDA, PROJ"
                  />
                </div>
              )}

              {(isManager || isAdmin) && availableTeams && availableTeams.length > 0 && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">
                    Assign to team <span className="text-xs text-gray-400 font-normal">(optional)</span>
                  </label>
                  <select
                    value={form.teamId}
                    onChange={(e) => setForm({ ...form, teamId: e.target.value })}
                    className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-500/20"
                  >
                    <option value="">Personal (no team)</option>
                    {availableTeams.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
              )}

              {formError && (
                <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                  <AlertCircle className="h-4 w-4 flex-shrink-0" />
                  {formError}
                </div>
              )}

              <div className="flex gap-2 pt-1">
                <Button type="submit" loading={createMutation.isPending}>Save</Button>
                <Button type="button" variant="secondary" onClick={() => setShowForm(false)}>Cancel</Button>
              </div>
            </form>
          </CardBody>
        </Card>
      )}

      {/* Sources list */}
      {sources?.length === 0 && (
        <div className="text-center py-16 text-gray-400">
          <Database className="h-10 w-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No data sources yet. Add one to start collecting metrics.</p>
        </div>
      )}

      <div className="space-y-3">
        {sources?.map((src) => {
          const expanded = expandedIds.has(src.id);
          const displayUrl = src.baseUrl ?? src.path;
          return (
            <Card key={src.id} className="hover:shadow-sm transition-shadow">
              {/* Header row */}
              <div className="flex items-center gap-4 px-5 py-4">
                <div className="flex-shrink-0 p-2.5 rounded-lg bg-gray-50 text-gray-500">
                  <TypeIcon type={src.type} />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="text-sm font-semibold text-gray-900">{src.name}</p>
                    <Badge color={TYPE_COLORS[src.type]}>{TYPE_LABELS[src.type]}</Badge>
                    {src.teamId && (
                      <Badge color="blue">Team</Badge>
                    )}
                  </div>
                  {displayUrl && (
                    <p className="text-xs text-gray-400 mt-0.5 truncate">{displayUrl}</p>
                  )}
                  <p className="text-xs text-gray-400 mt-0.5">
                    {syncingIds.has(src.id) ? (
                      <SyncProgressLine status={syncStatuses[src.id]} />
                    ) : (
                      <SyncStatusLine dateStr={src.lastSuccessSync} />
                    )}
                  </p>
                </div>

                <div className="flex items-center gap-1 flex-shrink-0">
                  {/* Collect button */}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => collectMutation.mutate(src.id)}
                    loading={
                      (collectMutation.isPending && collectMutation.variables === src.id) ||
                      syncingIds.has(src.id)
                    }
                    disabled={syncingIds.has(src.id)}
                    className="text-gray-400 hover:text-violet-600"
                    title={syncingIds.has(src.id) ? 'Sync in progress' : 'Collect data'}
                  >
                    <Play className="h-4 w-4" />
                  </Button>

                  {/* Expand/collapse repos */}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => toggleExpanded(src.id)}
                    className="text-gray-400 hover:text-gray-700"
                    title={expanded ? 'Hide repositories' : 'Show repositories'}
                  >
                    {expanded
                      ? <ChevronDown className="h-4 w-4" />
                      : <ChevronRight className="h-4 w-4" />
                    }
                  </Button>

                  {/* Delete — only shown when the user is allowed to delete this config */}
                  {src.canDelete && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteMutation.mutate(src.id)}
                      loading={deleteMutation.isPending}
                      className="text-gray-400 hover:text-red-600"
                      title="Delete data source"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              </div>

              {/* Repos panel */}
              {expanded && (
                <div className="border-t border-gray-100 px-5 py-3">
                  <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">
                    Repositories
                  </p>
                  <ReposPanel dataSourceId={src.id} sourceType={src.type} />
                </div>
              )}

              {/* Jira projects panel */}
              {expanded && src.type === 'JIRA' && (
                <div className="border-t border-gray-100 px-5 py-3">
                  <p className="text-xs font-semibold text-gray-500 mb-2 uppercase tracking-wide">
                    Jira Projects
                  </p>
                  <JiraProjectsPanel dataSourceId={src.id} syncing={syncingIds.has(src.id)} />
                </div>
              )}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
