import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CompareDelta } from './CompareDelta';
import { computeDelta } from './computeDelta';
import type { DateRange } from '@/types';

const primaryRange: DateRange = { from: '2026-06-01', to: '2026-06-08' };
const comparisonRange: DateRange = { from: '2026-05-24', to: '2026-05-31' };

function renderDelta(primaryTotal: number, comparisonTotal: number) {
  return render(
    <CompareDelta
      label="commits"
      primaryTotal={primaryTotal}
      comparisonTotal={comparisonTotal}
      primaryRange={primaryRange}
      comparisonRange={comparisonRange}
    />,
  );
}

describe('computeDelta', () => {
  it('returns a positive percentage when the primary total is higher', () => {
    expect(computeDelta(12, 9)).toEqual({ kind: 'pct', value: (3 / 9) * 100 });
  });

  it('returns a negative percentage when the primary total is lower', () => {
    expect(computeDelta(8, 10)).toEqual({ kind: 'pct', value: -20 });
  });

  it('returns "new" when the comparison total is zero but the primary is positive', () => {
    expect(computeDelta(5, 0)).toEqual({ kind: 'new' });
  });

  it('returns "none" when both totals are zero (never Infinity)', () => {
    expect(computeDelta(0, 0)).toEqual({ kind: 'none' });
  });
});

describe('CompareDelta', () => {
  it('renders the primary total, a signed badge, and "vs previous"', () => {
    renderDelta(12, 9);
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('▲ +33%')).toBeInTheDocument();
    expect(screen.getByText('vs previous')).toBeInTheDocument();
  });

  it('shows a downward badge for a decrease', () => {
    renderDelta(8, 10);
    expect(screen.getByText('▼ −20%')).toBeInTheDocument();
  });

  it('shows "new" instead of a percentage when there was no prior activity', () => {
    renderDelta(5, 0);
    expect(screen.getByText('new')).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it('shows "no change" when both totals are zero', () => {
    renderDelta(0, 0);
    expect(screen.getByText('– no change')).toBeInTheDocument();
  });
});
