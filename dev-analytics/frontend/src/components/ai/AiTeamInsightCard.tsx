import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { clsx } from 'clsx';
import {
  RefreshCw, Shield, AlertCircle,
  Square, Download, FileDown, FileCode, FileText, FileJson, Printer, Copy, Check,
} from 'lucide-react';
import { Chip } from '@/components/ui/Chip';
import { aiApi } from '@/api/ai';
import { AI } from '@/components/icons';
import { timeAgo } from '@/lib/dates';
import { summaryToText, summaryToMarkdown, summaryToHtml, downloadFile } from '@/lib/export';
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

function insightSymbol(kind: string): { symbol: string; color: string } {
  if (kind === 'positive') return { symbol: '+', color: 'var(--emerald)' };
  if (kind === 'risk')     return { symbol: '!', color: 'var(--coral)' };
  return                          { symbol: '~', color: 'var(--amber)' };
}

export function AiTeamInsightCard({ range, teamId, memberSummary, onSummaryGenerated }: Omit<Props, 'teamName'> & { teamName?: string }) {
  const qc = useQueryClient();
  const cacheKey = ['ai-summary-team', teamId, range.from, range.to];

  const abortRef = useRef<AbortController | null>(null);
  const exportMenuRef = useRef<HTMLDetailsElement>(null);

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
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const result = await aiApi.generateTeamSummary(teamId, range.from, range.to, controller.signal);
      const now = Date.now();
      setSummary(result);
      setGeneratedForRange(range);
      setGeneratedAt(new Date(now));
      qc.setQueryData(cacheKey, { summary: result, generatedAt: now, range });
      onSummaryGenerated?.(result);
    } catch (err) {
      if (axios.isCancel(err)) {
        // user aborted — reset silently
      } else {
        setError('AI summary is temporarily unavailable. Metrics remain accessible.');
      }
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null;
        setIsLoading(false);
      }
    }
  }

  function stop() {
    abortRef.current?.abort();
  }

  function closeExportMenu() {
    if (exportMenuRef.current) exportMenuRef.current.open = false;
  }

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (exportMenuRef.current?.open && !exportMenuRef.current.contains(e.target as Node)) {
        exportMenuRef.current.open = false;
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  async function copyToClipboard() {
    if (!summary) return;
    await navigator.clipboard.writeText(summaryToText(summary));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    closeExportMenu();
  }

  function exportAs(format: 'md' | 'html' | 'pdf' | 'txt' | 'json') {
    if (!summary) return;
    const stamp = `team-${teamId}-${summary.to}`;
    if (format === 'md') {
      downloadFile(`ai-team-insight-${stamp}.md`, summaryToMarkdown(summary), 'text/markdown');
    } else if (format === 'html') {
      downloadFile(`ai-team-insight-${stamp}.html`, summaryToHtml(summary), 'text/html');
    } else if (format === 'txt') {
      downloadFile(`ai-team-insight-${stamp}.txt`, summaryToText(summary), 'text/plain');
    } else if (format === 'json') {
      downloadFile(`ai-team-insight-${stamp}.json`, JSON.stringify(summary, null, 2), 'application/json');
    } else if (format === 'pdf') {
      const w = window.open('', '_blank');
      if (w) {
        w.document.write(summaryToHtml(summary));
        w.document.close();
        w.focus();
        w.print();
      }
    }
    closeExportMenu();
  }

  return (
    <div className="card" style={{ padding: 22, marginBottom: 24, display: 'grid', gridTemplateColumns: '1fr 1.6fr', gap: 24 }}>
      {/* Left — label + headline */}
      <div style={{ position: 'relative', zIndex: 1 }}>
        <div className="row gap-2" style={{ color: 'var(--accent)', marginBottom: 10 }}>
          <AI width={16} height={16} />
          <span className="t-label" style={{ color: 'var(--accent)' }}>TEAM INSIGHT</span>
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

          {/* Export menu */}
          {summary && !isLoading && (
            <details className="export-menu" ref={exportMenuRef} style={{ position: 'relative' }}>
              <summary
                role="button"
                className="btn btn-sm btn-icon"
                style={{ listStyle: 'none', cursor: 'pointer' }}
                title="Export insight"
                aria-label="Export insight"
              >
                <Download width={12} height={12} />
              </summary>
              <div className="card export-menu-panel">
                <span className="t-label export-menu-label">Share</span>
                <button className="menu-item" onClick={copyToClipboard}>
                  {copied ? <Check width={13} height={13} style={{ color: 'var(--emerald)' }} /> : <Copy width={13} height={13} />}
                  <span>{copied ? 'Copied to clipboard' : 'Copy to clipboard'}</span>
                </button>
                <span className="divider-2 export-menu-divider" />
                <span className="t-label export-menu-label">Download</span>
                <button className="menu-item" onClick={() => exportAs('md')}>
                  <FileDown width={13} height={13} /><span>Markdown</span><span className="export-menu-ext font-mono">.md</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('html')}>
                  <FileCode width={13} height={13} /><span>HTML document</span><span className="export-menu-ext font-mono">.html</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('pdf')}>
                  <Printer width={13} height={13} /><span>Print to PDF…</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('txt')}>
                  <FileText width={13} height={13} /><span>Plain text</span><span className="export-menu-ext font-mono">.txt</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('json')}>
                  <FileJson width={13} height={13} /><span>Raw JSON</span><span className="export-menu-ext font-mono">.json</span>
                </button>
              </div>
            </details>
          )}

          {/* Generate / Regenerate */}
          <button
            className={clsx('btn btn-sm', isLoading && 'is-disabled')}
            onClick={generate}
            disabled={isLoading || !hasData}
            aria-disabled={isLoading}
            aria-label={summary ? 'Regenerate AI team insight' : 'Generate AI team insight'}
          >
            <RefreshCw width={12} height={12} className={clsx(isLoading && 'animate-spin')} />
            {summary ? 'Regenerate' : 'Generate'}
          </button>

          {/* Stop — only while generating */}
          {isLoading && (
            <button
              className="btn btn-sm"
              onClick={stop}
              aria-label="Stop generating team insight"
              style={{ color: 'var(--coral)', borderColor: 'var(--coral)' }}
            >
              <Square width={12} height={12} />
              Stop
            </button>
          )}
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

        {/* Loading skeleton */}
        {isLoading && (
          <div className="col gap-3">
            {[85, 70, 90, 65, 75].map((w, i) => (
              <div
                key={i}
                style={{
                  height: 11,
                  width: `${w}%`,
                  background: 'var(--bg-2)',
                  borderRadius: 4,
                  animation: 'pulse 1.5s infinite',
                }}
              />
            ))}
          </div>
        )}

        {/* AI structured insights */}
        {summary && !isLoading && summary.insights.map((insight, i) => {
          const { symbol, color } = insightSymbol(insight.kind);
          return (
            <div key={i} className="col gap-1">
              <div className="row gap-3" style={{ alignItems: 'flex-start' }}>
                <span className="font-mono" style={{ color, width: 18, fontWeight: 600, flexShrink: 0 }}>{symbol}</span>
                <p className="t-body" style={{ margin: 0 }}>{insight.text}</p>
              </div>
              {insight.explanation && (
                <p
                  className="t-muted"
                  style={{ margin: 0, marginLeft: 21, fontSize: 12, lineHeight: 1.5 }}
                >
                  {insight.explanation}
                </p>
              )}
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
