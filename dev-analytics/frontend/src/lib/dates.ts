import type { DateRange } from '@/types';

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
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
  { label: 'Last 7 days', range: { from: daysAgo(7), to: today() } },
  { label: 'Last 30 days', range: { from: daysAgo(30), to: today() } },
  { label: 'Last 90 days', range: { from: daysAgo(90), to: today() } },
];
