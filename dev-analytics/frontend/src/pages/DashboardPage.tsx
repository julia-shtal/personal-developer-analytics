import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metricsApi } from '@/api/metrics';
import { MetricBarChart } from '@/components/charts/MetricBarChart';
import { Sparkline } from '@/components/charts/Sparkline';
import { KpiTile } from '@/components/ui/KpiTile';
import { Chip } from '@/components/ui/Chip';
import { PageSpinner } from '@/components/ui/Spinner';
import { useDateRange } from '@/context/DateRangeContext';
import { AiSummaryCard } from '@/components/ai/AiSummaryCard';
import { formatDate } from '@/lib/dates';
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
  const qc = useQueryClient();
  const { from, to } = range;

  const [aiSummary, setAiSummary] = useState<MetricsSummaryDto | null>(null);

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

  const recalculateMutation = useMutation({
    mutationFn: () => metricsApi.calculate(from, to),
    onSuccess: () => qc.invalidateQueries({ queryKey: [] }),
  });

  useEffect(() => {
    const h = () => recalculateMutation.mutate();
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

  // issueLeadTime kept for future use — data is fetched but not yet shown in KPI tiles
  void issueLeadTime;

  return (
    <div className="page fade-in">

      {/* ── Hero ──────────────────────────────────────────── */}
      <div style={{ marginBottom: 36 }}>
        <div className="t-eyebrow" style={{ marginBottom: 14 }}>
          ── Personal · {formatDate(from)} → {formatDate(to)}
        </div>
        <h1 className="t-h1" style={{ textAlign: 'justify' }}>
          <em>{totalCommits} commits</em>, <em>{totalPrsMerged} PRs merged</em>,
          {' '}a <em>{deepWorkDays}-day</em> deep-work streak
          {aiSummary?.headline ? ` — ${aiSummary.headline}` : '.'}
        </h1>
        <p className="t-body" style={{ marginTop: 18, textAlign: 'justify' }}>
          {aiSummary?.overview ?? 'Generate an AI summary to see your narrative overview.'}
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
          <Chip color="violet" dot>{commits.data?.length ?? 0} days</Chip>
        </div>
        <div style={{ overflow: 'hidden' }}>
          <Sparkline data={commits.data ?? []} color="var(--violet)" height={70} width={800} area responsive />
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
            accent="violet"
            icon={<Commits />}
            tooltip="Number of Git commits authored in the selected period."
          />
          <KpiTile
            label="prs merged"
            value={totalPrsMerged}
            sub="to default branch"
            accent="violet"
            icon={<PRMerged />}
            tooltip="Pull requests merged to a target branch in the selected period."
          />
          <KpiTile
            label="pr lead time"
            value={numSuffix(leadTimeHrs, 'h')}
            sub="open → merge · median"
            accent="cyan"
            icon={<LeadTime />}
            tooltip="Median time from when a PR is opened to when it is merged."
          />
          <KpiTile
            label="focus ratio"
            value={pctSuffix(focusRatio.data?.value)}
            sub="coding days / working"
            accent="emerald"
            icon={<Focus />}
            tooltip="Fraction of working days (Mon–Fri) on which you made at least one commit."
          />
        </div>
        <div className="divider" />
        <div className="grid-kpi">
          <KpiTile
            label="prs created"
            value={sum(prCreated.data ?? [])}
            sub=""
            accent="violet"
            icon={<PRCreated />}
            tooltip="Pull requests opened by you in the selected period."
          />
          <KpiTile
            label="issues closed"
            value={sum(issuesClosed.data ?? [])}
            sub=""
            accent="emerald"
            icon={<IssuesClosed />}
            tooltip="Issues resolved or closed by you in the selected period."
          />
          <KpiTile
            label="review response"
            value={numSuffix(reviewTime.data?.value, 'h')}
            sub="first review · median"
            accent="cyan"
            icon={<Review />}
            tooltip="Median time from PR open to receiving the first review comment or approval."
          />
          <KpiTile
            label="1st commit → merge"
            value={numSuffix(firstCommitLeadTimeHrs, 'd', (v) => (v / 24).toFixed(1))}
            sub="lead time · median"
            accent="cyan"
            icon={<FirstCommit />}
            tooltip="Median time from the first commit on a PR branch to the PR being merged."
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
          />
          <KpiTile
            label="refactor ratio"
            value={pctSuffix(refactorRatio.data?.value)}
            sub="cleanup commits"
            accent="emerald"
            icon={<Refactor />}
            tooltip="Share of commits where deleted lines outnumber added lines — a proxy for cleanup and refactoring activity."
          />
          <KpiTile
            label="merge w/o review"
            value={pctSuffix(mergeWithoutReview.data?.value)}
            sub="zero approvals"
            accent="coral"
            icon={<NoReview />}
            tooltip="Share of merged PRs that had zero reviewer approvals or comments before merge."
          />
          <KpiTile
            label="merge frequency"
            value={numSuffix(mergeToMain.data?.value, '/wk')}
            sub="dora proxy"
            accent="violet"
            icon={<MergeFreq />}
            tooltip="Average number of merges to the main branch per week. Used as a proxy for DORA deployment frequency."
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
          />
          <KpiTile
            label="knowledge silo"
            value={pctSuffix(knowledgeSilo.data?.value)}
            sub="max repo ownership"
            accent="coral"
            icon={<Silo />}
            tooltip="Your highest commit share across all repos. A high value means you are the sole owner of that repo's knowledge — a bus-factor risk."
          />
          <KpiTile
            label="pr size · median"
            value={prSizeComplexity.data?.value != null && prSizeComplexity.data.value > 0
              ? <span>{prSizeComplexity.data.value.toFixed(0)}<em style={{ fontSize: '0.5em', color: 'var(--fg-3)', marginLeft: 4, fontStyle: 'normal' }}>ln/c</em></span>
              : '—'}
            sub="lines per commit"
            accent="amber"
            icon={<PRSize />}
            tooltip="Median (additions + deletions) per commit across your PRs. Lower values mean smaller, more focused changes that are easier to review."
          />
        </div>
      </div>

      {/* ── AI Summary ───────────────────────────────────── */}
      <div className="hr-label"><span>ai summary</span></div>
      <AiSummaryCard range={range} onSummaryGenerated={setAiSummary} />

      {/* ── Activity over time ───────────────────────────── */}
      <div className="hr-label"><span>activity · over time</span></div>

      <div className="card" style={{ padding: 22, marginBottom: 14 }}>
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
          <div>
            <div className="t-eyebrow">commits — daily</div>
            <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Commit cadence</div>
          </div>
          <div className="row gap-2">
            <Chip color="violet" dot>{commits.data?.length ?? 0} days</Chip>
            {peakCommits > 0 && <Chip>peak: {peakCommits}</Chip>}
          </div>
        </div>
        <MetricBarChart
          data={commits.data ?? []}
          label="commits/day"
          color="var(--violet)"
          height={160}
        />
      </div>

      <div className="charts-grid">
        <div className="card" style={{ padding: 22 }}>
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
            <div>
              <div className="t-eyebrow">pull requests</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>PR flow</div>
            </div>
            <Chip color="cyan" dot>open → merge</Chip>
          </div>
          <div className="col gap-3">
            <div>
              <div className="row" style={{ justifyContent: 'space-between', marginBottom: 4 }}>
                <span className="t-label" style={{ color: 'var(--violet)' }}>created</span>
                <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{sum(prCreated.data ?? [])}</span>
              </div>
              <Sparkline data={prCreated.data ?? []} color="var(--violet)" height={36} width={500} responsive />
            </div>
            <div>
              <div className="row" style={{ justifyContent: 'space-between', marginBottom: 4 }}>
                <span className="t-label" style={{ color: 'var(--emerald)' }}>merged</span>
                <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{totalPrsMerged}</span>
              </div>
              <Sparkline data={prMerged.data ?? []} color="var(--emerald)" height={36} width={500} responsive />
            </div>
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
          <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
            <div>
              <div className="t-eyebrow">issues</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Created vs closed</div>
            </div>
            <Chip color="emerald" dot>tracker</Chip>
          </div>
          <div className="col gap-4">
            {(issuesClosed.data?.length ?? 0) > 0 && (
              <div>
                <span className="t-label" style={{ display: 'block', marginBottom: 6, color: 'var(--emerald)' }}>closed</span>
                <MetricBarChart data={issuesClosed.data ?? []} label="Closed" color="var(--emerald)" />
              </div>
            )}
            {(issuesCreated.data?.length ?? 0) > 0 && (
              <div>
                <span className="t-label" style={{ display: 'block', marginBottom: 6, color: 'var(--cyan)' }}>created</span>
                <MetricBarChart data={issuesCreated.data ?? []} label="Created" color="var(--cyan)" />
              </div>
            )}
          </div>
        </div>
      )}

      <div style={{ height: 32 }} />
    </div>
  );
}
