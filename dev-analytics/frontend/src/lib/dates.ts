import type { DateRange } from '@/types';

export function formatDate(iso: string): string {
  const d = new Date(iso);
  const month = d.toLocaleDateString('en-US', { month: 'short' });
  const day = d.getDate();
  const year = d.getFullYear().toString().slice(-2);
  return `${month} ${day} '${year}`;
}

export function isoDate(d: Date): string {
  return d.toISOString().split('T')[0];
}

const MS_PER_DAY = 86_400_000;

/** Shift a single ISO date by `days` (can be negative). UTC-based, DST-safe. */
export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return isoDate(d);
}

/** Inclusive day count of a range. Jun 26–Jul 3 → 8. */
export function windowLengthDays(range: DateRange): number {
  const from = Date.parse(`${range.from}T00:00:00Z`);
  const to = Date.parse(`${range.to}T00:00:00Z`);
  return Math.round((to - from) / MS_PER_DAY) + 1;
}

/** Shift both ends of a range by `days` (can be negative). Length preserved. */
export function shiftWindow(range: DateRange, days: number): DateRange {
  return { from: addDays(range.from, days), to: addDays(range.to, days) };
}

export function today(): string {
  return isoDate(new Date());
}

export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return isoDate(d);
}

export const PRESET_RANGES: { label: string; range: DateRange }[] = [
  { label: 'Today',         range: { from: today(),      to: today() } },
  { label: 'Yesterday',     range: { from: daysAgo(1),   to: daysAgo(1) } },
  { label: 'Last 7 days',   range: { from: daysAgo(7),   to: today() } },
  { label: 'Last 14 days',  range: { from: daysAgo(14),  to: today() } },
  { label: '4 weeks',       range: { from: daysAgo(28),  to: today() } },
  { label: '8 weeks',       range: { from: daysAgo(56),  to: today() } },
  { label: 'Last quarter',  range: { from: daysAgo(91),  to: today() } },
  { label: 'Year to date',  range: { from: startOfYear(), to: today() } },
];

export function startOfYear(): string {
  const d = new Date();
  d.setMonth(0, 1);
  return isoDate(d);
}

export function timeAgo(date: Date): string {
  const diffMs = Date.now() - date.getTime();
  if (!isFinite(diffMs) || diffMs < 60_000) return 'just now';
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h ago`;
  const diffD = Math.floor(diffH / 24);
  return `${diffD} ${diffD === 1 ? 'day' : 'days'} ago`;
}
