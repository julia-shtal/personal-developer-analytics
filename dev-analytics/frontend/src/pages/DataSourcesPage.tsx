import { useState, useEffect, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Trash2, ChevronDown, ChevronRight,
  ExternalLink, UserCheck, UserMinus, Play,
  Eye, EyeOff, MessageSquare, AlertCircle, X,
} from 'lucide-react';
import { clsx } from 'clsx';
import { datasourcesApi, jiraProjectsApi, type SyncStatus } from '@/api/datasources';
import { issuesApi } from '@/api/issues';
import { reposApi } from '@/api/repos';
import { DiscoverReposModal } from './DiscoverReposModal';
import { DiscoverProjectsModal } from './DiscoverProjectsModal';
import { teamsApi } from '@/api/teams';
import { Chip } from '@/components/ui/Chip';
import { PageSpinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import { Jira, Folder, Branch, Github } from '@/components/icons';
import type { DataSourceType, CreateDataSourceRequest, RepoDto, Team, TrackedJiraProjectDto } from '@/types';

// ─── Type helpers ──────────────────────────────────────────────────────────────

const TYPE_LABELS: Record<DataSourceType, string> = {
  GIT_LOCAL: 'Local Git',
  GITHUB: 'GitHub',
  JIRA: 'Jira',
};

const TYPE_ACCENT: Record<DataSourceType, string> = {
  GITHUB:    'var(--brand-github)',
  JIRA:      'var(--brand-jira)',
  GIT_LOCAL: 'var(--amber)',
};

const TYPE_ACCENT_BG: Record<DataSourceType, string> = {
  GITHUB:    'var(--brand-github-bg)',
  JIRA:      'var(--brand-jira-bg)',
  GIT_LOCAL: 'var(--amber-bg)',
};

const TYPE_DESCS: Record<DataSourceType, string> = {
  GITHUB: 'API · token',
  JIRA: 'cloud · token',
  GIT_LOCAL: 'filesystem',
};

const NEEDS_BASEURL: DataSourceType[] = ['GITHUB', 'JIRA'];
const NEEDS_PATH: DataSourceType[] = ['GIT_LOCAL'];
const NEEDS_TOKEN: DataSourceType[] = ['GITHUB', 'JIRA'];
const NEEDS_REPO_FULLNAME: DataSourceType[] = ['GITHUB'];

function TypeIcon({ type, size = 18 }: { type: DataSourceType; size?: number }) {
  if (type === 'GITHUB') return <Github width={size} height={size} />;
  if (type === 'JIRA') return <Jira width={size} height={size} />;
  return <Folder width={size} height={size} />;
}

// ─── Sync progress display ─────────────────────────────────────────────────────

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
    return <span className="t-label" style={{ color: 'var(--violet)', fontSize: 11 }}>Syncing — starting…</span>;
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
  const etaStr = fmtEta(status.overallEtaSeconds ?? status.phaseEtaSeconds);
  const elapsed = status.elapsedSeconds > 0 ? fmtSeconds(status.elapsedSeconds) : null;
  const mainLine = [phaseLabel, countStr].filter(Boolean).join(' — ');
  const timeLine = etaStr || (elapsed ? `${elapsed} elapsed` : '');
  const history = status.completedPhases.map(p =>
    `${p.name}: ${p.itemsSaved.toLocaleString()} (${fmtSeconds(p.durationSeconds)})`
  ).join(' → ');

  return (
    <span className="t-label" style={{ color: 'var(--violet)', fontSize: 11 }}>
      {`Syncing: ${mainLine}${timeLine ? ` (${timeLine})` : ''}`}
      {history && <span style={{ display: 'block', color: 'var(--fg-3)', marginTop: 2 }}>Done: {history}</span>}
    </span>
  );
}

// ─── Sync age formatter ────────────────────────────────────────────────────────

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

function syncDotVariant(dateStr?: string): 'live' | 'warn' | 'fail' {
  if (!dateStr) return 'fail';
  const diffH = (Date.now() - new Date(dateStr).getTime()) / 3600000;
  if (diffH < 24) return 'live';
  if (diffH < 168) return 'warn';
  return 'fail';
}

function SyncStatusLine({ dateStr }: { dateStr?: string }) {
  const variant = syncDotVariant(dateStr);
  return (
    <span className="row gap-2">
      <span className={`dot dot-${variant}`} />
      <span className="t-label" style={{ fontSize: 11 }}>
        {dateStr
          ? variant === 'warn'
            ? `last sync ${formatSyncAge(dateStr)} — overdue`
            : `synced ${formatSyncAge(dateStr)}`
          : 'never synced — check token'}
      </span>
    </span>
  );
}

// ─── Repos sub-panel ───────────────────────────────────────────────────────────

function RepoIssuesSection({ repo, isGitHub }: { repo: RepoDto; isGitHub: boolean }) {
  const qc = useQueryClient();
  const [enabled, setEnabled] = useState(repo.collectIssues);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEnabled(repo.collectIssues);
  }, [repo.collectIssues]);

  const { data: counts } = useQuery({
    queryKey: ['issue-count', repo.id],
    queryFn: () => issuesApi.getCount(repo.id).then((r) => r.data),
    enabled: isGitHub && enabled,
    refetchInterval: enabled && !repo.issuesLastSyncedAt ? 5_000 : false,
  });

  const toggleMutation = useMutation({
    mutationFn: (next: boolean) => reposApi.setCollectIssues(repo.id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['repos'] });
      qc.invalidateQueries({ queryKey: ['issue-count', repo.id] });
    },
    onError: () => setEnabled(repo.collectIssues),
  });

  if (!isGitHub) return null;

  const issuesUrl = repo.repoUrl ? `${repo.repoUrl}/issues` : undefined;
  const isInitialSync = enabled && !repo.issuesLastSyncedAt;

  function handleToggle() {
    const next = !enabled;
    setEnabled(next);
    toggleMutation.mutate(next);
  }

  return (
    <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--line-2)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
      <div className="row gap-2" style={{ fontSize: 11, color: 'var(--fg-3)', minWidth: 0 }}>
        <MessageSquare width={12} height={12} style={{ flexShrink: 0 }} />
        <span>Issues</span>
        {enabled && (
          isInitialSync && !counts ? (
            <span style={{ color: 'var(--violet)' }}>— syncing…</span>
          ) : counts ? (
            <span>
              —{' '}
              <span style={{ color: 'var(--emerald)', fontWeight: 500 }}>{counts.open.toLocaleString()}</span>
              {' '}open
              <span style={{ color: 'var(--line)', margin: '0 3px' }}>/</span>
              {counts.closed.toLocaleString()} closed
            </span>
          ) : null
        )}
      </div>
      <div className="row gap-1" style={{ flexShrink: 0 }}>
        <button
          role="switch"
          aria-checked={enabled}
          aria-label={enabled ? 'Disable issue collection' : 'Enable issue collection'}
          onClick={handleToggle}
          disabled={toggleMutation.isPending}
          className={clsx(
            'relative inline-flex h-4 w-7 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent',
            'transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-offset-1',
            enabled ? 'bg-[var(--accent)]' : 'bg-gray-200',
            toggleMutation.isPending && 'opacity-50 cursor-not-allowed'
          )}
        >
          <span className={clsx('inline-block h-3 w-3 transform rounded-full bg-white shadow transition duration-200', enabled ? 'translate-x-3' : 'translate-x-0')} />
        </button>
        {issuesUrl && (
          <a href={issuesUrl} target="_blank" rel="noopener noreferrer" className="btn btn-sm btn-icon" title="Open issues" aria-label="Open issues in browser">
            <ExternalLink width={11} height={11} />
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

  if (isLoading) return <p className="t-label" style={{ padding: '8px 0' }}>Loading repos…</p>;

  return (
    <>
      {isGitHub && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <button className="btn btn-sm" onClick={() => setShowModal(true)}>
            <Plus width={12} height={12} />
            Add repository
          </button>
        </div>
      )}

      {detachError && (
        <div className="row gap-2" style={{ fontSize: 11, color: 'var(--coral-strong)', background: 'var(--coral-bg)', borderRadius: 6, padding: '8px 12px', marginBottom: 8 }}>
          <AlertCircle width={12} height={12} style={{ flexShrink: 0 }} />
          {detachError}
        </div>
      )}

      {!repos?.length ? (
        <p className="t-label" style={{ padding: '8px 0' }}>
          {isGitHub ? 'No repositories attached. Use "Add repository" to get started.' : 'No repositories registered under this data source.'}
        </p>
      ) : (
        <div className="col gap-2">
          {repos.map((repo: RepoDto) => (
            <div key={repo.id} className="row" style={{ background: 'var(--bg-card)', border: '1px solid var(--line-2)', padding: '10px 12px', borderRadius: 6, gap: 10 }}>
              <Branch width={14} height={14} style={{ color: 'var(--fg-3)', flexShrink: 0 }} />
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--fg)', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {repo.repoFullName ?? repo.name}
              </span>
              {repo.subscribed && <Chip accent>subscribed</Chip>}
              <div className="row gap-1" style={{ flexShrink: 0 }}>
                {repo.subscribed ? (
                  <button className="btn btn-sm btn-icon" onClick={() => unsubscribeMutation.mutate(repo.id)} title="Unsubscribe" aria-label="Unsubscribe">
                    <UserMinus width={12} height={12} />
                  </button>
                ) : (
                  <button className="btn btn-sm btn-icon" onClick={() => subscribeMutation.mutate(repo.id)} title="Subscribe" aria-label="Subscribe">
                    <UserCheck width={12} height={12} />
                  </button>
                )}
                {repo.repoUrl && (
                  <a href={repo.repoUrl} target="_blank" rel="noopener noreferrer" className="btn btn-sm btn-icon" title="Open in browser" aria-label="Open repository">
                    <ExternalLink width={11} height={11} />
                  </a>
                )}
                {confirmDetachId === repo.id ? (
                  <div className="row gap-1">
                    <span className="t-label" style={{ color: 'var(--coral)', fontSize: 11 }}>Remove?</span>
                    <button className="btn btn-sm" style={{ color: 'var(--coral)' }} onClick={() => detachMutation.mutate(repo.id)}>Yes</button>
                    <button className="btn btn-sm" onClick={() => setConfirmDetachId(null)}>No</button>
                  </div>
                ) : (
                  <button className="btn btn-sm btn-icon" onClick={() => { setDetachError(null); setConfirmDetachId(repo.id); }} title="Remove repository" aria-label="Remove repository">
                    <Trash2 width={12} height={12} />
                  </button>
                )}
              </div>
              <RepoIssuesSection repo={repo} isGitHub={isGitHub} />
            </div>
          ))}
        </div>
      )}

      {showModal && <DiscoverReposModal dsId={dataSourceId} onClose={() => setShowModal(false)} />}
    </>
  );
}

// ─── Jira project → repo linking ──────────────────────────────────────────────

function LinkRepoModal({ jiraProjectId, onClose }: { jiraProjectId: number; onClose: () => void }) {
  const qc = useQueryClient();
  const [error, setError] = useState('');

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) { if (e.key === 'Escape') onClose(); }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const { data: allRepos } = useQuery({
    queryKey: ['repos'],
    queryFn: () => reposApi.list().then((r) => r.data),
    staleTime: 5 * 60_000,
  });

  const { data: linked } = useQuery({
    queryKey: ['jira-linked-repos', jiraProjectId],
    queryFn: () => jiraProjectsApi.listLinkedRepos(jiraProjectId).then((r) => r.data),
  });

  const subscribed = allRepos?.filter((r) => r.subscribed && r.repoFullName) ?? [];
  const linkedIds = new Set(linked?.map((r) => r.id) ?? []);

  const linkMutation = useMutation({
    mutationFn: (repoId: number) => jiraProjectsApi.linkRepo(jiraProjectId, repoId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['jira-linked-repos', jiraProjectId] });
      setError('');
    },
    onError: () => setError('Failed to link repository.'),
  });

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={onClose}
    >
      <div
        style={{ background: 'var(--bg-card)', border: '1px solid var(--line)', borderRadius: 10, padding: 24, width: 400, maxHeight: '80vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16, alignItems: 'center' }}>
          <span style={{ fontWeight: 600, fontSize: 14 }}>Link GitHub repository</span>
          <button className="btn btn-ghost btn-icon" onClick={onClose} aria-label="Close"><X width={14} height={14} /></button>
        </div>

        {error && (
          <div style={{ fontSize: 11, color: 'var(--coral-strong)', background: 'var(--coral-bg)', borderRadius: 6, padding: '6px 10px', marginBottom: 10 }}>{error}</div>
        )}

        {subscribed.length === 0 ? (
          <p className="t-label" style={{ padding: '8px 0' }}>No subscribed GitHub repositories found. Subscribe to a repo first.</p>
        ) : (
          <div className="col gap-2">
            {subscribed.map((repo) => {
              const isLinked = linkedIds.has(repo.id);
              return (
                <div key={repo.id} className="row" style={{ gap: 10, padding: '8px 10px', background: 'var(--bg-2)', borderRadius: 6, alignItems: 'center' }}>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, flex: 1 }}>{repo.repoFullName}</span>
                  {isLinked ? (
                    <Chip color="cyan">linked</Chip>
                  ) : (
                    <button
                      className="btn btn-sm"
                      disabled={linkMutation.isPending}
                      onClick={() => linkMutation.mutate(repo.id)}
                    >
                      Link
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function JiraProjectLinkedReposSection({ jiraProjectId }: { jiraProjectId: number }) {
  const qc = useQueryClient();
  const [showLinkModal, setShowLinkModal] = useState(false);

  const { data: linked } = useQuery({
    queryKey: ['jira-linked-repos', jiraProjectId],
    queryFn: () => jiraProjectsApi.listLinkedRepos(jiraProjectId).then((r) => r.data),
  });

  const unlinkMutation = useMutation({
    mutationFn: (repoId: number) => jiraProjectsApi.unlinkRepo(jiraProjectId, repoId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jira-linked-repos', jiraProjectId] }),
  });

  return (
    <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--line-2)' }}>
      <div className="row gap-2" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
        <span className="t-label" style={{ fontSize: 11, color: 'var(--fg-3)', flexShrink: 0 }}>linked repos:</span>
        {linked && linked.length > 0 ? linked.map((repo) => (
          <span key={repo.id} className="row gap-1" style={{ alignItems: 'center', background: 'var(--cyan-bg)', borderRadius: 4, padding: '2px 6px', fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--cyan)' }}>
            {repo.repoFullName ?? repo.name}
            <button
              style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, color: 'var(--fg-3)', lineHeight: 1, display: 'flex' }}
              title="Unlink"
              aria-label={`Unlink ${repo.repoFullName ?? repo.name}`}
              onClick={() => unlinkMutation.mutate(repo.id)}
              disabled={unlinkMutation.isPending}
            >
              <X width={10} height={10} />
            </button>
          </span>
        )) : (
          <span className="t-label" style={{ fontSize: 11 }}>none</span>
        )}
        <button className="btn btn-sm" style={{ marginLeft: 'auto' }} onClick={() => setShowLinkModal(true)}>
          <Plus width={10} height={10} />
          Link repo
        </button>
      </div>
      {showLinkModal && <LinkRepoModal jiraProjectId={jiraProjectId} onClose={() => setShowLinkModal(false)} />}
    </div>
  );
}

// ─── Jira projects sub-panel ───────────────────────────────────────────────────

function JiraProjectRow({
  p, syncing, onRequestDetach, isConfirming, onConfirmDetach, onCancelDetach, isDetaching, isOwner,
}: {
  p: TrackedJiraProjectDto;
  syncing: boolean;
  onRequestDetach: () => void;
  isConfirming: boolean;
  onConfirmDetach: () => void;
  onCancelDetach: () => void;
  isDetaching: boolean;
  isOwner: boolean;
}) {
  const { data: counts } = useQuery({
    queryKey: ['jira-issue-count', p.id],
    queryFn: () => issuesApi.getJiraProjectCount(p.id).then((r) => r.data),
    refetchInterval: syncing ? 3_000 : false,
  });

  const projectUrl = p.dataSourceBaseUrl ? `${p.dataSourceBaseUrl}/browse/${p.projectKey}` : undefined;

  return (
    <div style={{ background: 'var(--bg-card)', border: '1px solid var(--line-2)', padding: '10px 12px', borderRadius: 6 }}>
      <div className="row" style={{ gap: 10 }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color: 'var(--cyan)', background: 'var(--cyan-bg)', padding: '2px 6px', borderRadius: 4, flexShrink: 0 }}>
          {p.projectKey}
        </span>
        <span style={{ fontSize: 12, color: 'var(--fg)', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {p.projectName ?? p.projectKey}
        </span>
        {counts && (counts.open > 0 || counts.closed > 0) && (
          <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>
            <span style={{ color: 'var(--emerald)', fontWeight: 500 }}>{counts.open.toLocaleString()}</span>
            <span style={{ color: 'var(--line)', margin: '0 2px' }}>/</span>
            {counts.closed.toLocaleString()}
            <span className="t-label" style={{ marginLeft: 4 }}>issues</span>
          </span>
        )}
        <div className="row gap-1" style={{ flexShrink: 0 }}>
          {projectUrl && (
            <a href={projectUrl} target="_blank" rel="noopener noreferrer" className="btn btn-sm btn-icon" title="Open project" aria-label="Open Jira project">
              <ExternalLink width={11} height={11} />
            </a>
          )}
          {isOwner && (isConfirming ? (
            <div className="row gap-1">
              <span className="t-label" style={{ color: 'var(--coral)', fontSize: 11 }}>Remove?</span>
              <button className="btn btn-sm" style={{ color: 'var(--coral)' }} onClick={onConfirmDetach} disabled={isDetaching}>Yes</button>
              <button className="btn btn-sm" onClick={onCancelDetach}>No</button>
            </div>
          ) : (
            <button className="btn btn-sm btn-icon" onClick={onRequestDetach} title="Remove project" aria-label="Remove project">
              <Trash2 width={12} height={12} />
            </button>
          ))}
        </div>
      </div>
      <JiraProjectLinkedReposSection jiraProjectId={p.id} />
    </div>
  );
}

function JiraProjectsPanel({ dataSourceId, syncing, isOwner }: { dataSourceId: number; syncing: boolean; isOwner: boolean }) {
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

  if (isLoading) return <p className="t-label" style={{ padding: '8px 0' }}>Loading projects…</p>;
  if (isError) return <p className="t-label" style={{ padding: '8px 0', color: 'var(--coral)' }}>Could not load Jira projects.</p>;

  return (
    <>
      {isOwner && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <button className="btn btn-sm" onClick={() => setShowModal(true)}>
            <Plus width={12} height={12} />
            Add project
          </button>
        </div>
      )}

      {detachError && (
        <div className="row gap-2" style={{ fontSize: 11, color: 'var(--coral-strong)', background: 'var(--coral-bg)', borderRadius: 6, padding: '8px 12px', marginBottom: 8 }}>
          <AlertCircle width={12} height={12} style={{ flexShrink: 0 }} />
          {detachError}
        </div>
      )}

      {!projects?.length ? (
        <p className="t-label" style={{ padding: '8px 0' }}>No tracked Jira projects. Use "Add project" to get started.</p>
      ) : (
        <div className="col gap-2">
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
              isOwner={isOwner}
            />
          ))}
        </div>
      )}

      {showModal && <DiscoverProjectsModal dsId={dataSourceId} onClose={() => setShowModal(false)} />}
    </>
  );
}

// ─── Main page ─────────────────────────────────────────────────────────────────

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
  const [syncStatuses, setSyncStatuses] = useState<Record<number, SyncStatus>>({});

  const { data: sources, isLoading } = useQuery({
    queryKey: ['datasources'],
    queryFn: () => datasourcesApi.list().then((r) => r.data),
  });

  const { data: allRepos } = useQuery({
    queryKey: ['repos-all'],
    queryFn: () => reposApi.list().then((r) => r.data),
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
      qc.invalidateQueries({ queryKey: ['repos-all'] });
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

  const syncingIds = new Set(
    Object.entries(syncStatuses)
      .filter(([, s]) => s.running)
      .map(([id]) => Number(id))
  );

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
    }).catch(() => {});
  }, []);

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
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  }

  if (isLoading) return <PageSpinner />;

  const sourceCount = sources?.length ?? 0;
  const repoCount = allRepos?.length ?? 0;

  return (
    <div className="page fade-in">

      {/* ── Hero ──────────────────────────────────────────── */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 28, gap: 20, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 400px', minWidth: 0 }}>
          <div className="t-eyebrow" style={{ marginBottom: 10 }}>── Sources</div>
          <h1 className="t-h1">
            <em>{sourceCount} {sourceCount === 1 ? 'source' : 'sources'}</em>
            {repoCount > 0 && <>, <em>{repoCount} repos</em></>}
            {' '}feed the metrics.
          </h1>
        </div>
        <button className="btn btn-accent" onClick={() => setShowForm((v) => !v)} style={{ flexShrink: 0 }}>
          <Plus width={13} height={13} />
          connect source
        </button>
      </div>

      {/* ── New source form ───────────────────────────────── */}
      {showForm && (
        <div className="card" style={{ padding: 22, marginBottom: 20 }}>
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
            <div className="t-h2" style={{ fontSize: 20 }}>New source</div>
            <button className="btn btn-ghost btn-icon" onClick={() => setShowForm(false)} aria-label="Close form">
              <X width={14} height={14} />
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            {/* Type picker */}
            <div className="t-eyebrow" style={{ marginBottom: 10 }}>type</div>
            <div className="row gap-3" style={{ marginBottom: 18, flexWrap: 'wrap' }}>
              {(['GITHUB', 'JIRA', 'GIT_LOCAL'] as DataSourceType[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  className="col gap-2"
                  onClick={() => {
                    const defaultBaseUrl = t === 'GITHUB' ? 'https://api.github.com' : '';
                    setShowToken(false);
                    setForm({ ...form, type: t, baseUrl: defaultBaseUrl, path: '', repoFullName: '', projectKey: '' });
                  }}
                  style={{
                    padding: 14,
                    background: form.type === t ? 'var(--bg-2)' : 'var(--bg-card)',
                    border: `1px solid ${form.type === t ? 'var(--accent)' : 'var(--line)'}`,
                    borderRadius: 8,
                    cursor: 'pointer',
                    width: 150,
                    textAlign: 'left',
                  }}
                >
                  <span style={{ color: TYPE_ACCENT[t] }}><TypeIcon type={t} size={18} /></span>
                  <span style={{ fontWeight: 500, fontSize: 13, color: 'var(--fg)' }}>{TYPE_LABELS[t]}</span>
                  <span className="t-label" style={{ fontSize: 10 }}>{TYPE_DESCS[t]}</span>
                </button>
              ))}
            </div>

            {/* Form fields */}
            <div className="col gap-3" style={{ maxWidth: 600 }}>
              <div>
                <div className="t-eyebrow" style={{ marginBottom: 6 }}>name</div>
                <input
                  className="input"
                  placeholder="e.g. Work GitHub"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  required
                />
              </div>

              {NEEDS_BASEURL.includes(form.type) && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>base url</div>
                  <input
                    className="input"
                    value={form.baseUrl}
                    onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                    placeholder={form.type === 'JIRA' ? 'https://yourcompany.atlassian.net' : 'https://api.github.com'}
                    required
                  />
                </div>
              )}

              {NEEDS_PATH.includes(form.type) && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>repository path</div>
                  <input
                    className="input"
                    value={form.path}
                    onChange={(e) => setForm({ ...form, path: e.target.value })}
                    placeholder="C:\Projects\my-repo or /home/user/my-project"
                    required
                  />
                </div>
              )}

              {NEEDS_TOKEN.includes(form.type) && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>api token</div>
                  <div style={{ position: 'relative' }}>
                    <input
                      className="input"
                      type={showToken ? 'text' : 'password'}
                      value={form.apiToken}
                      onChange={(e) => setForm({ ...form, apiToken: e.target.value })}
                      placeholder="••••••••••••"
                      style={{ paddingRight: 40 }}
                    />
                    <button
                      type="button"
                      onClick={() => setShowToken((v) => !v)}
                      style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--fg-3)', padding: 0 }}
                      tabIndex={-1}
                      aria-label={showToken ? 'Hide token' : 'Show token'}
                    >
                      {showToken ? <EyeOff width={14} height={14} /> : <Eye width={14} height={14} />}
                    </button>
                  </div>
                  <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>stored encrypted · never logged</div>
                </div>
              )}

              {NEEDS_REPO_FULLNAME.includes(form.type) && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>github repository <span className="t-label" style={{ fontSize: 10 }}>(optional — owner/repo)</span></div>
                  <input
                    className="input"
                    value={form.repoFullName}
                    onChange={(e) => setForm({ ...form, repoFullName: e.target.value })}
                    placeholder="e.g. acme/backend-api"
                  />
                </div>
              )}

              {form.type === 'JIRA' && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>jira project key <span className="t-label" style={{ fontSize: 10 }}>(optional)</span></div>
                  <input
                    className="input"
                    value={form.projectKey}
                    onChange={(e) => setForm({ ...form, projectKey: e.target.value.toUpperCase() })}
                    placeholder="e.g. PDA, PROJ"
                  />
                </div>
              )}

              {(isManager || isAdmin) && availableTeams && availableTeams.length > 0 && (
                <div>
                  <div className="t-eyebrow" style={{ marginBottom: 6 }}>assign to team <span className="t-label" style={{ fontSize: 10 }}>(optional)</span></div>
                  <select
                    className="input"
                    value={form.teamId}
                    onChange={(e) => setForm({ ...form, teamId: e.target.value })}
                  >
                    <option value="">Personal (no team)</option>
                    {availableTeams.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
              )}

              {formError && (
                <div className="row gap-2" style={{ fontSize: 12, color: 'var(--coral-strong)', background: 'var(--coral-bg)', borderRadius: 6, padding: '10px 14px' }}>
                  <AlertCircle width={14} height={14} style={{ flexShrink: 0 }} />
                  {formError}
                </div>
              )}

              <div className="row gap-2" style={{ marginTop: 8 }}>
                <button type="submit" className="btn btn-accent" disabled={createMutation.isPending}>
                  {createMutation.isPending ? 'Saving…' : 'save & connect'}
                </button>
                <button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>cancel</button>
              </div>
            </div>
          </form>
        </div>
      )}

      {/* ── Empty state ─────────────────────────────────────── */}
      {sourceCount === 0 && (
        <div style={{ textAlign: 'center', padding: '48px 0', color: 'var(--muted)' }}>
          <p className="t-body">No data sources yet. Connect one to start collecting metrics.</p>
        </div>
      )}

      {/* ── Sources list ────────────────────────────────────── */}
      <div className="col gap-3" style={{ marginBottom: 24 }}>
        {sources?.map((src) => {
          const isExpanded = expandedIds.has(src.id);
          const displayUrl = src.baseUrl ?? src.path;
          const isSyncing = syncingIds.has(src.id);

          return (
            <div key={src.id} className="card">
              <div
                className="row"
                style={{ padding: '16px 18px', gap: 14, cursor: 'pointer', alignItems: 'center' }}
                onClick={() => toggleExpanded(src.id)}
              >
                {/* Type icon square */}
                <div style={{
                  width: 40, height: 40, borderRadius: 8,
                  background: TYPE_ACCENT_BG[src.type],
                  color: TYPE_ACCENT[src.type],
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}>
                  <TypeIcon type={src.type} size={18} />
                </div>

                {/* Name + meta */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="row gap-2" style={{ flexWrap: 'wrap', marginBottom: 4 }}>
                    <span style={{ fontWeight: 500, fontSize: 14, color: 'var(--fg)' }}>{src.name}</span>
                    <span className="chip" style={{ background: TYPE_ACCENT_BG[src.type], color: TYPE_ACCENT[src.type], borderColor: 'transparent' }}>{TYPE_LABELS[src.type]}</span>
                    {src.teamId && <Chip>team</Chip>}
                  </div>
                  {displayUrl && (
                    <div className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)', marginBottom: 6, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {displayUrl}
                    </div>
                  )}
                  <div>
                    {isSyncing ? (
                      <SyncProgressLine status={syncStatuses[src.id]} />
                    ) : (
                      <SyncStatusLine dateStr={src.lastSuccessSync} />
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="row gap-1" style={{ flexShrink: 0 }} onClick={(e) => e.stopPropagation()}>
                  <button
                    className="btn btn-sm btn-icon"
                    title={isSyncing ? 'Sync in progress' : 'Collect data'}
                    aria-label="Collect data"
                    disabled={isSyncing}
                    onClick={() => collectMutation.mutate(src.id)}
                  >
                    <Play width={11} height={11} />
                  </button>
                  {src.canDelete && (
                    <button
                      className="btn btn-sm btn-icon"
                      title="Delete data source"
                      aria-label="Delete data source"
                      onClick={() => deleteMutation.mutate(src.id)}
                      disabled={deleteMutation.isPending}
                    >
                      <Trash2 width={13} height={13} />
                    </button>
                  )}
                  <span style={{ padding: '6px 6px', color: 'var(--fg-3)' }}>
                    {isExpanded
                      ? <ChevronDown width={14} height={14} />
                      : <ChevronRight width={14} height={14} />}
                  </span>
                </div>
              </div>

              {/* Expanded panel */}
              {isExpanded && (
                <div style={{ borderTop: '1px solid var(--line-2)', background: 'var(--bg-2)', padding: 16 }}>
                  {src.type !== 'JIRA' && (
                    <>
                      <div className="t-eyebrow" style={{ marginBottom: 10 }}>── repositories</div>
                      <ReposPanel dataSourceId={src.id} sourceType={src.type} />
                    </>
                  )}
                  {src.type === 'JIRA' && (
                    <>
                      <div className="t-eyebrow" style={{ marginBottom: 10 }}>── jira projects</div>
                      <JiraProjectsPanel dataSourceId={src.id} syncing={isSyncing} isOwner={!!src.canDelete} />
                    </>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div style={{ height: 32 }} />
    </div>
  );
}