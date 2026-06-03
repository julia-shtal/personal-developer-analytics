import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Shield, Copy, Check, RefreshCw, Sparkles, History } from 'lucide-react';
import { Chip } from '@/components/ui/Chip';
import { ProseWithNumbers } from '@/components/ui/ProseWithNumbers';
import { FollowUpDrawer } from '@/components/ai/FollowUpDrawer';
import { SummaryHistoryDrawer } from '@/components/ai/SummaryHistoryDrawer';
import { aiApi } from '@/api/ai';
import { AI } from '@/components/icons';
import { timeAgo } from '@/lib/dates';
import type { MetricsSummaryDto } from '@/types/ai';
import type { DateRange } from '@/types';

interface Props {
  range: DateRange;
  onSummaryGenerated?: (summary: MetricsSummaryDto) => void;
}

function summaryToText(s: MetricsSummaryDto): string {
  const lines: string[] = [];
  if (s.headline) lines.push(s.headline);
  if (s.overview) lines.push('\nOverview\n' + s.overview);
  if (s.insights.length) {
    lines.push('\nKey Insights\n' + s.insights.map((i) => `• ${i.text}`).join('\n'));
  }
  if (s.recommendations.length) {
    lines.push('\nRecommendations\n' + s.recommendations.map((r) => `• ${r}`).join('\n'));
  }
  return lines.join('');
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

  async function generate() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await aiApi.generateSummary(range.from, range.to);
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
    <div className="card" style={{ padding: 24 }}>

      {/* ── Top row ── */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18, gap: 12, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0, flex: '1 1 auto' }}>
          <div className="row gap-2" style={{ color: 'var(--violet-strong)', marginBottom: 8, flexWrap: 'wrap' }}>
            <AI width={16} height={16} />
            <span className="t-label" style={{ color: 'var(--violet-strong)', letterSpacing: '0.08em' }}>AI SUMMARY</span>
            <span className="tick">·</span>
            <span className="t-label" style={{ fontSize: 10.5 }}>Automatic overview for the selected period</span>
          </div>
          {summary?.headline && (
            <h3 className="t-h2" style={{ fontSize: 22, lineHeight: 1.25, maxWidth: 760 }}>
              {summary.headline}
            </h3>
          )}
        </div>
        <div className="row gap-2" style={{ flexShrink: 0 }}>
          {isLoading && (
            <Chip color="violet">Generating…</Chip>
          )}
          {summary && !isLoading && (
            <Chip color={isOutdated ? 'amber' : 'emerald'} dot>
              {isOutdated ? 'Outdated' : 'Fresh'}
            </Chip>
          )}
          {summary && !isLoading && (
            <button
              className="btn btn-sm btn-icon"
              onClick={copyToClipboard}
              title="Copy to clipboard"
              aria-label="Copy summary to clipboard"
            >
              {copied ? <Check width={12} height={12} /> : <Copy width={12} height={12} />}
            </button>
          )}
          <button className="btn btn-sm" onClick={generate} disabled={isLoading} aria-label={summary ? 'Regenerate AI summary' : 'Generate AI summary'}>
            <RefreshCw width={12} height={12} />
            {summary ? 'Regenerate' : 'Generate'}
          </button>
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
                    <div key={i} className="row gap-3" style={{ alignItems: 'flex-start', padding: '6px 0' }}>
                      <span style={{
                        fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 600,
                        color,
                        width: 18, lineHeight: 1.45, flexShrink: 0,
                      }}>{sym}</span>
                      <ProseWithNumbers text={insight.text} className="t-body" style={{ margin: 0, lineHeight: 1.55, flex: 1 }} />
                      {insight.metric && <Chip color={chipColor}>{insight.metric}</Chip>}
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
                    <span className="chip-dot" style={{ color: 'var(--violet)', marginTop: 7, flexShrink: 0 }} />
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
