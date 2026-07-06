import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { clsx } from 'clsx';
import { Shield, Copy, Check, RefreshCw, Sparkles, History, Square, Download, FileDown, FileCode, FileText, FileJson, Printer } from 'lucide-react';
import { Chip } from '@/components/ui/Chip';
import { ProseWithNumbers } from '@/components/ui/ProseWithNumbers';
import { FollowUpDrawer } from '@/components/ai/FollowUpDrawer';
import { SummaryHistoryDrawer } from '@/components/ai/SummaryHistoryDrawer';
import { aiApi } from '@/api/ai';
import { AI } from '@/components/icons';
import { timeAgo } from '@/lib/dates';
import { summaryToText, summaryToMarkdown, summaryToHtml, downloadFile } from '@/lib/export';
import type { MetricsSummaryDto } from '@/types/ai';
import type { DateRange } from '@/types';

interface Props {
  range: DateRange;
  onSummaryGenerated?: (summary: MetricsSummaryDto) => void;
}

const KIND_COLOR: Record<string, string> = {
  positive: 'var(--emerald)',
  risk: 'var(--coral)',
  note: 'var(--amber)',
};

const KIND_CHIP: Record<string, 'emerald' | 'coral' | 'amber'> = {
  positive: 'emerald',
  risk: 'coral',
  note: 'amber',
};

const KIND_SYM: Record<string, string> = {
  positive: '+',
  risk: '!',
  note: '~',
};

export function AiSummaryCard({ range, onSummaryGenerated }: Props) {
  const qc = useQueryClient();
  const cacheKey = ['ai-summary-personal', range.from, range.to];

  const [summary, setSummary] = useState<MetricsSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [generatedForRange, setGeneratedForRange] = useState<DateRange | null>(null);
  const [copied, setCopied] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const exportMenuRef = useRef<HTMLDetailsElement>(null);

  const { data: dbLatest } = useQuery({
    queryKey: ['ai-summary-latest'],
    queryFn: () => aiApi.latestSummary(),
    staleTime: Infinity,
    retry: false,
  });

  // Restore from in-memory cache when range changes
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
  }, [range.from, range.to]);

  // Fall back to DB-persisted latest when no in-memory summary is present
  useEffect(() => {
    if (!summary && dbLatest) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time hydration from the DB-persisted latest summary when no in-memory summary exists; guarded and depends only on dbLatest
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

  async function generate() {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setIsLoading(true);
    setError(null);
    try {
      const result = await aiApi.generateSummary(range.from, range.to, undefined, controller.signal);
      const now = Date.now();
      setSummary(result);
      setGeneratedForRange(range);
      setGeneratedAt(new Date(now));
      qc.setQueryData(cacheKey, { summary: result, generatedAt: now, range });
      onSummaryGenerated?.(result);
    } catch (err) {
      if (axios.isCancel(err)) {
        // User aborted — reset to idle silently (no error banner).
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

  async function copyToClipboard() {
    if (!summary) return;
    await navigator.clipboard.writeText(summaryToText(summary));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    closeExportMenu();
  }

  function closeExportMenu() {
    if (exportMenuRef.current) exportMenuRef.current.open = false;
  }

  // Close the export menu when the user clicks outside it
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (exportMenuRef.current?.open && !exportMenuRef.current.contains(e.target as Node)) {
        exportMenuRef.current.open = false;
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  function exportAs(format: 'md' | 'html' | 'pdf' | 'txt' | 'json') {
    if (!summary) return;
    const stamp = summary.to;
    if (format === 'md') {
      downloadFile(`ai-summary-${stamp}.md`, summaryToMarkdown(summary), 'text/markdown');
    } else if (format === 'html') {
      downloadFile(`ai-summary-${stamp}.html`, summaryToHtml(summary), 'text/html');
    } else if (format === 'txt') {
      downloadFile(`ai-summary-${stamp}.txt`, summaryToText(summary), 'text/plain');
    } else if (format === 'json') {
      downloadFile(`ai-summary-${stamp}.json`, JSON.stringify(summary, null, 2), 'application/json');
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
    <div className="card" style={{ padding: 24 }}>

      {/* ── Top row ── */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18, gap: 12, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0, flex: '1 1 auto' }}>
          <div className="row gap-2" style={{ color: 'var(--accent-strong)', marginBottom: 8, flexWrap: 'wrap' }}>
            <AI width={16} height={16} />
            <span className="t-label" style={{ color: 'var(--accent-strong)', letterSpacing: '0.08em' }}>AI SUMMARY</span>
            <span className="tick">·</span>
            <span className="t-label" style={{ fontSize: 10.5 }}>Automatic overview for the selected period</span>
          </div>
          {summary?.headline && (
            <h3 className="t-h2" style={{ fontSize: 22, lineHeight: 1.25, maxWidth: 760 }}>
              {summary.headline}
            </h3>
          )}
        </div>
        <div className="row gap-2" style={{ flexShrink: 0, alignItems: 'center' }}>
          {isLoading && (
            <Chip accent>Generating…</Chip>
          )}
          {summary && !isLoading && (
            <Chip color={isOutdated ? 'amber' : 'emerald'} dot>
              {isOutdated ? 'Outdated' : 'Fresh'}
            </Chip>
          )}

          {/* Export menu — only when a summary exists and we're idle */}
          {summary && !isLoading && (
            <details className="export-menu" ref={exportMenuRef} style={{ position: 'relative' }}>
              <summary
                className="btn btn-sm btn-icon"
                style={{ listStyle: 'none', cursor: 'pointer' }}
                title="Export summary"
                aria-label="Export summary"
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
                  <FileDown width={13} height={13} />
                  <span>Markdown</span>
                  <span className="export-menu-ext font-mono">.md</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('html')}>
                  <FileCode width={13} height={13} />
                  <span>HTML document</span>
                  <span className="export-menu-ext font-mono">.html</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('pdf')}>
                  <Printer width={13} height={13} />
                  <span>Print to PDF…</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('txt')}>
                  <FileText width={13} height={13} />
                  <span>Plain text</span>
                  <span className="export-menu-ext font-mono">.txt</span>
                </button>
                <button className="menu-item" onClick={() => exportAs('json')}>
                  <FileJson width={13} height={13} />
                  <span>Raw JSON</span>
                  <span className="export-menu-ext font-mono">.json</span>
                </button>
              </div>
            </details>
          )}

          {/* Regenerate stays visible but is clearly disabled while generating */}
          <button
            className={clsx('btn btn-sm', isLoading && 'is-disabled')}
            onClick={generate}
            disabled={isLoading}
            aria-disabled={isLoading}
            title={isLoading ? 'Generating…' : undefined}
            aria-label={summary ? 'Regenerate AI summary' : 'Generate AI summary'}
          >
            <RefreshCw width={12} height={12} className={clsx(isLoading && 'animate-spin')} />
            {summary ? 'Regenerate' : 'Generate'}
          </button>

          {/* Stop button — only while generating */}
          {isLoading && (
            <button
              className="btn btn-sm"
              onClick={stop}
              aria-label="Stop generating summary"
              style={{ color: 'var(--coral)', borderColor: 'var(--coral)' }}
            >
              <Square width={12} height={12} />
              Stop
            </button>
          )}
        </div>
      </div>

      {/* ── Loading skeleton ── */}
      {isLoading && (
        <div className="col gap-4">
          {[90, 75, 60, 85, 70].map((w, i) => (
            <div key={i} style={{ height: 12, width: `${w}%`, background: 'var(--bg-2)', borderRadius: 4, animation: 'pulse 1.5s infinite' }} />
          ))}
        </div>
      )}

      {/* ── Error state ── */}
      {error && !isLoading && (
        <div style={{ background: 'var(--amber-bg)', border: '1px solid var(--amber)', borderRadius: 8, padding: '12px 16px' }}>
          <p className="t-body" style={{ color: 'var(--amber-strong)', marginBottom: 8 }}>{error}</p>
          <button className="btn btn-sm" onClick={generate}>Retry</button>
        </div>
      )}

      {/* ── Empty state ── */}
      {!summary && !isLoading && !error && (
        <div style={{ padding: '32px 0', textAlign: 'center' }}>
          <Sparkles style={{ width: 32, height: 32, color: 'var(--line)', margin: '0 auto 12px' }} />
          <p className="t-muted" style={{ marginBottom: 16 }}>No summary generated for this period</p>
          <button className="btn btn-sm btn-accent" onClick={generate}>
            <Sparkles width={12} height={12} />
            Generate AI Summary
          </button>
        </div>
      )}

      {/* ── Summary content ── */}
      {summary && !isLoading && (
        <>
          {/* Overview */}
          <div style={{ marginTop: 16 }}>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>overview</div>
            <ProseWithNumbers text={summary.overview} className="t-body" style={{ maxWidth: 920, lineHeight: 1.6 }} />
          </div>

          {/* Key insights */}
          {summary.insights.length > 0 && (
            <div style={{ marginTop: 22 }}>
              <div className="t-eyebrow" style={{ marginBottom: 10 }}>key insights</div>
              <div className="col gap-2">
                {summary.insights.map((insight, i) => {
                  const color = KIND_COLOR[insight.kind] ?? KIND_COLOR.note;
                  const chipColor = KIND_CHIP[insight.kind] ?? KIND_CHIP.note;
                  const sym = KIND_SYM[insight.kind] ?? KIND_SYM.note;
                  return (
                    <div key={i} className="col gap-1" style={{ padding: '6px 0' }}>
                      <div className="row gap-3" style={{ alignItems: 'flex-start' }}>
                        <span style={{
                          fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 600,
                          color,
                          width: 18, lineHeight: 1.45, flexShrink: 0,
                        }}>{sym}</span>
                        <ProseWithNumbers text={insight.text} className="t-body" style={{ margin: 0, lineHeight: 1.55, flex: 1 }} />
                        {insight.metric && <Chip color={chipColor}>{insight.metric}</Chip>}
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
              </div>
            </div>
          )}

          {/* Recommendations */}
          {summary.recommendations.length > 0 && (
            <div style={{ marginTop: 22 }}>
              <div className="t-eyebrow" style={{ marginBottom: 10 }}>recommendations</div>
              <div className="col gap-2">
                {summary.recommendations.map((rec, i) => (
                  <div key={i} className="row gap-3" style={{ alignItems: 'flex-start', padding: '6px 0' }}>
                    <span className="chip-dot" style={{ color: 'var(--accent)', marginTop: 7, flexShrink: 0 }} />
                    <ProseWithNumbers text={rec} className="t-body" style={{ margin: 0, lineHeight: 1.55 }} />
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Footer */}
          <div className="row gap-2" style={{ marginTop: 22, paddingTop: 14, borderTop: '1px solid var(--line-2)', color: 'var(--fg-3)', flexWrap: 'wrap' }}>
            <Shield width={12} height={12} />
            <span className="t-label" style={{ fontSize: 10.5 }}>Generated locally from metrics only</span>
            <span className="tick">·</span>
            <span className="t-label" style={{ fontSize: 10.5 }}>{summary.modelName} via Ollama</span>
            {generatedAt && (
              <>
                <span className="tick">·</span>
                <span className="t-label" style={{ fontSize: 10.5 }}>{timeAgo(generatedAt)}</span>
              </>
            )}
            <span style={{ flex: 1 }} />
            <button
              className="btn btn-sm"
              onClick={() => setHistoryOpen(true)}
              aria-label="View summary history"
            >
              <History width={12} height={12} />
              History
            </button>
            <button
              className="btn btn-sm btn-accent"
              onClick={() => setDrawerOpen(true)}
              aria-label="Ask follow-up questions about this summary"
            >
              <Sparkles width={12} height={12} />
              Ask follow-up
            </button>
          </div>
        </>
      )}

      {summary && (
        <FollowUpDrawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          summary={summary}
        />
      )}

      <SummaryHistoryDrawer
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      />
    </div>
  );
}
