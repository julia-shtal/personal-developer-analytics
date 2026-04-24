import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Database, GitBranch, Layers, AlertCircle } from 'lucide-react';
import { datasourcesApi } from '@/api/datasources';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { PageSpinner } from '@/components/ui/Spinner';
import type { DataSourceType, CreateDataSourceRequest } from '@/types';

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
  { value: 'GIT_LOCAL', label: 'Local Git repository' },
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'JIRA', label: 'Jira' },
  { value: 'GITHUB_ISSUES', label: 'GitHub Issues' },
];

const NEEDS_URL: DataSourceType[] = ['GIT_LOCAL', 'JIRA'];
const NEEDS_TOKEN: DataSourceType[] = ['GITHUB', 'JIRA', 'GITHUB_ISSUES'];

export function DataSourcesPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CreateDataSourceRequest>({
    type: 'GITHUB',
    name: '',
    url: '',
    apiToken: '',
  });
  const [formError, setFormError] = useState('');

  const { data: sources, isLoading } = useQuery({
    queryKey: ['datasources'],
    queryFn: () => datasourcesApi.list().then((r) => r.data),
  });

  const createMutation = useMutation({
    mutationFn: (req: CreateDataSourceRequest) => datasourcesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['datasources'] });
      setShowForm(false);
      setForm({ type: 'GITHUB', name: '', url: '', apiToken: '' });
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
    if (NEEDS_URL.includes(form.type) && form.url) req.url = form.url;
    if (NEEDS_TOKEN.includes(form.type) && form.apiToken) req.apiToken = form.apiToken;
    createMutation.mutate(req);
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
                  onChange={(e) => setForm({ ...form, type: e.target.value as DataSourceType })}
                />
                <Input
                  label="Name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. My GitHub account"
                  required
                />
              </div>

              {NEEDS_URL.includes(form.type) && (
                <Input
                  label={form.type === 'GIT_LOCAL' ? 'Repository path' : 'URL'}
                  value={form.url}
                  onChange={(e) => setForm({ ...form, url: e.target.value })}
                  placeholder={form.type === 'GIT_LOCAL' ? '/home/user/my-project' : 'https://yourcompany.atlassian.net'}
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
        {sources?.map((src) => (
          <Card key={src.id} className="hover:shadow-sm transition-shadow">
            <div className="flex items-center gap-4 px-5 py-4">
              <div className="flex-shrink-0 p-2.5 rounded-lg bg-gray-50 text-gray-500">
                <TypeIcon type={src.type} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-sm font-semibold text-gray-900">{src.name}</p>
                  <Badge color={TYPE_COLORS[src.type]}>{TYPE_LABELS[src.type]}</Badge>
                </div>
                {src.url && <p className="text-xs text-gray-400 mt-0.5 truncate">{src.url}</p>}
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => deleteMutation.mutate(src.id)}
                loading={deleteMutation.isPending}
                className="flex-shrink-0 text-gray-400 hover:text-red-600"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
