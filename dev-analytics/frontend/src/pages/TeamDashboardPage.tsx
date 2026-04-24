import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Users, TrendingUp } from 'lucide-react';
import { teamsApi } from '@/api/teams';
import { teamMetricsApi } from '@/api/metrics';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { DateRangePicker } from '@/components/DateRangePicker';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { PageSpinner } from '@/components/ui/Spinner';
import { PRESET_RANGES } from '@/lib/dates';
import type { DateRange, Team } from '@/types';

function fmt(v: number | undefined, decimals = 1) {
  if (v == null || v === 0) return '—';
  return v.toFixed(decimals);
}

export function TeamDashboardPage() {
  const qc = useQueryClient();
  const [range, setRange] = useState<DateRange>(PRESET_RANGES[1].range);
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null);
  const { from, to } = range;

  const { data: teams, isLoading: teamsLoading } = useQuery<Team[]>({
    queryKey: ['teams'],
    queryFn: () => teamsApi.list().then((r) => r.data),
  });

  // Set default team once loaded
  const activeTeamId = selectedTeamId ?? teams?.[0]?.id ?? null;

  const { data: commitSeries, isLoading: commitsLoading } = useQuery({
    queryKey: ['team-commits', activeTeamId, from, to],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.dailyCommits(activeTeamId, from, to).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !!activeTeamId,
  });

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['team-summary', activeTeamId, from, to],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.summary(activeTeamId, from, to).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !!activeTeamId,
  });

  const calculateMutation = useMutation({
    mutationFn: () => {
      if (!activeTeamId) return Promise.reject(new Error('No team selected'));
      return teamMetricsApi.calculate(activeTeamId, from, to);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['team-commits', activeTeamId] }),
  });

  if (teamsLoading) return <PageSpinner />;

  if (!teams?.length) {
    return (
      <div className="p-6 text-center py-24 text-gray-400">
        <Users className="h-10 w-10 mx-auto mb-3 opacity-30" />
        <p className="text-sm">No teams found. You need to be a manager of at least one team.</p>
      </div>
    );
  }

  const activeTeam = teams.find((t) => t.id === activeTeamId);

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Team Dashboard</h1>
          <p className="text-sm text-gray-500 mt-0.5">Overview across all team members</p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {teams.length > 1 && (
            <div className="flex gap-1">
              {teams.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setSelectedTeamId(t.id)}
                  className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                    t.id === activeTeamId
                      ? 'bg-violet-600 text-white border-violet-600'
                      : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {t.name}
                </button>
              ))}
            </div>
          )}
          <DateRangePicker value={range} onChange={setRange} />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => calculateMutation.mutate()}
            loading={calculateMutation.isPending}
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Recalculate
          </Button>
        </div>
      </div>

      {/* Team commits chart */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">
              Daily commits — {activeTeam?.name}
            </h2>
          </div>
          <p className="text-xs text-gray-400 mt-0.5">Per-member breakdown over the period</p>
        </CardHeader>
        <CardBody>
          {commitsLoading ? (
            <div className="h-64 flex items-center justify-center text-gray-400 text-sm">Loading…</div>
          ) : commitSeries?.length ? (
            <MultiLineChart data={commitSeries.filter((d) => d.username !== 'team')} />
          ) : (
            <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
              No data — try recalculating for this period.
            </div>
          )}
        </CardBody>
      </Card>

      {/* Member summary table */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Users className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">Member summary</h2>
          </div>
        </CardHeader>
        <CardBody className="px-0 py-0">
          {summaryLoading ? (
            <div className="py-12 flex items-center justify-center text-gray-400 text-sm">Loading…</div>
          ) : summary?.length ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="text-left px-5 py-3 font-medium text-gray-500 text-xs">Member</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-500 text-xs">Commits</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-500 text-xs">PRs merged</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-500 text-xs">PRs created</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-500 text-xs">Issues closed</th>
                    <th className="text-right px-5 py-3 font-medium text-gray-500 text-xs">Churn avg</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.map((m) => (
                    <tr key={m.userId} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-violet-100 flex items-center justify-center text-violet-700 text-xs font-semibold">
                            {m.username[0].toUpperCase()}
                          </div>
                          <span className="font-medium text-gray-900">{m.username}</span>
                        </div>
                      </td>
                      <td className="text-right px-4 py-3 text-gray-700 font-medium">
                        {fmt(m.metrics.DAILY_COMMITS_COUNT, 0)}
                      </td>
                      <td className="text-right px-4 py-3 text-gray-700">
                        {fmt(m.metrics.DAILY_PR_MERGED, 0)}
                      </td>
                      <td className="text-right px-4 py-3 text-gray-700">
                        {fmt(m.metrics.DAILY_PR_CREATED, 0)}
                      </td>
                      <td className="text-right px-4 py-3 text-gray-700">
                        {fmt(m.metrics.DAILY_ISSUES_CLOSED, 0)}
                      </td>
                      <td className="text-right px-5 py-3">
                        {m.metrics.DAILY_CHURN_RATIO != null && m.metrics.DAILY_CHURN_RATIO > 0 ? (
                          <Badge color={m.metrics.DAILY_CHURN_RATIO > 0.5 ? 'amber' : 'emerald'}>
                            {(m.metrics.DAILY_CHURN_RATIO * 100).toFixed(0)}%
                          </Badge>
                        ) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="py-12 flex items-center justify-center text-gray-400 text-sm">
              No summary data — recalculate to populate.
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
