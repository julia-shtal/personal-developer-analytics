/** Discriminated result of comparing two period totals. */
export type Delta =
  | { kind: 'pct'; value: number }
  | { kind: 'new' }
  | { kind: 'none' };

/**
 * Single source of truth for the delta edge cases. A comparison total of zero
 * has no meaningful percentage: it is either brand-new activity (`new`) or no
 * activity at all (`none`), never `Infinity%`.
 */
export function computeDelta(primaryTotal: number, comparisonTotal: number): Delta {
  if (comparisonTotal === 0) {
    return primaryTotal > 0 ? { kind: 'new' } : { kind: 'none' };
  }
  return { kind: 'pct', value: ((primaryTotal - comparisonTotal) / comparisonTotal) * 100 };
}
