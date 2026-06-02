import { useState } from 'react';
import { X, History, ChevronDown, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Spinner } from '@/components/ui/Spinner';
import { Chip } from '@/components/ui/Chip';
import { ProseWithNumbers } from '@/components/ui/ProseWithNumbers';
import { aiApi } from '@/api/ai';
import { timeAgo } from '@/lib/dates';
import type { MetricsSummaryDto } from '@/types/ai';

interface Props {
  open: boolean;
  onClose: () => void;
}

const KIND_SYM: Record<string, string> = { positive: '+', risk: '!', note: '~' };
const KIND_CHIP: Record<string, 'emerald' | 'coral' | 'amber'> = {
  positive: 'emerald', risk: 'coral', note: 'amber',
};

function formatRange(from: string, to: string) {
  const fmt = (d: string) => new Date(d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return `${fmt(from)} – ${fmt(to)}`;
}

function HistoryItem({ summary }: { summary: MetricsSummaryDto }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div style={{ borderBottom: '1px solid var(--line-2)' }}>
      <button
        onClick={() => setExpanded((v) => !v)}
        style={{
          width: '100%', textAlign: 'left',
          padding: '12px 18px',
          background: 'transparent', border: 'none',
          cursor: 'pointer',
          display: 'flex', alignItems: 'flex-start', gap: 10,
        }}
      >
        <span style={{ color: 'var(--fg-3)', marginTop: 2, flexShrink: 0 }}>
          {expanded
            ? <ChevronDown width={14} height={14} />
            : <ChevronRight width={14} height={14} />}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="row gap-2" style={{ flexWrap: 'wrap', marginBottom: 4 }}>
            <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>
              {formatRange(summary.from, summary.to)}
            </span>
            {summary.generatedAt && (
              <>
                <span className="tick">·</span>
                <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>
                  {timeAgo(new Date(summary.generatedAt))}
                </span>
              </>
            )}
          </div>
          <p style={{ fontSize: 13, fontWeight: 500, color: 'var(--fg)', margin: 0, lineHeight: 1.4 }}>
            {summary.headline || '(no headline)'}
          </p>
        </div>
      </button>

      {expanded && (
        <div style={{ padding: '0 18px 16px 42px' }}>
          {summary.overview && (
            <ProseWithNumbers
              text={summary.overview}
              className="t-body"
              style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--fg-2)', marginBottom: 12 }}
            />
          )}

          {summary.insights.length > 0 && (
            <div style={{ marginBottom: 10 }}>
              <div className="t-eyebrow" style={{ fontSize: 9, marginBottom: 6 }}>insights</div>
              <div className="col gap-1">
                {summary.insights.map((ins, i) => (
                  <div key={i} className="row gap-2" style={{ alignItems: 'flex-start' }}>
                    <span style={{
                      fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700,
                      color: ins.kind === 'positive' ? 'var(--emerald)'
                        : ins.kind === 'risk' ? 'var(--coral)' : 'var(--amber)',
                      flexShrink: 0, width: 14,
                    }}>
                      {KIND_SYM[ins.kind] ?? '~'}
                    </span>
                    <span style={{ fontSize: 12, color: 'var(--fg)', lineHeight: 1.5, flex: 1 }}>{ins.text}</span>
                    {ins.metric && <Chip color={KIND_CHIP[ins.kind] ?? 'amber'}>{ins.metric}</Chip>}
                  </div>
                ))}
              </div>
            </div>
          )}

          {summary.recommendations.length > 0 && (
            <div>
              <div className="t-eyebrow" style={{ fontSize: 9, marginBottom: 6 }}>recommendations</div>
              <div className="col gap-1">
                {summary.recommendations.map((r, i) => (
                  <div key={i} className="row gap-2" style={{ alignItems: 'flex-start' }}>
                    <span className="chip-dot" style={{ color: 'var(--violet)', marginTop: 6, flexShrink: 0 }} />
                    <span style={{ fontSize: 12, color: 'var(--fg)', lineHeight: 1.5 }}>{r}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function SummaryHistoryDrawer({ open, onClose }: Props) {
  const { data: history = [], isLoading, isError } = useQuery({
    queryKey: ['ai-summary-history'],
    queryFn: () => aiApi.summaryHistory(20),
    enabled: open,
    staleTime: 1000 * 60 * 5,
  });

  if (!open) return null;

  return (
    <>
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0, zIndex: 49,
          background: 'color-mix(in oklab, #000 25%, transparent)',
        }}
        aria-hidden="true"
      />

      <div
        role="dialog"
        aria-label="AI summary history"
        style={{
          position: 'fixed', right: 0, top: 0,
          height: '100vh', width: 'min(520px, 100vw)',
          background: 'var(--bg-card)',
          borderLeft: '1px solid var(--line)',
          boxShadow: '-4px 0 32px rgba(0,0,0,.14)',
          display: 'flex', flexDirection: 'column',
          zIndex: 50,
          animation: 'drawer-slide-in .2s cubic-bezier(.2,.9,.3,1)',
        }}
      >
        {/* Header */}
        <div className="row gap-2" style={{
          padding: '14px 18px',
          borderBottom: '1px solid var(--line-2)',
          flexShrink: 0,
        }}>
          <button
            onClick={onClose}
            className="btn btn-sm btn-icon"
            aria-label="Close history"
            style={{ marginRight: 4 }}
          >
            <X width={14} height={14} />
          </button>
          <History width={14} height={14} style={{ color: 'var(--violet-strong)' }} />
          <span className="t-label" style={{ color: 'var(--violet-strong)', letterSpacing: '0.06em' }}>
            INSIGHT HISTORY
          </span>
        </div>

        {/* Content */}
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {isLoading && (
            <div className="row gap-2" style={{ padding: 18 }}>
              <Spinner size="sm" />
              <span className="t-muted" style={{ fontSize: 12 }}>Loading…</span>
            </div>
          )}

          {isError && (
            <div style={{ padding: 18, fontSize: 13, color: 'var(--coral-strong)' }}>
              Failed to load history.
            </div>
          )}

          {!isLoading && !isError && history.length === 0 && (
            <div style={{ padding: '40px 18px', textAlign: 'center' }}>
              <History width={28} height={28} style={{ color: 'var(--line)', margin: '0 auto 12px' }} />
              <p className="t-muted" style={{ fontSize: 12 }}>No summaries generated yet.</p>
              <p className="t-muted" style={{ fontSize: 11, marginTop: 4 }}>
                Generate your first AI summary from the dashboard.
              </p>
            </div>
          )}

          {history.map((s, i) => (
            <HistoryItem key={`${s.from}-${s.to}-${i}`} summary={s} />
          ))}
        </div>
      </div>

      <style>{`
        @keyframes drawer-slide-in {
          from { transform: translateX(32px); opacity: 0 }
          to   { transform: none; opacity: 1 }
        }
      `}</style>
    </>
  );
}