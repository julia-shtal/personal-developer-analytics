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
  Moon,
  Flame,
  Scissors,
  GitBranch,
  Eye,
  Zap,
} from 'lucide-react';
import { metricsApi } from '@/api/metrics';
import { KpiCard, Card } from '@/components/ui/Card';
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

  const focusRatioSeries = useQuery({
    queryKey: ['focus-ratio-series', from, to],
    queryFn: () => metricsApi.focusRatioSeries(from, to).then((r) => r.data),
  });

  const issuesCreated = useQuery({
    queryKey: ['daily-issues-created', from, to],
    queryFn: () => metricsApi.dailyIssuesCreated(from, to).then((r) => r.data),
  });

  const issueLeadTime = useQuery({
    queryKey: ['issue-lead-time', from, to],
    queryFn: () => metricsApi.issueLeadTime(from, to).then((r) => r.data),
  });

  const prFirstCommitLeadTime = useQuery({
    queryKey: ['pr-first-commit-lead-time', from, to],
    queryFn: () => metricsApi.prFirstCommitLeadTime(from, to).then((r) => r.data),
  });

  const afterHours = useQuery({
    queryKey: ['after-hours', from, to],
    queryFn: () => metricsApi.dailyAfterHours(from, to).then((r) => r.data),
  });

  const refactorRatio = useQuery({
    queryKey: ['refactor-ratio', from, to],
    queryFn: () => metricsApi.dailyRefactorRatio(from, to).then((r) => r.data),
  });

  const mergeToMain = useQuery({
    queryKey: ['merge-to-main', from, to],
    queryFn: () => metricsApi.mergeToMain(from, to).then((r) => r.data),
  });

  const deepWorkStreak = useQuery({
    queryKey: ['deep-work-streak', from, to],
    queryFn: () => metricsApi.deepWorkStreak(from, to).then((r) => r.data),
  });

  const mergeWithoutReview = useQuery({
    queryKey: ['merge-without-review', from, to],
    queryFn: () => metricsApi.mergeWithoutReview(from, to).then((r) => r.data),
  });

  const prSizeComplexity = useQuery({
    queryKey: ['pr-size-complexity', from, to],
    queryFn: () => metricsApi.prSizeComplexity(from, to).then((r) => r.data),
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
  const firstCommitLeadTimeHrs = prFirstCommitLeadTime.data?.value ?? 0;
  const issueLeadTimeHrs = issueLeadTime.data?.value ?? 0;

  function fmtHours(h: number) {
    if (!h || h <= 0) return '—';
    if (h < 24) return `${h.toFixed(1)}h`;
    return `${(h / 24).toFixed(1)}d`;
  }

  function fmtPct(v: number | undefined | null) {
    if (v == null || v <= 0) return '—';
    return `${(v * 100).toFixed(0)}%`;
  }

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

      {/* Primary KPI row */}
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
          value={fmtHours(leadTimeHrs)}
          subtitle="open → merge, median"
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label="Focus Ratio"
          value={focusRatio.data?.value != null ? fmtPct(focusRatio.data.value) : '—'}
          subtitle="coding days / working days"
          icon={<Target className="h-4 w-4" />}
        />
      </div>

      {/* Secondary KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
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
            ? fmtHours(reviewTime.data.value)
            : '—'}
          subtitle="first review, median"
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label="1st Commit → Merge"
          value={fmtHours(firstCommitLeadTimeHrs)}
          subtitle="lead time from first commit"
          icon={<GitCommit className="h-4 w-4" />}
        />
      </div>

      {/* Tertiary KPIs — issue + churn */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Issues Created"
          value={sum(issuesCreated.data ?? [])}
          icon={<GitBranch className="h-4 w-4" />}
        />
        <KpiCard
          label="Issue Lead Time"
          value={fmtHours(issueLeadTimeHrs)}
          subtitle="open → close, median"
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label="Avg Churn"
          value={avgChurn > 0 ? `${(avgChurn * 100).toFixed(1)}%` : '—'}
          subtitle="deleted / total lines"
          icon={<Scissors className="h-4 w-4" />}
        />
        <KpiCard
          label="Deep Work Streak"
          value={deepWorkStreak.data?.value != null && deepWorkStreak.data.value > 0
            ? `${Math.round(deepWorkStreak.data.value)}d`
            : '—'}
          subtitle="longest active run"
          icon={<Zap className="h-4 w-4" />}
        />
      </div>

      {/* Wellness & Quality KPIs */}
      <div>
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Wellness &amp; Quality</h2>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard
            label="After-Hours Commits"
            value={afterHours.data?.value != null && afterHours.data.value > 0
              ? fmtPct(afterHours.data.value)
              : '—'}
            subtitle="commits outside 09–18 Mon–Fri"
            icon={<Moon className="h-4 w-4" />}
          />
          <KpiCard
            label="Refactor Ratio"
            value={refactorRatio.data?.value != null && refactorRatio.data.value > 0
              ? fmtPct(refactorRatio.data.value)
              : '—'}
            subtitle="commits: deletions > additions"
            icon={<Scissors className="h-4 w-4" />}
          />
          <KpiCard
            label="Merge Without Review"
            value={mergeWithoutReview.data?.value != null && mergeWithoutReview.data.value > 0
              ? fmtPct(mergeWithoutReview.data.value)
              : '—'}
            subtitle="PRs merged with 0 reviews"
            icon={<Eye className="h-4 w-4" />}
          />
          <KpiCard
            label="Merge Frequency"
            value={mergeToMain.data?.value != null && mergeToMain.data.value > 0
              ? `${mergeToMain.data.value.toFixed(1)}/wk`
              : '—'}
            subtitle="merges to main (DORA proxy)"
            icon={<Flame className="h-4 w-4" />}
          />
        </div>
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

        <ChartSection title="Issues" subtitle="Created vs closed per day">
          <div className="space-y-4">
            <div>
              <p className="text-xs font-medium text-gray-500 mb-2">Closed</p>
              <MetricBarChart data={issuesClosed.data ?? []} label="Closed" color="#10b981" />
            </div>
            {(issuesCreated.data?.length ?? 0) > 0 && (
              <div>
                <p className="text-xs font-medium text-gray-500 mb-2">Created</p>
                <MetricBarChart data={issuesCreated.data ?? []} label="Created" color="#0ea5e9" />
              </div>
            )}
          </div>
        </ChartSection>

        {(focusRatioSeries.data?.length ?? 0) > 0 && (
          <ChartSection title="Focus ratio over time" subtitle="Coding days vs working days">
            <MetricLineChart
              data={focusRatioSeries.data ?? []}
              label="Focus ratio"
              color="#7c3aed"
              unit=""
            />
            <p className="mt-2 text-xs text-gray-400">
              1.0 = all working days had commits; 0.0 = no coding.
            </p>
          </ChartSection>
        )}

        {(mergeToMain.data?.value ?? 0) > 0 && (
          <ChartSection title="Merge frequency" subtitle="Merges to main branch — DORA proxy">
            <div className="flex items-center gap-3 py-4">
              <Flame className="h-8 w-8 text-violet-400" />
              <div>
                <p className="text-3xl font-semibold text-gray-900">
                  {mergeToMain.data!.value.toFixed(1)}
                  <span className="text-base font-normal text-gray-400 ml-1">/ week</span>
                </p>
                <p className="text-xs text-gray-400 mt-0.5">
                  Proxy for deployment frequency (no CI/CD data). Label: Merge Frequency.
                </p>
              </div>
            </div>
          </ChartSection>
        )}

        {(prSizeComplexity.data?.value ?? 0) > 0 && (
          <ChartSection title="PR size complexity" subtitle="(additions + deletions) / commits per PR — median">
            <div className="flex items-center gap-3 py-4">
              <GitPullRequest className="h-8 w-8 text-amber-400" />
              <div>
                <p className="text-3xl font-semibold text-gray-900">
                  {prSizeComplexity.data!.value.toFixed(0)}
                  <span className="text-base font-normal text-gray-400 ml-1">lines/commit</span>
                </p>
                <p className="text-xs text-gray-400 mt-0.5">
                  Lower is better — small focused PRs are easier to review.
                </p>
              </div>
            </div>
          </ChartSection>
        )}
      </div>
    </div>
  );
}
