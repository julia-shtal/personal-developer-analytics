import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Sparkles, RefreshCw, Shield, AlertCircle, Copy, Check } from 'lucide-react';
import { Chip } from '@/components/ui/Chip';
import { aiApi } from '@/api/ai';
import { AI } from '@/components/icons';
import type { MetricsSummaryDto } from '@/types/ai';
import type { DateRange, MemberSummaryDto } from '@/types';

interface Props {
  range: DateRange;
  teamId: number;
  teamName: string;
  memberSummary: MemberSummaryDto[];
  onSummaryGenerated?: (summary: MetricsSummaryDto) => void;
}

function fmtHours(h: number): string {
  if (h < 24) return `${h.toFixed(1)}h`;
  return `${(h / 24).toFixed(1)}d`;
}

function timeAgo(date: Date): string {
  const diffMin = Math.floor((Date.now() - date.getTime()) / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin} min ago`;
  return `${Math.floor(diffMin / 60)}h ago`;
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



function summaryToText(s: MetricsSummaryDto): string {
  const lines: string[] = [];
  if (s.headline) lines.push(s.headline);
  if (s.overview) lines.push('Team Overview\n' + s.overview);
  if (s.insights.length) lines.push('Insights\n' + s.insights.map((i) => `• [${i.kind}] ${i.text}`).join('\n'));
  if (s.recommendations.length) lines.push('Suggested Actions\n' + s.recommendations.map((r) => `• ${r}`).join('\n'));
  return lines.join('\n\n');
}

function insightSymbol(kind: string): { symbol: string; color: string } {
  if (kind === 'positive') return { symbol: '+', color: 'var(--emerald)' };
  if (kind === 'risk')     return { symbol: '!', color: 'var(--coral)' };
  return                          { symbol: '~', color: 'var(--amber)' };
}

export function AiTeamInsightCard({ range, teamId, memberSummary, onSummaryGenerated }: Omit<Props, 'teamName'> & { teamName?: string }) {
  const qc = useQueryClient();
  const cacheKey = ['ai-summary-team', teamId, range.from, range.to];

  const [summary, setSummary] = useState<MetricsSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [generatedForRange, setGeneratedForRange] = useState<DateRange | null>(null);
  const [copied, setCopied] = useState(false);

  const { data: dbLatest } = useQuery({
    queryKey: ['ai-team-summary-latest', teamId],
    queryFn: () => aiApi.latestTeamSummary(teamId),
    staleTime: Infinity,
    retry: false,
  });

  // Restore from in-memory cache when teamId or range changes
  useEffect(() => {
    const cached = qc.getQueryData<{ summary: MetricsSummaryDto; generatedAt: number; range: DateRange }>(cacheKey);
    if (cached) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
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

  // Fall back to DB-persisted latest when no in-memory summary is present
  useEffect(() => {
    if (!summary && dbLatest) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSummary(dbLatest);
      setGeneratedAt(dbLatest.generatedAt ? new Date(dbLatest.generatedAt) : null);
      setGeneratedForRange({ from: dbLatest.from, to: dbLatest.to });
      onSummaryGenerated?.(dbLatest);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dbLatest]);

  const isOutdated =
    generatedForRange !== null &&
    (generatedForRange.from !== range.from || generatedForRange.to !== range.to);

  const hasData = memberSummary.length > 0;
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
    <div className="card" style={{ padding: 22, marginBottom: 24, display: 'grid', gridTemplateColumns: '1fr 1.6fr', gap: 24 }}>
      {/* Left — label + headline */}
      <div>
        <div className="row gap-2" style={{ color: 'var(--violet)', marginBottom: 10 }}>
          <AI width={16} height={16} />
          <span className="t-label" style={{ color: 'var(--violet)' }}>TEAM INSIGHT</span>
        </div>
        {summary ? (
          <h3 className="t-h2" style={{ fontSize: 20, lineHeight: 1.3 }}>
            <em>{summary.headline}</em>
          </h3>
        ) : (
          <h3 className="t-h2" style={{ fontSize: 20, lineHeight: 1.3, color: 'var(--fg-3)' }}>
            {isLoading ? 'Generating insight…' : 'Generate an AI summary to see team narrative.'}
          </h3>
        )}

        {/* Actions */}
        <div className="row gap-2" style={{ marginTop: 14, flexWrap: 'wrap' }}>
          {summary && !isLoading && (
            <Chip color={isOutdated ? 'amber' : 'emerald'}>{isOutdated ? 'Outdated' : 'Fresh'}</Chip>
          )}
          {isLoading && <Chip color="violet">Generating…</Chip>}
          {summary && !isLoading && (
            <button
              className="btn btn-sm btn-icon"
              onClick={copyToClipboard}
              title="Copy to clipboard"
              aria-label="Copy summary"
            >
              {copied ? <Check width={12} height={12} /> : <Copy width={12} height={12} />}
            </button>
          )}
          <button
            className="btn btn-sm"
            onClick={generate}
            disabled={isLoading || !hasData}
            aria-label={summary ? 'Regenerate AI summary' : 'Generate AI summary'}
          >
            {summary ? (
              <><RefreshCw width={12} height={12} />regenerate</>
            ) : (
              <><Sparkles width={12} height={12} />generate</>
            )}
          </button>
        </div>
      </div>

      {/* Right — insights */}
      <div className="col gap-3" style={{ justifyContent: 'center' }}>
        {/* No data */}
        {!hasData && (
          <p className="t-muted" style={{ fontSize: 13 }}>No team data for this period. Recalculate team metrics first.</p>
        )}

        {/* Error */}
        {error && !isLoading && (
          <div className="row gap-2" style={{ color: 'var(--coral)', alignItems: 'flex-start' }}>
            <AlertCircle width={14} height={14} style={{ flexShrink: 0, marginTop: 2 }} />
            <p className="t-body" style={{ margin: 0, fontSize: 13 }}>{error}</p>
          </div>
        )}

        {/* AI structured insights */}
        {summary && !isLoading && summary.insights.map((insight, i) => {
          const { symbol, color } = insightSymbol(insight.kind);
          return (
            <div key={i} className="row gap-3" style={{ alignItems: 'flex-start' }}>
              <span className="font-mono" style={{ color, width: 18, fontWeight: 600, flexShrink: 0 }}>{symbol}</span>
              <p className="t-body" style={{ margin: 0 }}>{insight.text}</p>
            </div>
          );
        })}

        {/* Fallback highlights when no AI summary yet */}
        {!summary && !isLoading && !error && hasData && highlights.slice(0, 3).map((h, i) => (
          <div key={i} className="row gap-3" style={{ alignItems: 'flex-start' }}>
            <span className="font-mono" style={{ color: 'var(--fg-3)', width: 18 }}>·</span>
            <p className="t-body" style={{ margin: 0 }}>{h}</p>
          </div>
        ))}

        {/* Footer */}
        {summary && !isLoading && (
          <div className="row gap-2" style={{ marginTop: 4, flexWrap: 'wrap' }}>
            <Shield width={12} height={12} style={{ color: 'var(--fg-3)', flexShrink: 0 }} />
            <span className="t-label" style={{ fontSize: 10 }}>local · {summary.modelName} via Ollama</span>
            {generatedAt && (
              <span className="t-label" style={{ fontSize: 10 }}>· {timeAgo(generatedAt)}</span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
