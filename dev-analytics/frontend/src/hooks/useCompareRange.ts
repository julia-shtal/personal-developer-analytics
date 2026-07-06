import { useCallback, useState } from 'react';
import type { DateRange } from '@/types';
import { addDays, windowLengthDays } from '@/lib/dates';

export interface UseCompareRangeResult {
  enabled: boolean;
  compareRange: DateRange;
  /** Locked comparison length (= primary length), for the picker caption. */
  windowDays: number;
  toggle: () => void;
  setCompareRange: (range: DateRange) => void;
}

const MS_PER_DAY = 86_400_000;

/** Signed day offset from `a` to `b` (b - a). */
function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / MS_PER_DAY);
}

/** Same-length window ending the day before `primary.from`. */
function defaultCompareRange(primary: DateRange): DateRange {
  const len = windowLengthDays(primary);
  const to = addDays(primary.from, -1);
  const from = addDays(to, -(len - 1));
  return { from, to };
}

/**
 * Snap a user-chosen anchor to the locked length. We infer which end the user
 * edited by diffing against the previous value: a changed `from` anchors the
 * start (recompute `to`); a changed `to` anchors the end (recompute `from`).
 */
function snapToLength(prev: DateRange, next: DateRange, len: number): DateRange {
  if (next.from !== prev.from) {
    return { from: next.from, to: addDays(next.from, len - 1) };
  }
  if (next.to !== prev.to) {
    return { from: addDays(next.to, -(len - 1)), to: next.to };
  }
  return prev;
}

/**
 * When the primary range changes, keep the comparison window at the new length
 * while preserving its offset from `primary.from` — but only if that keeps the
 * comparison strictly before the (new) primary window. Otherwise fall back to
 * the default prior-period window.
 */
function resizeToPrimary(current: DateRange, prevPrimary: DateRange, nextPrimary: DateRange): DateRange {
  const len = windowLengthDays(nextPrimary);
  const offset = daysBetween(prevPrimary.from, current.from);
  const candidateFrom = addDays(nextPrimary.from, offset);
  const candidateTo = addDays(candidateFrom, len - 1);
  // ISO date strings sort lexicographically; strict inequality = no overlap.
  if (candidateTo < nextPrimary.from) {
    return { from: candidateFrom, to: candidateTo };
  }
  return defaultCompareRange(nextPrimary);
}

/**
 * Local compare-window state for one chart card. Not persisted — resets on
 * unmount/reload per the FC-6 spec ("toggle state resets on page reload").
 * The comparison length is always locked to the primary length; the user can
 * move where the window sits but not resize it independently.
 */
export function useCompareRange(primary: DateRange): UseCompareRangeResult {
  const [enabled, setEnabled] = useState(false);
  const [compareRange, setCompareRangeState] = useState<DateRange>(() => defaultCompareRange(primary));
  const [prevPrimary, setPrevPrimary] = useState<DateRange>(primary);

  // Derive from props: adjust state during render when the primary range moves
  // (React's recommended pattern for prop-derived state, no effect needed).
  if (primary.from !== prevPrimary.from || primary.to !== prevPrimary.to) {
    setCompareRangeState((cur) => resizeToPrimary(cur, prevPrimary, primary));
    setPrevPrimary(primary);
  }

  const windowDays = windowLengthDays(primary);

  const toggle = useCallback(() => {
    setEnabled((prev) => {
      const next = !prev;
      if (next) setCompareRangeState(defaultCompareRange(primary));
      return next;
    });
  }, [primary]);

  const setCompareRange = useCallback((range: DateRange) => {
    const len = windowLengthDays(primary);
    setCompareRangeState((prev) => snapToLength(prev, range, len));
  }, [primary]);

  return { enabled, compareRange, windowDays, toggle, setCompareRange };
}
