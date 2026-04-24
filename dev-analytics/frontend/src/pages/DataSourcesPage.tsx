import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus, Trash2, Database, GitBranch, Layers, AlertCircle,
  ChevronDown, ChevronRight, BookOpen, Link, Unlink,
} from 'lucide-react';
import { datasourcesApi } from '@/api/datasources';
import { reposApi } from '@/api/repos';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { PageSpinner } from '@/components/ui/Spinner';
import type { DataSourceType, CreateDataSourceRequest, RepoDto } from '@/types';

// ─── Type helpers ─────────────────────────────────────────────────────────────

const TYPE_LABELS: Record<DataSourceType, string> = {
  GIT_LOCAL: 'Local Git',
  GITHUB: 'GitHub',
  JIRA: 'Jira',
  GITHUB_ISSUES: 'GitHub Issues',
};

const TYPE_COLORS: Record<DataSourceType, 'gray' | 'violet' | 'blue' | 'amber'> = {
  GIT_LOCAL: 'gray',
  GITHUB: 'violet',
  JIRA: 'blue',
  GITHUB_ISSUES: 'amber',
};

function TypeIcon({ type }: { type: DataSourceType }) {
  if (type === 'GITHUB' || type === 'GITHUB_ISSUES') return <GitBranch className="h-5 w-5" />;
  if (type === 'GIT_LOCAL') return <Layers className="h-5 w-5" />;
  return <Database className="h-5 w-5" />;
}

const TYPE_OPTIONS = [
  { value: 'GIT_LOCAL',       label: 'Local Git repository' },
  { value: 'GITHUB',          label: 'GitHub' },
  { value: 'JIRA',            label: 'Jira' },
  { value: 'GITHUB_ISSUES',   label: 'GitHub Issues' },
];

// Types that need a base URL (the remote API address)
const NEEDS_BASEURL: DataSourceType[] = ['GITHUB', 'JIRA', 'GITHUB_ISSUES'];
// Types that need a local filesystem path
const NEEDS_PATH: DataSourceType[] = ['GIT_LOCAL'];
// Types that need an API token
const NEEDS_TOKEN: DataSourceType[] = ['GITHUB', 'JIRA', 'GITHUB_ISSUES'];

// ─── Repos sub-panel ──────────────────────────────────────────────────────────

function ReposPanel({ dataSourceId }: { dataSourceId: number }) {
  const qc = useQueryClient();

  const { data: repos, isLoading } = useQuery({
    queryKey: ['repos', dataSourceId],
    queryFn: () => reposApi.list(dataSourceId).then((r) => r.data),
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

  if (isLoading) return <p className="text-xs text-gray-400 py-2">Loading repos…</p>;
  if (!repos?.length) return <p className="text-xs text-gray-400 py-2">No repositories registered under this data source.</p>;

  return (
    <ul className="space-y-1.5">
      {repos.map((repo: RepoDto) => (
        <li key={repo.id} className="flex items-center justify-between gap-2 rounded-md bg-gray-50 px-3 py-2">
          <div className="flex items-center gap-2 min-w-0">
            <BookOpen className="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
            <span className="text-xs font-medium text-gray-800 truncate">
              {repo.repoFullName ?? repo.name}
            </span>
            {repo.subscribed && (
              <Badge color="violet" className="text-[10px] px-1.5 py-0">subscribed</Badge>
            )}
          </div>
          {repo.subscribed ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => unsubscribeMutation.mutate(repo.id)}
              loading={unsubscribeMutation.isPending}
              className="flex-shrink-0 text-gray-400 hover:text-red-600 text-xs"
              title="Unsubscribe"
            >
              <Unlink className="h-3.5 w-3.5" />
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => subscribeMutation.mutate(repo.id)}
              loading={subscribeMutation.isPending}
              className="flex-shrink-0 text-gray-500 hover:text-violet-600 text-xs"
              title="Subscribe"
            >
              <Link className="h-3.5 w-3.5" />
            </Button>
          )}
        </li>
      ))}
    </ul>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

type FormState = {
  type: DataSourceType;
  name: string;
  baseUrl: string;
  path: string;
  apiToken: string;
};

export function DataSourcesPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<FormState>({
    type: 'GITHUB',
    name: '',
    baseUrl: '',
    path: '',
    apiToken: '',
  });
  const [formError, setFormError] = useState('');
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  const { data: sources, isLoading } = useQuery({
    queryKey: ['datasources'],
    queryFn: () => datasourcesApi.list().then((r) => r.data),
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateDataSourceRequest) => datasourcesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['datasources'] });
      setShowForm(false);
      setForm({ type: 'GITHUB', name: '', baseUrl: '', path: '', apiToken: '' });
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

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError('');
    const req: CreateDataSourceRequest = { type: form.type, name: form.name };
    if (NEEDS_BASEURL.includes(form.type) && form.baseUrl) req.baseUrl = form.baseUrl;
    if (NEEDS_PATH.includes(form.type) && form.path) req.path = form.path;
    if (NEEDS_TOKEN.includes(form.type) && form.apiToken) req.apiToken = form.apiToken;
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
                  onChange={(e) =>
                    setForm({ ...form, type: e.target.value as DataSourceType, baseUrl: '', path: '' })
                  }
                />
                <Input
                  label="Name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. My GitHub account"
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
                      : 'https://github.com'
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
                <Input
                  label="API token"
                  type="password"
                  value={form.apiToken}
                  onChange={(e) => setForm({ ...form, apiToken: e.target.value })}
                  placeholder="••••••••••••"
                />
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
                </div>

                <div className="flex items-center gap-1 flex-shrink-0">
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
                  <ReposPanel dataSourceId={src.id} />
                </div>
              )}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
