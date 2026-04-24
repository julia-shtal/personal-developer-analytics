import api from '@/lib/api';
import type { MetricPointDto, MetricAggregateDto, TeamMetricPointDto, MemberSummaryDto } from '@/types';

const fmt = (d: string) => d; // already ISO

// ─── Personal ─────────────────────────────────────────────────────────────────

export const metricsApi = {
  calculate: (from: string, to: string) =>
    api.post('/metrics/calculate', null, { params: { from: fmt(from), to: fmt(to) } }),

  dailyCommits: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-commits', { params: { from, to, repoId } }),

  dailyPrCreated: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-pr-created', { params: { from, to, repoId } }),

  dailyPrMerged: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-pr-merged', { params: { from, to, repoId } }),

  dailyChurn: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-churn', { params: { from, to, repoId } }),

  dailyIssuesClosed: (from: string, to: string, repoId?: number) =>
    api.get<MetricPointDto[]>('/metrics/daily-issues-closed', { params: { from, to, repoId } }),

  prLeadTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/pr-lead-time', { params: { from, to, repoId } }),

  reviewResponseTime: (from: string, to: string, repoId?: number) =>
    api.get<MetricAggregateDto>('/metrics/review-response-time', { params: { from, to, repoId } }),

  focusRatio: (from: string, to: string) =>
    api.get<MetricAggregateDto>('/metrics/focus-ratio', { params: { from, to } }),

  focusRatioSeries: (from: string, to: string) =>
    api.get<MetricPointDto[]>('/metrics/focus-ratio/series', { params: { from, to } }),
};

// ─── Team ─────────────────────────────────────────────────────────────────────

export const teamMetricsApi = {
  calculate: (teamId: number, from: string, to: string) =>
    api.post(`/metrics/teams/${teamId}/calculate`, null, { params: { from, to } }),

  dailyCommits: (teamId: number, from: string, to: string) =>
    api.get<TeamMetricPointDto[]>(`/metrics/teams/${teamId}/daily-commits`, { params: { from, to } }),

  summary: (teamId: number, from: string, to: string) =>
    api.get<MemberSummaryDto[]>(`/metrics/teams/${teamId}/summary`, { params: { from, to } }),

  memberSummary: (teamId: number, memberId: number, from: string, to: string) =>
    api.get<MemberSummaryDto>(`/metrics/teams/${teamId}/members/${memberId}/summary`, { params: { from, to } }),
};
