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
