import type { DateRange } from '@/types';
import { formatDate } from '@/lib/dates';
import { computeDelta, type Delta } from './computeDelta';

const SUCCESS = 'var(--emerald)';
const DANGER = 'var(--coral)';
const MUTED = 'var(--fg-3)';

function badge(delta: Delta): { text: string; color: string } {
  if (delta.kind === 'new') return { text: 'new', color: SUCCESS };
  if (delta.kind === 'none') return { text: '– no change', color: MUTED };
  const rounded = Math.round(delta.value);
  if (rounded > 0) return { text: `▲ +${rounded}%`, color: SUCCESS };
  if (rounded < 0) return { text: `▼ −${Math.abs(rounded)}%`, color: DANGER };
  return { text: '– 0%', color: MUTED };
}

interface CompareDeltaProps {
  label: string;
  primaryTotal: number;
  comparisonTotal: number;
  primaryRange: DateRange;
  comparisonRange: DateRange;
}

/**
 * Presentational delta headline: primary total, a signed coloured badge, and a
 * "vs previous" caption. Pure — all branching lives in {@link computeDelta}.
 */
export function CompareDelta({
  label,
  primaryTotal,
  comparisonTotal,
  primaryRange,
  comparisonRange,
}: CompareDeltaProps) {
  const { text, color } = badge(computeDelta(primaryTotal, comparisonTotal));
  const title =
    `${formatDate(primaryRange.from)}–${formatDate(primaryRange.to)}` +
    ` vs ${formatDate(comparisonRange.from)}–${formatDate(comparisonRange.to)}`;

  return (
    <div className="row" style={{ gap: 8, alignItems: 'baseline', flexWrap: 'wrap' }} title={title}>
      <span className="t-label" style={{ color: MUTED }}>{label}</span>
      <span className="font-mono" style={{ fontSize: 16, color: 'var(--fg)' }}>{primaryTotal}</span>
      <span className="font-mono" style={{ fontSize: 12, fontWeight: 600, color }}>{text}</span>
      <span className="t-label" style={{ color: MUTED }}>vs previous</span>
    </div>
  );
}
