import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Sparkles, RefreshCw, Shield, AlertCircle, Zap, Eye, Target, GitMerge, Copy, Check } from 'lucide-react';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { aiApi } from '@/api/ai';
import type { MetricsSummaryDto } from '@/types/ai';
import type { DateRange, MemberSummaryDto } from '@/types';

interface Props {
  range: DateRange;
  teamId: number;
  teamName: string;
  memberSummary: MemberSummaryDto[];
  onSummaryGenerated?: (summary: MetricsSummaryDto) => void;
}

type SignalStatus = 'stable' | 'watch' | 'attention' | 'neutral';

interface Signal {
  label: string;
  interpretation: string;
  status: SignalStatus;
}

function fmtHours(h: number): string {
  if (h < 24) return `${h.toFixed(1)}h`;
  return `${(h / 24).toFixed(1)}d`;
}

function statusChip(status: SignalStatus): { text: string; className: string } {
  switch (status) {
    case 'stable':     return { text: 'Stable',          className: 'bg-emerald-50 text-emerald-700' };
    case 'watch':      return { text: 'Watch',           className: 'bg-amber-50 text-amber-700' };
    case 'attention':  return { text: 'Needs attention', className: 'bg-red-50 text-red-600' };
    default:           return { text: 'No data',         className: 'bg-gray-100 text-gray-500' };
  }
}

function timeAgo(date: Date): string {
  const diffMin = Math.floor((Date.now() - date.getTime()) / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin} min ago`;
  return `${Math.floor(diffMin / 60)}h ago`;
}

function avg(values: number[]): number | null {
  if (!values.length) return null;
  return values.reduce((a, b) => a + b, 0) / values.length;
}

function computeSignals(members: MemberSummaryDto[], range: DateRange): Signal[] {
  const workingDays = countWorkingDays(range.from, range.to);

  // Delivery flow — avg PR lead time
  const prLeadTimes = members
    .map((m) => m.metrics.PR_LEAD_TIME_HOURS_MEDIAN)
    .filter((v): v is number => v != null && v > 0);
  const avgLead = avg(prLeadTimes);
  const deliveryStatus: SignalStatus =
    avgLead == null ? 'neutral' : avgLead < 24 ? 'stable' : avgLead < 72 ? 'watch' : 'attention';

  // Review responsiveness — avg review response time
  const reviewTimes = members
    .map((m) => m.metrics.REVIEW_RESPONSE_TIME_HOURS_MEDIAN)
    .filter((v): v is number => v != null && v > 0);
  const avgReview = avg(reviewTimes);
  const reviewStatus: SignalStatus =
    avgReview == null ? 'neutral' : avgReview < 8 ? 'stable' : avgReview < 24 ? 'watch' : 'attention';

  // Focus — avg focus ratio (active days / working days)
  const focusDays = members
    .map((m) => m.metrics.FOCUS_RATIO_DAYS_TASKS)
    .filter((v): v is number => v != null && v > 0);
  const avgFocusDays = avg(focusDays);
  const focusRatio = avgFocusDays != null && workingDays > 0 ? avgFocusDays / workingDays : null;
  const focusStatus: SignalStatus =
    focusRatio == null ? 'neutral' : focusRatio > 0.7 ? 'stable' : focusRatio > 0.4 ? 'watch' : 'attention';

  // Risk — avg merge without review ratio
  const mergeNoReview = members
    .map((m) => m.metrics.MERGE_WITHOUT_REVIEW_RATIO)
    .filter((v): v is number => v != null);
  const avgMergeNoReview = avg(mergeNoReview);
  const riskStatus: SignalStatus =
    avgMergeNoReview == null ? 'neutral'
    : avgMergeNoReview < 0.1 ? 'stable'
    : avgMergeNoReview < 0.3 ? 'watch'
    : 'attention';

  return [
    {
      label: 'Delivery Flow',
      interpretation: avgLead != null
        ? `Avg PR lead time ${fmtHours(avgLead)}`
        : 'No PR lead time data',
      status: deliveryStatus,
    },
    {
      label: 'Review',
      interpretation: avgReview != null
        ? `Avg response ${fmtHours(avgReview)}`
        : 'No review data',
      status: reviewStatus,
    },
    {
      label: 'Focus',
      interpretation: focusRatio != null
        ? `${(focusRatio * 100).toFixed(0)}% avg coding days`
        : 'No focus data',
      status: focusStatus,
    },
    {
      label: 'Risk',
      interpretation: avgMergeNoReview != null
        ? `${(avgMergeNoReview * 100).toFixed(0)}% unreviewed merges`
        : 'No merge review data',
      status: riskStatus,
    },
  ];
}

function computeHighlights(members: MemberSummaryDto[]): string[] {
  const highlights: string[] = [];

  const withLeadTime = members.filter((m) => (m.metrics.PR_LEAD_TIME_HOURS_MEDIAN ?? 0) > 0);
  if (withLeadTime.length) {
    const fastest = withLeadTime.reduce((a, b) =>
      (a.metrics.PR_LEAD_TIME_HOURS_MEDIAN ?? Infinity) < (b.metrics.PR_LEAD_TIME_HOURS_MEDIAN ?? Infinity) ? a : b
    );
    highlights.push(`${fastest.username}: fastest PR lead time (${fmtHours(fastest.metrics.PR_LEAD_TIME_HOURS_MEDIAN!)})`);
  }

  const byCommits = [...members].sort((a, b) =>
    (b.metrics.DAILY_COMMITS_COUNT ?? 0) - (a.metrics.DAILY_COMMITS_COUNT ?? 0)
  );
  if ((byCommits[0]?.metrics.DAILY_COMMITS_COUNT ?? 0) > 0) {
    highlights.push(`${byCommits[0].username}: most commits (${Math.round(byCommits[0].metrics.DAILY_COMMITS_COUNT!)})`);
  }

  const withChurn = members.filter((m) => (m.metrics.DAILY_CHURN_RATIO ?? 0) > 0);
  if (withChurn.length) {
    const highest = withChurn.reduce((a, b) =>
      (a.metrics.DAILY_CHURN_RATIO ?? 0) > (b.metrics.DAILY_CHURN_RATIO ?? 0) ? a : b
    );
    highlights.push(`${highest.username}: highest churn (${((highest.metrics.DAILY_CHURN_RATIO ?? 0) * 100).toFixed(0)}%)`);
  }

  const withReview = members.filter((m) => (m.metrics.REVIEW_RESPONSE_TIME_HOURS_MEDIAN ?? 0) > 0);
  if (withReview.length > 1) {
    const slowest = withReview.reduce((a, b) =>
      (a.metrics.REVIEW_RESPONSE_TIME_HOURS_MEDIAN ?? 0) > (b.metrics.REVIEW_RESPONSE_TIME_HOURS_MEDIAN ?? 0) ? a : b
    );
    highlights.push(`${slowest.username}: slowest review response (${fmtHours(slowest.metrics.REVIEW_RESPONSE_TIME_HOURS_MEDIAN!)})`);
  }

  return highlights;
}

function countWorkingDays(from: string, to: string): number {
  let count = 0;
  const cur = new Date(from + 'T00:00:00');
  const end = new Date(to + 'T00:00:00');
  while (cur <= end) {
    const dow = cur.getDay();
    if (dow !== 0 && dow !== 6) count++;
    cur.setDate(cur.getDate() + 1);
  }
  return count;
}

const signalIcons = [GitMerge, Eye, Target, Zap];

function summaryToText(s: MetricsSummaryDto): string {
  const lines: string[] = [];
  if (s.overview) lines.push('Team Overview\n' + s.overview);
  if (s.insights.length) lines.push('Insights\n' + s.insights.map((i) => `• ${i}`).join('\n'));
  if (s.recommendations.length) lines.push('Suggested Actions\n' + s.recommendations.map((r) => `• ${r}`).join('\n'));
  return lines.join('\n\n');
}

export function AiTeamInsightCard({ range, teamId, teamName, memberSummary, onSummaryGenerated }: Props) {
  const qc = useQueryClient();
  const cacheKey = ['ai-summary-team', teamId, range.from, range.to];

  const [summary, setSummary] = useState<MetricsSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [generatedForRange, setGeneratedForRange] = useState<DateRange | null>(null);
  const [copied, setCopied] = useState(false);

  // Restore from cache when teamId or range changes
  useEffect(() => {
    const cached = qc.getQueryData<{ summary: MetricsSummaryDto; generatedAt: number; range: DateRange }>(cacheKey);
    if (cached) {
      setSummary(cached.summary);
      setGeneratedAt(new Date(cached.generatedAt));
      setGeneratedForRange(cached.range);
      onSummaryGenerated?.(cached.summary);
    } else {
      setSummary(null);
      setGeneratedAt(null);
      setGeneratedForRange(null);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teamId, range.from, range.to]);

  const isOutdated =
    generatedForRange !== null &&
    (generatedForRange.from !== range.from || generatedForRange.to !== range.to);

  const hasData = memberSummary.length > 0;
  const signals = hasData ? computeSignals(memberSummary, range) : [];
  const highlights = hasData ? computeHighlights(memberSummary) : [];

  async function generate() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await aiApi.generateTeamSummary(teamId, range.from, range.to);
      const now = Date.now();
      setSummary(result);
      setGeneratedForRange(range);
      setGeneratedAt(new Date(now));
      qc.setQueryData(cacheKey, { summary: result, generatedAt: now, range });
      onSummaryGenerated?.(result);
    } catch {
      setError('AI summary is temporarily unavailable. Metrics remain accessible.');
    } finally {
      setIsLoading(false);
    }
  }

  async function copyToClipboard() {
    if (!summary) return;
    await navigator.clipboard.writeText(summaryToText(summary));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <Card>
      <CardHeader className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-violet-500" />
            <h2 className="text-sm font-semibold text-gray-900">AI Team Insight</h2>
          </div>
          <p className="text-xs text-gray-400 mt-0.5">{teamName} · Team-level interpretation for the selected period</p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0 ml-4">
          {isLoading && (
            <span className="animate-pulse bg-violet-50 text-violet-600 text-xs px-2 py-0.5 rounded-full font-medium">
              Generating...
            </span>
          )}
          {summary && !isLoading && (
            <span
              className={
                isOutdated
                  ? 'bg-amber-50 text-amber-600 text-xs px-2 py-0.5 rounded-full font-medium'
                  : 'bg-emerald-50 text-emerald-600 text-xs px-2 py-0.5 rounded-full font-medium'
              }
            >
              {isOutdated ? 'Outdated' : 'Fresh'}
            </span>
          )}
          {summary && !isLoading && (
            <button
              onClick={copyToClipboard}
              className="p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
              title="Copy to clipboard"
            >
              {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
            </button>
          )}
          <Button variant="secondary" size="sm" loading={isLoading} onClick={generate} disabled={!hasData}>
            {summary ? (
              <>
                <RefreshCw className="h-3.5 w-3.5" />
                Regenerate
              </>
            ) : (
              <>
                <Sparkles className="h-3.5 w-3.5" />
                Generate
              </>
            )}
          </Button>
        </div>
      </CardHeader>

      <CardBody>
        {/* No data at all */}
        {!hasData && (
          <div className="py-8 text-center">
            <Sparkles className="h-8 w-8 text-violet-200 mx-auto" />
            <p className="text-sm text-gray-400 mt-3">No team data for this period</p>
            <p className="text-xs text-gray-400 mt-1">Recalculate team metrics first</p>
          </div>
        )}

        {hasData && (
          <div className="space-y-5">
            {/* Loading skeleton for AI overview + actions */}
            {isLoading && (
              <div className="space-y-2">
                <div className="h-2.5 w-20 bg-gray-100 rounded animate-pulse" />
                <div className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: '92%' }} />
                <div className="h-3 bg-gray-100 rounded animate-pulse" style={{ width: '78%' }} />
              </div>
            )}

            {/* AI Team Overview — shown only after generation */}
            {summary && !isLoading && (
              <div>
                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  Team Overview
                </div>
                <p className="text-sm text-gray-700 leading-relaxed">{summary.overview}</p>
              </div>
            )}

            {/* Error state */}
            {error && !isLoading && (
              <div className="bg-amber-50 rounded-lg px-4 py-3 border border-amber-200 flex flex-col gap-2">
                <div className="flex items-start gap-2">
                  <AlertCircle className="h-4 w-4 text-amber-500 flex-shrink-0 mt-0.5" />
                  <span className="text-sm text-amber-700">{error}</span>
                </div>
                <Button variant="ghost" size="sm" onClick={generate} className="self-start">
                  Retry
                </Button>
              </div>
            )}

            {/* Signals grid — always shown when data available */}
            <div>
              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">
                Signals
              </div>
              <div className="grid grid-cols-2 gap-3">
                {signals.map((signal, i) => {
                  const chip = statusChip(signal.status);
                  const Icon = signalIcons[i];
                  return (
                    <div
                      key={signal.label}
                      className="bg-gray-50 rounded-lg border border-gray-100 px-4 py-3 flex flex-col gap-1.5"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1.5">
                          <Icon className="h-3.5 w-3.5 text-gray-400" />
                          <span className="text-xs font-medium text-gray-600">{signal.label}</span>
                        </div>
                        <span className={`text-xs font-medium px-1.5 py-0.5 rounded-full ${chip.className}`}>
                          {chip.text}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500">{signal.interpretation}</p>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Member highlights — always shown when data available */}
            {highlights.length > 0 && (
              <div>
                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  Member Highlights
                </div>
                <ul className="space-y-2">
                  {highlights.map((h, i) => (
                    <li key={i} className="flex items-start gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-violet-400 flex-shrink-0 mt-1.5" />
                      <span className="text-sm text-gray-700">{h}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Suggested actions — shown only after AI generation */}
            {summary && !isLoading && summary.recommendations.length > 0 && (
              <div>
                <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  Suggested Actions
                </div>
                <ul className="space-y-2">
                  {summary.recommendations.map((rec, i) => (
                    <li key={i} className="flex items-start gap-2.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0 mt-1.5" />
                      <span className="text-sm text-gray-700">{rec}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Footer */}
            {summary && !isLoading && (
              <div className="mt-4 pt-4 border-t border-gray-100 flex flex-wrap items-center gap-x-2 gap-y-1">
                <Shield className="h-3.5 w-3.5 text-gray-300" />
                <span className="text-xs text-gray-400">Generated locally from metrics only</span>
                <span className="text-gray-200">·</span>
                <span className="text-xs text-gray-400">{summary.modelName} via Ollama</span>
                {generatedAt && (
                  <>
                    <span className="text-gray-200">·</span>
                    <span className="text-xs text-gray-400">{timeAgo(generatedAt)}</span>
                  </>
                )}
              </div>
            )}

            {/* Prompt to generate when no summary yet */}
            {!summary && !isLoading && !error && (
              <div className="pt-1 flex items-center gap-2 text-xs text-gray-400">
                <Sparkles className="h-3.5 w-3.5 text-violet-300" />
                Generate AI analysis to get team overview and suggested actions
              </div>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
