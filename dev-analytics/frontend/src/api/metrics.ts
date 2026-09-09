import api from '@/lib/api';
import type { MetricPointDto, MetricAggregateDto, TeamMetricPointDto, MemberSummaryDto, MetricAnomalyResponse } from '@/types';

// ─── Personal ─────────────────────────────────────────────────────────────────

export const metricsApi = {
  calculate: (from: string, to: string) =>
    api.post('/metrics/calculate', null, { params: { from, to } }),

  // ── Core activity ──────────────────────────────────────────────────────────
  dailyCommits: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-commits-count', { params: { from, to, repoId } }),

  dailyPrCreated: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-pr-created', { params: { from, to, repoId } }),

  dailyPrMerged: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-pr-merged', { params: { from, to, repoId } }),

  dailyChurn: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-churn-ratio', { params: { from, to, repoId } }),

  dailyIssuesClosed: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-issues-closed', { params: { from, to, repoId } }),

  dailyIssuesCreated: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-issues-created', { params: { from, to, repoId } }),

  // ── Lead times ─────────────────────────────────────────────────────────────
  prLeadTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/pr-lead-time', { params: { from, to, repoId } }),

  prFirstCommitLeadTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/pr-first-commit-to-merge-lead-time', { params: { from, to, repoId } }),

  reviewResponseTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/review-response-time', { params: { from, to, repoId } }),

  issueLeadTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/issue-lead-time', { params: { from, to, repoId } }),

  // ── Focus ──────────────────────────────────────────────────────────────────
  focusRatio: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/focus-ratio', { params: { from, to } }),

  focusRatioSeries: (from: string, to: string) =>
    api.get<MetricPointDto[]>('/metrics/focus-ratio/series', { params: { from, to } }),

  // ── Wellness & quality (Ticket 5 metrics) ─────────────────────────────────
  dailyAfterHours: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/after-hours-commit-ratio', { params: { from, to } }),

  dailyRefactorRatio: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/refactor-ratio', { params: { from, to } }),

  commitsPerWeekAvg: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/commits-per-week-avg', { params: { from, to } }),

  deepWorkStreak: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/deep-work-streak', { params: { from, to } }),

  mergeWithoutReview: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/merge-without-review-ratio', { params: { from, to, repoId } }),

  prSizeComplexity: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/pr-size-complexity', { params: { from, to, repoId } }),

  wipOpenPrAge: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/wip-open-pr-age', { params: { from, to, repoId } }),

  knowledgeSilo: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/knowledge-silo-score', { params: { from, to, repoId } }),

  reviewParticipation: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/review-participation', { params: { from, to } }),

  freshness: () =>
    api.get<{ metricsComputedThrough?: string }>('/metrics/freshness'),

  anomalies: (from: string, to: string) =>
    api.get<MetricAnomalyResponse>('/metrics/anomalies', { params: { from, to } }),

  backfill: (from: string, to: string) =>
    api.post('/metrics/backfill', null, { params: { from, to } }),
};

// ─── Team ─────────────────────────────────────────────────────────────────────

export const teamMetricsApi = {
  calculate: (teamId: number, from: string, to: string) =>
    api.post(`/metrics/teams/${teamId}/calculate`, null, { params: { from, to } }),

  dailyCommits: (teamId: number, from: string, to: string, repoId?: number | null) =>
    api.get<TeamMetricPointDto[]>(`/metrics/teams/${teamId}/daily-commits-count`, {
      params: { from, to, ...(repoId != null && { repoId }) },
    }),

  dailyPrMerged: (teamId: number, from: string, to: string, repoId?: number | null) =>
    api.get<TeamMetricPointDto[]>(`/metrics/teams/${teamId}/daily-pr-merged`, {
      params: { from, to, ...(repoId != null && { repoId }) },
    }),

  dailyIssuesClosed: (teamId: number, from: string, to: string, repoId?: number | null) =>
    api.get<TeamMetricPointDto[]>(`/metrics/teams/${teamId}/daily-issues-closed`, {
      params: { from, to, ...(repoId != null && { repoId }) },
    }),

  summary: (teamId: number, from: string, to: string) =>
    api.get<MemberSummaryDto[]>(`/metrics/teams/${teamId}/summary`, { params: { from, to } }),

  memberSummary: (teamId: number, memberId: number, from: string, to: string) =>
    api.get<MemberSummaryDto>(`/metrics/teams/${teamId}/members/${memberId}/summary`, { params: { from, to } }),

  memberDailyCommits: (teamId: number, memberId: number, from: string, to: string) =>
    api.get<MetricPointDto[]>(`/metrics/teams/${teamId}/members/${memberId}/daily-commits-count`, { params: { from, to } }),

  memberDailyPrCreated: (teamId: number, memberId: number, from: string, to: string) =>
    api.get<MetricPointDto[]>(`/metrics/teams/${teamId}/members/${memberId}/daily-pr-created`, { params: { from, to } }),

  memberDailyPrMerged: (teamId: number, memberId: number, from: string, to: string) =>
    api.get<MetricPointDto[]>(`/metrics/teams/${teamId}/members/${memberId}/daily-pr-merged`, { params: { from, to } }),
};
