import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { goalsApi } from '@/api/goals';
import type { GoalRequestDto } from '@/api/goals';
import { metricsApi } from '@/api/metrics';
import { MetricBarChart } from '@/components/charts/MetricBarChart';
import { Sparkline } from '@/components/charts/Sparkline';
import { KpiTile } from '@/components/ui/KpiTile';
import { Chip } from '@/components/ui/Chip';
import { PageSpinner } from '@/components/ui/Spinner';
import { useDateRange } from '@/context/DateRangeContext';
import { useRepoScope } from '@/context/RepoScopeContext';
import { RepoSelector } from '@/components/RepoSelector';
import { AiSummaryCard } from '@/components/ai/AiSummaryCard';
import { useCompareRange } from '@/hooks/useCompareRange';
import { CompareToggle } from '@/components/charts/CompareToggle';
import { CompareDelta } from '@/components/charts/CompareDelta';
import { formatDate } from '@/lib/dates';
import { reposApi } from '@/api/repos';
import type { MetricsSummaryDto } from '@/types/ai';
import {
  Commits, PRMerged, LeadTime, Focus, PRCreated, IssuesClosed,
  Review, FirstCommit, AfterHours, Refactor, NoReview, MergeFreq,
  DeepWork, Churn, Silo, PRSize,
} from '@/components/icons';

function numSuffix(
  val: number | undefined | null,
  unit: string,
  fmt: (v: number) => string = (v) => v.toFixed(1),
): ReactNode {
  if (!val || val <= 0) return '—';
  return (
    <span>
      {fmt(val)}
      <em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 4 }}>{unit}</em>
    </span>
  );
}

function pctSuffix(val: number | undefined | null): ReactNode {
  if (val == null || val <= 0) return '—';
  return (
    <span>
      {Math.round(val * 100)}
      <em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 2 }}>%</em>
    </span>
  );
}

function sum(data: { value: number }[]) {
  return data.reduce((a, b) => a + b.value, 0);
}

function avg(data: { value: number }[]) {
  if (!data.length) return 0;
  return data.reduce((a, b) => a + b.value, 0) / data.length;
}

export function DashboardPage() {
  const { range } = useDateRange();
  const { repoId } = useRepoScope();
  const qc = useQueryClient();
  const { from, to } = range;

  const [aiSummary, setAiSummary] = useState<MetricsSummaryDto | null>(null);

  const { data: allRepos } = useQuery({
    queryKey: ['repos'],
    queryFn: () => reposApi.list().then((r) => r.data),
    staleTime: 5 * 60_000,
  });
  const selectedRepo = allRepos?.find((r) => r.id === repoId) ?? null;

  const rId = repoId ?? undefined;

  const commits = useQuery({
    queryKey: ['daily-commits-count', from, to, repoId],
    queryFn: () => metricsApi.dailyCommits(from, to, rId).then((r) => r.data),
  });

  const commitsCompareRange = useCompareRange(range);
  const commitsCompareQuery = useQuery({
    queryKey: ['daily-commits-count', commitsCompareRange.compareRange.from, commitsCompareRange.compareRange.to, repoId, 'compare'],
    queryFn: () => metricsApi.dailyCommits(commitsCompareRange.compareRange.from, commitsCompareRange.compareRange.to, rId).then((r) => r.data),
    enabled: commitsCompareRange.enabled,
  });

  const prCreated = useQuery({
    queryKey: ['daily-pr-created', from, to, repoId],
    queryFn: () => metricsApi.dailyPrCreated(from, to, rId).then((r) => r.data),
  });

  const prMerged = useQuery({
    queryKey: ['daily-pr-merged', from, to, repoId],
    queryFn: () => metricsApi.dailyPrMerged(from, to, rId).then((r) => r.data),
  });

  const prFlowCompareRange = useCompareRange(range);
  const prCreatedCompareQuery = useQuery({
    queryKey: ['daily-pr-created', prFlowCompareRange.compareRange.from, prFlowCompareRange.compareRange.to, repoId, 'compare'],
    queryFn: () => metricsApi.dailyPrCreated(prFlowCompareRange.compareRange.from, prFlowCompareRange.compareRange.to, rId).then((r) => r.data),
    enabled: prFlowCompareRange.enabled,
  });
  const prMergedCompareQuery = useQuery({
    queryKey: ['daily-pr-merged', prFlowCompareRange.compareRange.from, prFlowCompareRange.compareRange.to, repoId, 'compare'],
    queryFn: () => metricsApi.dailyPrMerged(prFlowCompareRange.compareRange.from, prFlowCompareRange.compareRange.to, rId).then((r) => r.data),
    enabled: prFlowCompareRange.enabled,
  });

  const churn = useQuery({
    queryKey: ['daily-churn-ratio', from, to, repoId],
    queryFn: () => metricsApi.dailyChurn(from, to, rId).then((r) => r.data),
  });

  const issuesClosed = useQuery({
    queryKey: ['daily-issues-closed', from, to, repoId],
    queryFn: () => metricsApi.dailyIssuesClosed(from, to, rId).then((r) => r.data),
  });

  const prLeadTime = useQuery({
    queryKey: ['pr-lead-time', from, to, repoId],
    queryFn: () => metricsApi.prLeadTime(from, to, rId).then((r) => r.data),
  });

  const reviewTime = useQuery({
    queryKey: ['review-response-time', from, to, repoId],
    queryFn: () => metricsApi.reviewResponseTime(from, to, rId).then((r) => r.data),
  });

  const focusRatio = useQuery({
    queryKey: ['focus-ratio', from, to],
    queryFn: () => metricsApi.focusRatio(from, to).then((r) => r.data),
  });

  const issuesCreated = useQuery({
    queryKey: ['daily-issues-created', from, to, repoId],
    queryFn: () => metricsApi.dailyIssuesCreated(from, to, rId).then((r) => r.data),
  });

  const issuesCompareRange = useCompareRange(range);
  const issuesClosedCompareQuery = useQuery({
    queryKey: ['daily-issues-closed', issuesCompareRange.compareRange.from, issuesCompareRange.compareRange.to, repoId, 'compare'],
    queryFn: () => metricsApi.dailyIssuesClosed(issuesCompareRange.compareRange.from, issuesCompareRange.compareRange.to, rId).then((r) => r.data),
    enabled: issuesCompareRange.enabled,
  });
  const issuesCreatedCompareQuery = useQuery({
    queryKey: ['daily-issues-created', issuesCompareRange.compareRange.from, issuesCompareRange.compareRange.to, repoId, 'compare'],
    queryFn: () => metricsApi.dailyIssuesCreated(issuesCompareRange.compareRange.from, issuesCompareRange.compareRange.to, rId).then((r) => r.data),
    enabled: issuesCompareRange.enabled,
  });

  const issueLeadTime = useQuery({
    queryKey: ['issue-lead-time', from, to, repoId],
    queryFn: () => metricsApi.issueLeadTime(from, to, rId).then((r) => r.data),
  });

  const prFirstCommitLeadTime = useQuery({
    queryKey: ['pr-first-commit-to-merge-lead-time', from, to, repoId],
    queryFn: () => metricsApi.prFirstCommitLeadTime(from, to, rId).then((r) => r.data),
  });

  const afterHours = useQuery({
    queryKey: ['after-hours', from, to],
    queryFn: () => metricsApi.dailyAfterHours(from, to).then((r) => r.data),
  });

  const refactorRatio = useQuery({
    queryKey: ['refactor-ratio', from, to],
    queryFn: () => metricsApi.dailyRefactorRatio(from, to).then((r) => r.data),
  });

  const commitsPerWeekAvg = useQuery({
    queryKey: ['commits-per-week-avg', from, to],
    queryFn: () => metricsApi.commitsPerWeekAvg(from, to).then((r) => r.data),
  });

  const deepWorkStreak = useQuery({
    queryKey: ['deep-work-streak', from, to],
    queryFn: () => metricsApi.deepWorkStreak(from, to).then((r) => r.data),
  });

  const mergeWithoutReview = useQuery({
    queryKey: ['merge-without-review-ratio', from, to, repoId],
    queryFn: () => metricsApi.mergeWithoutReview(from, to, rId).then((r) => r.data),
  });

  const prSizeComplexity = useQuery({
    queryKey: ['pr-size-complexity', from, to, repoId],
    queryFn: () => metricsApi.prSizeComplexity(from, to, rId).then((r) => r.data),
  });

  const wipOpenPrAge = useQuery({
    queryKey: ['wip-open-pr-age', from, to, repoId],
    queryFn: () => metricsApi.wipOpenPrAge(from, to, rId).then((r) => r.data),
  });

  const knowledgeSilo = useQuery({
    queryKey: ['knowledge-silo-score', from, to, repoId],
    queryFn: () => metricsApi.knowledgeSilo(from, to, rId).then((r) => r.data),
  });

  const reviewParticipation = useQuery({
    queryKey: ['review-participation', from, to],
    queryFn: () => metricsApi.reviewParticipation(from, to).then((r) => r.data),
  });

  const freshness = useQuery({
    queryKey: ['metrics-freshness'],
    queryFn: () => metricsApi.freshness().then((r) => r.data),
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const anomalies = useQuery({
    queryKey: ['metric-anomalies', from, to],
    queryFn: () => metricsApi.anomalies(from, to).then((r) => r.data),
    staleTime: 1000 * 60 * 5,
    retry: false,
  });

  const { data: goals } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.list(),
  });

  const recalculateMutation = useMutation({
    mutationFn: () => metricsApi.calculate(from, to),
    onSuccess: () => qc.invalidateQueries({ queryKey: [] }),
    onSettled: () => window.dispatchEvent(new CustomEvent('da:recalculate-done')),
  });

  const [goalModal, setGoalModal] = useState<{ metricType: string; label: string } | null>(null);
  const [goalForm, setGoalForm] = useState<Omit<GoalRequestDto, 'metricType'>>({ targetValue: 0, targetDate: '' });

  const createGoalMutation = useMutation({
    mutationFn: (req: GoalRequestDto) => goalsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['goals'] });
      setGoalModal(null);
    },
  });

  function openGoalModal(metricType: string, label: string) {
    setGoalForm({ targetValue: 0, targetDate: '' });
    setGoalModal({ metricType, label });
  }

  function handleGoalSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!goalModal || !goalForm.targetDate || goalForm.targetValue < 0) return;
    createGoalMutation.mutate({ metricType: goalModal.metricType, ...goalForm });
  }

  useEffect(() => {
    const h = () => {
      window.dispatchEvent(new CustomEvent('da:recalculate-start'));
      recalculateMutation.mutate();
    };
    window.addEventListener('da:recalculate', h);
    return () => window.removeEventListener('da:recalculate', h);
  }, [recalculateMutation]);

  const isLoading = commits.isLoading && prCreated.isLoading;
  if (isLoading) return <PageSpinner />;

  const totalCommits = sum(commits.data ?? []);
  const totalPrsMerged = sum(prMerged.data ?? []);
  const avgChurnVal = avg(churn.data ?? []);
  const leadTimeHrs = prLeadTime.data?.value ?? 0;
  const firstCommitLeadTimeHrs = prFirstCommitLeadTime.data?.value ?? 0;
  const deepWorkDays = Math.round(deepWorkStreak.data?.value ?? 0);
  const peakCommits = commits.data?.length ? Math.max(...commits.data.map((d) => d.value)) : 0;
  const reviewParticipationCount = Math.round(reviewParticipation.data?.value ?? 0);

  // issueLeadTime kept for future use — data is fetched but not yet shown in KPI tiles
  void issueLeadTime;

  const METRIC_LABELS: Partial<Record<string, string>> = {
    DAILY_COMMITS_COUNT: 'Daily Commits',
    DAILY_PR_CREATED: 'PRs Created',
    DAILY_PR_MERGED: 'Merged PRs',
    DAILY_ISSUES_CREATED: 'Issues Created',
    DAILY_ISSUES_CLOSED: 'Issues Closed',
    DAILY_CHURN_RATIO: 'Churn Ratio',
    PR_LEAD_TIME_HOURS_MEDIAN: 'PR Lead Time',
    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: 'First Commit to Merge',
    ISSUE_LEAD_TIME_HOURS_MEDIAN: 'Issue Lead Time',
    REVIEW_RESPONSE_TIME_HOURS_MEDIAN: 'Review Response Time',
    FOCUS_RATIO_DAYS_TASKS: 'Focus Ratio',
  };

  const anomalousMetricNames = Object.entries(anomalies.data ?? {})
    .filter(([, v]) => v)
    .map(([k]) => METRIC_LABELS[k] ?? k);

  return (
    <div className="page fade-in">

      {/* ── Hero ──────────────────────────────────────────── */}
      <div style={{ marginBottom: 36 }}>
        <div className="row" style={{ marginBottom: 14, gap: 12, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between' }}>
          <div className="row" style={{ gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
            <div className="t-eyebrow">── Personal · {formatDate(from)} → {formatDate(to)}</div>
            {freshness.data?.metricsComputedThrough && (
              <Chip color="amber">metrics through {freshness.data.metricsComputedThrough}</Chip>
            )}
            {!!freshness.data?.daysRemaining && (
              <Chip color="amber">{freshness.data.daysRemaining} day(s) of history still computing</Chip>
            )}
            {selectedRepo && (
              <Chip color="cyan">{selectedRepo.name}</Chip>
            )}
          </div>
          <div className="row" style={{ gap: 8, alignItems: 'center' }}>
            <RepoSelector />
          </div>
        </div>
        <h1 className="t-h1" style={{ textAlign: 'justify' }}>
          <em>{totalCommits} commits</em>, <em>{totalPrsMerged} PRs merged</em>,
          {' '}a <em>{deepWorkDays}-day</em> deep-work streak
          {aiSummary?.headline ? ` — ${aiSummary.headline}` : '.'}
        </h1>
        <p className="t-body" style={{ marginTop: 18, textAlign: 'justify' }}>
          {selectedRepo
            ? `Showing metrics for ${selectedRepo.name}.`
            : (aiSummary?.overview ?? 'Generate an AI summary to see your narrative overview.')}
        </p>
      </div>

      {/* ── Commits hero card ────────────────────────────── */}
      <div className="card" style={{ padding: 22, marginBottom: 32 }}>
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
          <div>
            <div className="t-eyebrow">commits — daily</div>
            <div className="t-number-hero" style={{ marginTop: 8 }}>
              {totalCommits}
              <em style={{ fontSize: '0.4em', marginLeft: 8, color: 'var(--fg-3)', fontStyle: 'normal' }}>total</em>
            </div>
          </div>
          <Chip accent dot>{commits.data?.length ?? 0} days</Chip>
        </div>
        <div style={{ overflow: 'hidden' }}>
          <Sparkline data={commits.data ?? []} color="var(--accent)" height={70} width={800} area responsive />
        </div>
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
          <span className="t-label">{formatDate(from)}</span>
          <span className="t-label">{formatDate(to)}</span>
        </div>
      </div>

      {/* ── Velocity ─────────────────────────────────────── */}
      <div className="hr-label"><span>velocity</span></div>
      <div className="card">
        <div className="grid-kpi">
          <KpiTile
            label="commits"
            value={totalCommits}
            sub={`in ${commits.data?.length ?? 0} days`}
            accent="accent"
            icon={<Commits />}
            tooltip="Number of Git commits authored in the selected period."
            anomaly={anomalies.data?.DAILY_COMMITS_COUNT}
            onSetGoal={() => openGoalModal('DAILY_COMMITS_COUNT', 'commits')}
          />
          <KpiTile
            label="prs merged"
            value={totalPrsMerged}
            sub="to default branch"
            accent="accent"
            icon={<PRMerged />}
            tooltip="Pull requests merged to a target branch in the selected period."
            anomaly={anomalies.data?.DAILY_PR_MERGED}
            onSetGoal={() => openGoalModal('DAILY_PR_MERGED', 'prs merged')}
          />
          <KpiTile
            label="pr lead time"
            value={numSuffix(leadTimeHrs, 'h')}
            sub="open → merge · median"
            accent="cyan"
            icon={<LeadTime />}
            tooltip="Median time from when a PR is opened to when it is merged."
            anomaly={anomalies.data?.PR_LEAD_TIME_HOURS_MEDIAN}
            onSetGoal={() => openGoalModal('PR_LEAD_TIME_HOURS_MEDIAN', 'pr lead time')}
          />
          <KpiTile
            label="focus ratio"
            value={pctSuffix(focusRatio.data?.value)}
            sub="coding days / working"
            accent="emerald"
            icon={<Focus />}
            tooltip="Coding days ÷ working days (Mon–Fri)."
            anomaly={anomalies.data?.FOCUS_RATIO_DAYS_TASKS}
            onSetGoal={() => openGoalModal('FOCUS_RATIO_DAYS_TASKS', 'focus ratio')}
          />
        </div>
        <div className="divider" />
        <div className="grid-kpi">
          <KpiTile
            label="prs created"
            value={sum(prCreated.data ?? [])}
            sub=""
            accent="accent"
            icon={<PRCreated />}
            tooltip="Pull requests opened by you in the selected period."
            anomaly={anomalies.data?.DAILY_PR_CREATED}
            onSetGoal={() => openGoalModal('DAILY_PR_CREATED', 'prs created')}
          />
          <KpiTile
            label="issues closed"
            value={sum(issuesClosed.data ?? [])}
            sub=""
            accent="emerald"
            icon={<IssuesClosed />}
            tooltip="Issues resolved or closed by you in the selected period."
            anomaly={anomalies.data?.DAILY_ISSUES_CLOSED}
            onSetGoal={() => openGoalModal('DAILY_ISSUES_CLOSED', 'issues closed')}
          />
          <KpiTile
            label="review response"
            value={numSuffix(reviewTime.data?.value, 'h')}
            sub="first review · median"
            accent="cyan"
            icon={<Review />}
            tooltip="Median time from PR open to receiving the first review comment or approval."
            anomaly={anomalies.data?.REVIEW_RESPONSE_TIME_HOURS_MEDIAN}
            onSetGoal={() => openGoalModal('REVIEW_RESPONSE_TIME_HOURS_MEDIAN', 'review response')}
          />
          <KpiTile
            label="1st commit → merge"
            value={numSuffix(firstCommitLeadTimeHrs, 'd', (v) => (v / 24).toFixed(1))}
            sub="lead time · median"
            accent="cyan"
            icon={<FirstCommit />}
            tooltip="Median hours from first commit on a PR branch to its merge."
            anomaly={anomalies.data?.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN}
            onSetGoal={() => openGoalModal('PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN', '1st commit → merge')}
          />
        </div>
      </div>

      {/* ── Wellness & Quality ───────────────────────────── */}
      <div className="hr-label"><span>wellness · quality</span></div>
      <div className="card">
        <div className="grid-kpi">
          <KpiTile
            label="after-hours"
            value={pctSuffix(afterHours.data?.value)}
            sub="commits outside 9–6"
            accent="amber"
            icon={<AfterHours />}
            tooltip="Share of commits made outside 09:00–18:00 Mon–Fri in your local timezone."
            onSetGoal={() => openGoalModal('AFTER_HOURS_COMMIT_RATIO', 'after-hours')}
          />
          <KpiTile
            label="refactor ratio"
            value={pctSuffix(refactorRatio.data?.value)}
            sub="cleanup commits"
            accent="emerald"
            icon={<Refactor />}
            tooltip="Share of commits where deleted lines outnumber added lines — a proxy for cleanup and refactoring activity."
            onSetGoal={() => openGoalModal('REFACTOR_RATIO', 'refactor ratio')}
          />
          <KpiTile
            label="merge w/o review"
            value={pctSuffix(mergeWithoutReview.data?.value)}
            sub="zero approvals"
            accent="coral"
            icon={<NoReview />}
            tooltip="Share of merged PRs that had zero reviewer approvals or comments before merge."
            onSetGoal={() => openGoalModal('MERGE_WITHOUT_REVIEW_RATIO', 'merge w/o review')}
          />
          <KpiTile
            label="commits per week"
            value={numSuffix(commitsPerWeekAvg.data?.value, '/wk')}
            sub="avg per iso week"
            accent="accent"
            icon={<MergeFreq />}
            tooltip="Average number of commits you authored per ISO calendar week in the selected window."
            onSetGoal={() => openGoalModal('COMMITS_PER_WEEK_AVG', 'commits per week')}
          />
        </div>
        <div className="divider" />
        <div className="grid-kpi">
          <KpiTile
            label="deep work streak"
            value={deepWorkDays > 0
              ? <span>{deepWorkDays}<em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 4, fontStyle: 'normal' }}>d</em></span>
              : '—'}
            sub="longest run"
            accent="emerald"
            icon={<DeepWork />}
            tooltip="Longest consecutive run of days on which you authored at least one commit."
            onSetGoal={() => openGoalModal('DEEP_WORK_STREAK_DAYS', 'deep work streak')}
          />
          <KpiTile
            label="avg churn"
            value={avgChurnVal > 0
              ? <span>{(avgChurnVal * 100).toFixed(1)}<em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 2, fontStyle: 'normal' }}>%</em></span>
              : '—'}
            sub="rewrites / changes"
            accent="amber"
            icon={<Churn />}
            tooltip="Average daily ratio of deleted lines to total changed lines. High churn may indicate rework or rewrites."
            anomaly={anomalies.data?.DAILY_CHURN_RATIO}
            onSetGoal={() => openGoalModal('DAILY_CHURN_RATIO', 'avg churn')}
          />
          <KpiTile
            label="knowledge silo"
            value={pctSuffix(knowledgeSilo.data?.value)}
            sub="max repo ownership"
            accent="coral"
            icon={<Silo />}
            tooltip="Your highest commit share across all repos. A high value means you are the sole owner of that repo's knowledge — a bus-factor risk."
            onSetGoal={() => openGoalModal('KNOWLEDGE_SILO_SCORE', 'knowledge silo')}
          />
          <KpiTile
            label="pr size · median"
            value={prSizeComplexity.data?.value != null && prSizeComplexity.data.value > 0
              ? <span>{prSizeComplexity.data.value.toFixed(0)}<em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 4, fontStyle: 'normal' }}>ln/c</em></span>
              : '—'}
            sub="lines per commit"
            accent="amber"
            icon={<PRSize />}
            tooltip="Median lines changed per PR (additions + deletions)."
            onSetGoal={() => openGoalModal('PR_SIZE_COMPLEXITY_SCORE', 'pr size')}
          />
        </div>
        <div className="divider" />
        <div className="grid-kpi">
          <KpiTile
            label="open pr age"
            value={numSuffix(wipOpenPrAge.data?.value, 'h')}
            sub="median · open prs"
            accent="amber"
            icon={<LeadTime />}
            tooltip="Median age of your currently open PRs. High values indicate a WIP queue building up."
            onSetGoal={() => openGoalModal('WIP_OPEN_PR_AGE_HOURS_MEDIAN', 'open pr age')}
          />
          <KpiTile
            label="code review participation"
            value={reviewParticipationCount > 0 ? reviewParticipationCount : '—'}
            sub="prs reviewed"
            accent="cyan"
            icon={<Review />}
            tooltip="Number of distinct pull requests in which you participated as a reviewer (approved, requested changes, or commented) in the selected period. Self-reviews excluded."
            onSetGoal={() => openGoalModal('REVIEW_PARTICIPATION_COUNT', 'code review participation')}
          />
        </div>
      </div>

      {/* ── Anomaly strip ────────────────────────────────── */}
      {anomalousMetricNames.length > 0 && (
        <>
          <div className="hr-label"><span>what changed</span></div>
          <div className="card" style={{ padding: '14px 20px', marginBottom: 0 }}>
            <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              <span className="t-label" style={{ color: 'var(--fg-2)' }}>unusual this period:</span>
              {anomalousMetricNames.map((name) => (
                <Chip key={name} color="amber">{name}</Chip>
              ))}
            </div>
          </div>
        </>
      )}

      {/* ── AI Summary ───────────────────────────────────── */}
      <div className="hr-label"><span>ai summary</span></div>
      <AiSummaryCard range={range} onSummaryGenerated={setAiSummary} />

      {/* ── Activity over time ───────────────────────────── */}
      <div className="hr-label"><span>activity · over time</span></div>

      <div className="card" style={{ padding: 22, marginBottom: 14 }}>
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14, flexWrap: 'wrap', gap: 8 }}>
          <div>
            <div className="t-eyebrow">commits — daily</div>
            <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Commit cadence</div>
          </div>
          <div className="row gap-2" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip accent dot>{commits.data?.length ?? 0} days</Chip>
            {peakCommits > 0 && <Chip>peak: {peakCommits}</Chip>}
            <CompareToggle
              enabled={commitsCompareRange.enabled}
              onToggle={commitsCompareRange.toggle}
              compareRange={commitsCompareRange.compareRange}
              onCompareRangeChange={commitsCompareRange.setCompareRange}
              windowDays={commitsCompareRange.windowDays}
            />
            {commitsCompareRange.enabled && commitsCompareQuery.isError && (
              <span className="t-label" style={{ fontSize: 10, color: 'var(--coral)' }}>comparison failed to load</span>
            )}
          </div>
        </div>
        {commitsCompareRange.enabled && commitsCompareQuery.data && (
          <div style={{ marginBottom: 12 }}>
            <CompareDelta
              label="commits"
              primaryTotal={totalCommits}
              comparisonTotal={sum(commitsCompareQuery.data)}
              primaryRange={range}
              comparisonRange={commitsCompareRange.compareRange}
            />
          </div>
        )}
        <MetricBarChart
          data={commits.data ?? []}
          label="commits/day"
          color="var(--accent)"
          height={160}
          compareData={commitsCompareRange.enabled ? commitsCompareQuery.data : undefined}
          primaryRangeLabel={`${formatDate(from)} – ${formatDate(to)}`}
          compareRangeLabel={`${formatDate(commitsCompareRange.compareRange.from)} – ${formatDate(commitsCompareRange.compareRange.to)}`}
        />
      </div>

      <div className="charts-grid">
        <div className="card" style={{ padding: 22 }}>
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
            <div>
              <div className="t-eyebrow">pull requests</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>PR flow</div>
            </div>
            <div className="row gap-2" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Chip color="cyan" dot>open → merge</Chip>
              <CompareToggle
                enabled={prFlowCompareRange.enabled}
                onToggle={prFlowCompareRange.toggle}
                compareRange={prFlowCompareRange.compareRange}
                onCompareRangeChange={prFlowCompareRange.setCompareRange}
                windowDays={prFlowCompareRange.windowDays}
              />
            </div>
          </div>
          {prFlowCompareRange.enabled && (prCreatedCompareQuery.data || prMergedCompareQuery.data) && (
            <div className="col gap-1" style={{ marginBottom: 12 }}>
              {prCreatedCompareQuery.data && (
                <CompareDelta
                  label="created"
                  primaryTotal={sum(prCreated.data ?? [])}
                  comparisonTotal={sum(prCreatedCompareQuery.data)}
                  primaryRange={range}
                  comparisonRange={prFlowCompareRange.compareRange}
                />
              )}
              {prMergedCompareQuery.data && (
                <CompareDelta
                  label="merged"
                  primaryTotal={totalPrsMerged}
                  comparisonTotal={sum(prMergedCompareQuery.data)}
                  primaryRange={range}
                  comparisonRange={prFlowCompareRange.compareRange}
                />
              )}
            </div>
          )}
          <div className="col gap-3">
            <div>
              <div className="row" style={{ justifyContent: 'space-between', marginBottom: 4 }}>
                {/* Semantic: violet = PR created (open state). Do not replace with var(--accent). */}
                <span className="t-label" style={{ color: 'var(--violet)' }}>created</span>
                <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{sum(prCreated.data ?? [])}</span>
              </div>
              {/* Semantic: violet = created/open, emerald = merged/done — do not replace with var(--accent) */}
              <Sparkline
                data={prCreated.data ?? []}
                color="var(--violet)"
                height={36}
                width={500}
                responsive
                compareData={prFlowCompareRange.enabled ? prCreatedCompareQuery.data : undefined}
              />
            </div>
            <div>
              <div className="row" style={{ justifyContent: 'space-between', marginBottom: 4 }}>
                <span className="t-label" style={{ color: 'var(--emerald)' }}>merged</span>
                <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{totalPrsMerged}</span>
              </div>
              <Sparkline
                data={prMerged.data ?? []}
                color="var(--emerald)"
                height={36}
                width={500}
                responsive
                compareData={prFlowCompareRange.enabled ? prMergedCompareQuery.data : undefined}
              />
            </div>
            {prFlowCompareRange.enabled && (
              <div className="row gap-3" style={{ marginTop: 2, flexWrap: 'wrap' }}>
                <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-2)' }}>
                  <span style={{ display: 'inline-block', width: 8, height: 2, background: 'var(--fg-2)', marginRight: 4, verticalAlign: 'middle' }} />
                  This period — {formatDate(from)} – {formatDate(to)}
                </span>
                <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>
                  <span style={{ display: 'inline-block', width: 8, height: 0, borderTop: '1.5px dashed var(--fg-3)', marginRight: 4, verticalAlign: 'middle' }} />
                  Previous period — {formatDate(prFlowCompareRange.compareRange.from)} – {formatDate(prFlowCompareRange.compareRange.to)}
                </span>
                <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>x-axis: day of period</span>
                {(prCreatedCompareQuery.isError || prMergedCompareQuery.isError) && (
                  <span className="t-label" style={{ fontSize: 10, color: 'var(--coral)' }}>comparison failed to load</span>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="card" style={{ padding: 22 }}>
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
            <div>
              <div className="t-eyebrow">code churn</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Rewrite ratio</div>
            </div>
            <Chip color="amber" dot>watch</Chip>
          </div>
          <Sparkline
            data={(churn.data ?? []).map((d) => ({ ...d, value: d.value * 100 }))}
            color="var(--amber)"
            height={110}
            width={500}
            responsive
          />
          <p className="t-muted" style={{ marginTop: 6, fontFamily: 'var(--font-mono)', fontSize: 11 }}>
            churn = lines_deleted / (lines_added + lines_deleted) · daily
          </p>
        </div>
      </div>

      {/* ── Issues (additional chart) ─────────────────────── */}
      {((issuesClosed.data?.length ?? 0) > 0 || (issuesCreated.data?.length ?? 0) > 0) && (
        <div className="card" style={{ padding: 22, marginTop: 14 }}>
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14, flexWrap: 'wrap', gap: 8 }}>
            <div>
              <div className="t-eyebrow">issues</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Created vs closed</div>
            </div>
            <div className="row gap-2" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Chip color="emerald" dot>tracker</Chip>
              <CompareToggle
                enabled={issuesCompareRange.enabled}
                onToggle={issuesCompareRange.toggle}
                compareRange={issuesCompareRange.compareRange}
                onCompareRangeChange={issuesCompareRange.setCompareRange}
                windowDays={issuesCompareRange.windowDays}
              />
              {issuesCompareRange.enabled && (issuesClosedCompareQuery.isError || issuesCreatedCompareQuery.isError) && (
                <span className="t-label" style={{ fontSize: 10, color: 'var(--coral)' }}>comparison failed to load</span>
              )}
            </div>
          </div>
          {issuesCompareRange.enabled && (issuesClosedCompareQuery.data || issuesCreatedCompareQuery.data) && (
            <div className="col gap-1" style={{ marginBottom: 12 }}>
              {(issuesClosed.data?.length ?? 0) > 0 && issuesClosedCompareQuery.data && (
                <CompareDelta
                  label="closed"
                  primaryTotal={sum(issuesClosed.data ?? [])}
                  comparisonTotal={sum(issuesClosedCompareQuery.data)}
                  primaryRange={range}
                  comparisonRange={issuesCompareRange.compareRange}
                />
              )}
              {(issuesCreated.data?.length ?? 0) > 0 && issuesCreatedCompareQuery.data && (
                <CompareDelta
                  label="created"
                  primaryTotal={sum(issuesCreated.data ?? [])}
                  comparisonTotal={sum(issuesCreatedCompareQuery.data)}
                  primaryRange={range}
                  comparisonRange={issuesCompareRange.compareRange}
                />
              )}
            </div>
          )}
          <div className="col gap-4">
            {(issuesClosed.data?.length ?? 0) > 0 && (
              <div>
                <span className="t-label" style={{ display: 'block', marginBottom: 6, color: 'var(--emerald)' }}>closed</span>
                <MetricBarChart
                  data={issuesClosed.data ?? []}
                  label="Closed"
                  color="var(--emerald)"
                  compareData={issuesCompareRange.enabled ? issuesClosedCompareQuery.data : undefined}
                  primaryRangeLabel={`${formatDate(from)} – ${formatDate(to)}`}
                  compareRangeLabel={`${formatDate(issuesCompareRange.compareRange.from)} – ${formatDate(issuesCompareRange.compareRange.to)}`}
                />
              </div>
            )}
            {(issuesCreated.data?.length ?? 0) > 0 && (
              <div>
                <span className="t-label" style={{ display: 'block', marginBottom: 6, color: 'var(--cyan)' }}>created</span>
                <MetricBarChart
                  data={issuesCreated.data ?? []}
                  label="Created"
                  color="var(--cyan)"
                  compareData={issuesCompareRange.enabled ? issuesCreatedCompareQuery.data : undefined}
                  primaryRangeLabel={`${formatDate(from)} – ${formatDate(to)}`}
                  compareRangeLabel={`${formatDate(issuesCompareRange.compareRange.from)} – ${formatDate(issuesCompareRange.compareRange.to)}`}
                />
              </div>
            )}
          </div>
        </div>
      )}

      <div style={{ height: 32 }} />

      {/* ── Set Goal Modal ───────────────────────────────── */}
      {goalModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 50,
          }}
          onClick={(e) => { if (e.target === e.currentTarget) setGoalModal(null); }}
        >
          <div
            style={{
              background: 'var(--bg)',
              border: '1px solid var(--line)',
              borderRadius: 12,
              padding: '28px 32px',
              width: '100%',
              maxWidth: 400,
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <div className="t-eyebrow" style={{ marginBottom: 4 }}>set goal</div>
                <h2 style={{ margin: 0, fontSize: 17, fontWeight: 600, color: 'var(--fg)' }}>
                  {goalModal.label}
                </h2>
              </div>
              <button
                className="btn btn-sm"
                aria-label="Close"
                onClick={() => setGoalModal(null)}
                style={{ padding: '4px 8px' }}
              >
                <X width={16} height={16} />
              </button>
            </div>

            {(() => {
              const existing = goals?.filter((g) => g.metricType === goalModal.metricType) ?? [];
              return existing.length > 0 ? (
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                  padding: '10px 12px',
                  background: 'var(--bg-2)',
                  borderRadius: 6,
                  border: '1px solid var(--line)',
                }}>
                  <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)', marginBottom: 2 }}>
                    active goal{existing.length > 1 ? 's' : ''} for this metric
                  </span>
                  {existing.map((g) => (
                    <span key={g.id} style={{ fontSize: 12, color: 'var(--fg-2)' }}>
                      target <strong style={{ color: 'var(--fg)' }}>{g.targetValue}</strong>
                      {' · '}by <strong style={{ color: 'var(--fg)' }}>{g.targetDate}</strong>
                    </span>
                  ))}
                </div>
              ) : null;
            })()}

            <form onSubmit={handleGoalSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg-muted)' }}>
                  Target Value
                </label>
                <input
                  className="input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={goalForm.targetValue}
                  onChange={(e) => setGoalForm((f) => ({ ...f, targetValue: Number(e.target.value) }))}
                  required
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg-muted)' }}>
                  Target Date
                </label>
                <input
                  className="input"
                  type="date"
                  value={goalForm.targetDate}
                  onChange={(e) => setGoalForm((f) => ({ ...f, targetDate: e.target.value }))}
                  required
                />
              </div>

              {createGoalMutation.isError && (
                <p style={{ fontSize: 12, color: 'var(--coral-strong)', margin: 0 }}>
                  Failed to save goal. Please try again.
                </p>
              )}

              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={() => setGoalModal(null)}
                  disabled={createGoalMutation.isPending}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-sm btn-accent"
                  disabled={createGoalMutation.isPending}
                >
                  {createGoalMutation.isPending ? 'Saving…' : 'Save Goal'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
