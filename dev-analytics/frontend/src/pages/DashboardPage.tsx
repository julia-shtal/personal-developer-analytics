import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  GitCommit,
  GitPullRequest,
  GitMerge,
  Clock,
  Target,
  RefreshCw,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { metricsApi } from '@/api/metrics';
import { KpiCard } from '@/components/ui/Card';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { DateRangePicker } from '@/components/DateRangePicker';
import { MetricLineChart } from '@/components/charts/MetricLineChart';
import { MetricBarChart } from '@/components/charts/MetricBarChart';
import { PageSpinner } from '@/components/ui/Spinner';
import { PRESET_RANGES } from '@/lib/dates';
import type { DateRange } from '@/types';

function sum(data: { value: number }[]) {
  return data.reduce((a, b) => a + b.value, 0);
}

function avg(data: { value: number }[]) {
  if (!data.length) return 0;
  return data.reduce((a, b) => a + b.value, 0) / data.length;
}

interface ChartSectionProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

function ChartSection({ title, subtitle, children }: ChartSectionProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <Card>
      <button
        className="w-full text-left px-5 py-4 flex items-center justify-between group"
        onClick={() => setExpanded((v) => !v)}
      >
        <div>
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
          {subtitle && <p className="text-xs text-gray-400 mt-0.5">{subtitle}</p>}
        </div>
        {expanded
          ? <ChevronUp className="h-4 w-4 text-gray-400 group-hover:text-gray-600" />
          : <ChevronDown className="h-4 w-4 text-gray-400 group-hover:text-gray-600" />
        }
      </button>
      {expanded && <div className="px-5 pb-5">{children}</div>}
    </Card>
  );
}

export function DashboardPage() {
  const [range, setRange] = useState<DateRange>(PRESET_RANGES[1].range); // last 30 days
  const qc = useQueryClient();

  const { from, to } = range;

  const commits = useQuery({
    queryKey: ['daily-commits', from, to],
    queryFn: () => metricsApi.dailyCommits(from, to).then((r) => r.data),
  });

  const prCreated = useQuery({
    queryKey: ['daily-pr-created', from, to],
    queryFn: () => metricsApi.dailyPrCreated(from, to).then((r) => r.data),
  });

  const prMerged = useQuery({
    queryKey: ['daily-pr-merged', from, to],
    queryFn: () => metricsApi.dailyPrMerged(from, to).then((r) => r.data),
  });

  const churn = useQuery({
    queryKey: ['daily-churn', from, to],
    queryFn: () => metricsApi.dailyChurn(from, to).then((r) => r.data),
  });

  const issuesClosed = useQuery({
    queryKey: ['daily-issues-closed', from, to],
    queryFn: () => metricsApi.dailyIssuesClosed(from, to).then((r) => r.data),
  });

  const prLeadTime = useQuery({
    queryKey: ['pr-lead-time', from, to],
    queryFn: () => metricsApi.prLeadTime(from, to).then((r) => r.data),
  });

  const reviewTime = useQuery({
    queryKey: ['review-response-time', from, to],
    queryFn: () => metricsApi.reviewResponseTime(from, to).then((r) => r.data),
  });

  const focusRatio = useQuery({
    queryKey: ['focus-ratio', from, to],
    queryFn: () => metricsApi.focusRatio(from, to).then((r) => r.data),
  });

  const calculateMutation = useMutation({
    mutationFn: () => metricsApi.calculate(from, to),
    onSuccess: () => qc.invalidateQueries({ queryKey: [] }),
  });

  const isLoading = commits.isLoading && prCreated.isLoading;
  if (isLoading) return <PageSpinner />;

  const totalCommits = sum(commits.data ?? []);
  const totalPrsMerged = sum(prMerged.data ?? []);
  const avgChurn = avg(churn.data ?? []);
  const leadTimeHrs = prLeadTime.data?.value ?? 0;

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Personal Dashboard</h1>
          <p className="text-sm text-gray-500 mt-0.5">Your activity in the selected period</p>
        </div>
        <div className="flex items-center gap-2">
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

      {/* KPI row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Total Commits"
          value={totalCommits}
          subtitle="in period"
          icon={<GitCommit className="h-4 w-4" />}
        />
        <KpiCard
          label="PRs Merged"
          value={totalPrsMerged}
          subtitle="in period"
          icon={<GitMerge className="h-4 w-4" />}
        />
        <KpiCard
          label="PR Lead Time"
          value={leadTimeHrs > 0 ? `${leadTimeHrs.toFixed(1)}h` : '—'}
          subtitle="median"
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label="Focus Ratio"
          value={focusRatio.data?.value != null ? `${(focusRatio.data.value * 100).toFixed(0)}%` : '—'}
          subtitle="coding days / working days"
          icon={<Target className="h-4 w-4" />}
        />
      </div>

      {/* Secondary KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
        <KpiCard
          label="PRs Created"
          value={sum(prCreated.data ?? [])}
          icon={<GitPullRequest className="h-4 w-4" />}
        />
        <KpiCard
          label="Issues Closed"
          value={sum(issuesClosed.data ?? [])}
          icon={<Target className="h-4 w-4" />}
        />
        <KpiCard
          label="Review Response"
          value={reviewTime.data?.value != null && reviewTime.data.value > 0
            ? `${reviewTime.data.value.toFixed(1)}h`
            : '—'}
          subtitle="median time to first review"
          icon={<Clock className="h-4 w-4" />}
        />
      </div>

      {/* Charts — progressive disclosure */}
      <div className="space-y-3">
        <ChartSection title="Commits over time" subtitle="Daily commit count">
          <MetricBarChart
            data={commits.data ?? []}
            label="Commits"
            color="#7c3aed"
          />
        </ChartSection>

        <ChartSection title="Pull request activity" subtitle="PRs created and merged per day">
          <div className="space-y-4">
            <div>
              <p className="text-xs font-medium text-gray-500 mb-2">Created</p>
              <MetricLineChart data={prCreated.data ?? []} label="Created" color="#0ea5e9" />
            </div>
            <div>
              <p className="text-xs font-medium text-gray-500 mb-2">Merged</p>
              <MetricLineChart data={prMerged.data ?? []} label="Merged" color="#10b981" />
            </div>
          </div>
        </ChartSection>

        <ChartSection title="Code churn ratio" subtitle="Rewrites as a fraction of total changes">
          <MetricLineChart
            data={churn.data ?? []}
            label="Churn ratio"
            color="#f59e0b"
            unit=""
          />
          <p className="mt-2 text-xs text-gray-400">
            Churn ratio = lines deleted / (lines added + lines deleted). High churn may indicate rework.
          </p>
        </ChartSection>

        <ChartSection title="Issues closed" subtitle="Daily closed issue count">
          <MetricBarChart
            data={issuesClosed.data ?? []}
            label="Issues closed"
            color="#10b981"
          />
        </ChartSection>

        {avgChurn > 0 && (
          <div className="mt-2">
            <Card>
              <CardHeader>
                <h3 className="text-sm font-semibold text-gray-900">Avg daily churn</h3>
              </CardHeader>
              <CardBody>
                <p className="text-3xl font-semibold text-gray-900">{(avgChurn * 100).toFixed(1)}%</p>
                <p className="text-xs text-gray-400 mt-1">averaged over the selected period</p>
              </CardBody>
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
