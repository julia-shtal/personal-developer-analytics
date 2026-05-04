import { useState, useMemo } from 'react';
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

  const knowledgeSilo = useQuery({
    queryKey: ['knowledge-silo', from, to],
    queryFn: () => metricsApi.knowledgeSilo(from, to).then((r) => r.data),
  });

  const calculateMutation = useMutation({
    mutationFn: () => metricsApi.calculate(from, to),
    onSuccess: () => qc.invalidateQueries({ queryKey: [] }),
  });

  // The backend only stores a value=1 snapshot for days with commits; zero-commit
  // weekdays have no row. Fill in the gaps so the chart shows true on/off activity
  // rather than a flat line at 1.0 connecting only the active days.
  const focusRatioFilled = useMemo(() => {
    const activeDates = new Set((focusRatioSeries.data ?? []).map((p) => p.date));
    const result: { date: string; value: number; metricType: string }[] = [];
    const end = new Date(to + 'T00:00:00');
    const cur = new Date(from + 'T00:00:00');
    while (cur <= end) {
      const dow = cur.getDay(); // 0 = Sun, 6 = Sat
      if (dow !== 0 && dow !== 6) {
        const dateStr = cur.toISOString().slice(0, 10);
        result.push({ date: dateStr, value: activeDates.has(dateStr) ? 1 : 0, metricType: 'FOCUS_RATIO_DAYS_TASKS' });
      }
      cur.setDate(cur.getDate() + 1);
    }
    return result;
  }, [focusRatioSeries.data, from, to]);

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
          tooltip="Number of Git commits authored in the selected period."
        />
        <KpiCard
          label="PRs Merged"
          value={totalPrsMerged}
          subtitle="in period"
          icon={<GitMerge className="h-4 w-4" />}
          tooltip="Pull requests merged to a target branch in the selected period."
        />
        <KpiCard
          label="PR Lead Time"
          value={fmtHours(leadTimeHrs)}
          subtitle="open → merge, median"
          icon={<Clock className="h-4 w-4" />}
          tooltip="Median time from when a PR is opened to when it is merged."
        />
        <KpiCard
          label="Focus Ratio"
          value={focusRatio.data?.value != null ? fmtPct(focusRatio.data.value) : '—'}
          subtitle="coding days / working days"
          icon={<Target className="h-4 w-4" />}
          tooltip="Fraction of working days (Mon–Fri) on which you made at least one commit."
        />
      </div>

      {/* Secondary KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="PRs Created"
          value={sum(prCreated.data ?? [])}
          icon={<GitPullRequest className="h-4 w-4" />}
          tooltip="Pull requests opened by you in the selected period."
        />
        <KpiCard
          label="Issues Closed"
          value={sum(issuesClosed.data ?? [])}
          icon={<Target className="h-4 w-4" />}
          tooltip="Issues resolved or closed by you in the selected period."
        />
        <KpiCard
          label="Review Response"
          value={reviewTime.data?.value != null && reviewTime.data.value > 0
            ? fmtHours(reviewTime.data.value)
            : '—'}
          subtitle="first review, median"
          icon={<Clock className="h-4 w-4" />}
          tooltip="Median time from PR open to receiving the first review comment or approval."
        />
        <KpiCard
          label="1st Commit → Merge"
          value={fmtHours(firstCommitLeadTimeHrs)}
          subtitle="lead time from first commit"
          icon={<GitCommit className="h-4 w-4" />}
          tooltip="Median time from the first commit on a PR branch to the PR being merged."
        />
      </div>

      {/* Tertiary KPIs — issue + churn */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Issues Created"
          value={sum(issuesCreated.data ?? [])}
          icon={<GitBranch className="h-4 w-4" />}
          tooltip="Issues opened in the selected period across all connected trackers."
        />
        <KpiCard
          label="Issue Lead Time"
          value={fmtHours(issueLeadTimeHrs)}
          subtitle="open → close, median"
          icon={<Clock className="h-4 w-4" />}
          tooltip="Median time from issue creation to it being marked as closed or resolved."
        />
        <KpiCard
          label="Avg Churn"
          value={avgChurn > 0 ? `${(avgChurn * 100).toFixed(1)}%` : '—'}
          subtitle="deleted / total lines"
          icon={<Scissors className="h-4 w-4" />}
          tooltip="Average daily ratio of deleted lines to total changed lines. High churn may indicate rework or rewrites."
        />
        <KpiCard
          label="Deep Work Streak"
          value={deepWorkStreak.data?.value != null && deepWorkStreak.data.value > 0
            ? `${Math.round(deepWorkStreak.data.value)}d`
            : '—'}
          subtitle="longest active run"
          icon={<Zap className="h-4 w-4" />}
          tooltip="Longest consecutive run of days on which you authored at least one commit."
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
            tooltip="Share of commits made outside 09:00–18:00 Mon–Fri in your local timezone. High values may indicate unsustainable working patterns."
          />
          <KpiCard
            label="Refactor Ratio"
            value={refactorRatio.data?.value != null && refactorRatio.data.value > 0
              ? fmtPct(refactorRatio.data.value)
              : '—'}
            subtitle="commits: deletions > additions"
            icon={<Scissors className="h-4 w-4" />}
            tooltip="Share of commits where deleted lines outnumber added lines — a proxy for cleanup and refactoring activity."
          />
          <KpiCard
            label="Merge Without Review"
            value={mergeWithoutReview.data?.value != null && mergeWithoutReview.data.value > 0
              ? fmtPct(mergeWithoutReview.data.value)
              : '—'}
            subtitle="PRs merged with 0 reviews"
            icon={<Eye className="h-4 w-4" />}
            tooltip="Share of merged PRs that had zero reviewer approvals or comments before merge."
          />
          <KpiCard
            label="Merge Frequency"
            value={mergeToMain.data?.value != null && mergeToMain.data.value > 0
              ? `${mergeToMain.data.value.toFixed(1)}/wk`
              : '—'}
            subtitle="merges to main (DORA proxy)"
            icon={<Flame className="h-4 w-4" />}
            tooltip="Average number of merges to the main branch per week. Used as a proxy for DORA deployment frequency."
          />
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
          <KpiCard
            label="Knowledge Silo"
            value={knowledgeSilo.data?.value != null && knowledgeSilo.data.value > 0
              ? fmtPct(knowledgeSilo.data.value)
              : '—'}
            subtitle="max repo ownership share"
            icon={<GitBranch className="h-4 w-4" />}
            tooltip="Your highest commit share across all repos. A high value means you are the sole owner of that repo's knowledge — a bus-factor risk."
          />
          <KpiCard
            label="PR Size Complexity"
            value={prSizeComplexity.data?.value != null && prSizeComplexity.data.value > 0
              ? `${prSizeComplexity.data.value.toFixed(0)} ln`
              : '—'}
            subtitle="lines/commit, median"
            icon={<GitPullRequest className="h-4 w-4" />}
            tooltip="Median (additions + deletions) per commit across your PRs. Lower values mean smaller, more focused changes that are easier to review."
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

        {focusRatioFilled.some((p) => p.value > 0) && (
          <ChartSection title="Focus ratio over time" subtitle="Weekdays with at least one commit">
            <MetricBarChart
              data={focusRatioFilled}
              label="Active day"
              color="#7c3aed"
            />
            <p className="mt-2 text-xs text-gray-400">
              1 = had commits that day; 0 = no commits. The card percentage is active days / total weekdays in the period.
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
