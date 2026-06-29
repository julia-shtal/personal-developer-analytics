import { describe, it, expect, vi, afterEach } from 'vitest';
import { timeAgo } from './dates';

afterEach(() => vi.useRealTimers());

describe('timeAgo()', () => {
  it('returns "just now" for less than 60 seconds', () => {
    const now = new Date();
    expect(timeAgo(now)).toBe('just now');
  });

  it('returns minutes for less than 60 minutes', () => {
    vi.useFakeTimers();
    const base = new Date(2026, 0, 1, 12, 0, 0);
    vi.setSystemTime(new Date(2026, 0, 1, 12, 45, 0));
    expect(timeAgo(base)).toBe('45 min ago');
  });

  it('returns hours for 1–23 hours', () => {
    vi.useFakeTimers();
    const base = new Date(2026, 0, 1, 12, 0, 0);
    vi.setSystemTime(new Date(2026, 0, 1, 23, 0, 0));
    expect(timeAgo(base)).toBe('11h ago');
  });

  it('returns "1 day ago" for exactly 24 hours', () => {
    vi.useFakeTimers();
    const base = new Date(2026, 0, 1, 12, 0, 0);
    vi.setSystemTime(new Date(2026, 0, 2, 12, 0, 0));
    expect(timeAgo(base)).toBe('1 day ago');
  });

  it('returns "3 days ago" for 72 hours', () => {
    vi.useFakeTimers();
    const base = new Date(2026, 0, 1, 12, 0, 0);
    vi.setSystemTime(new Date(2026, 0, 4, 12, 0, 0));
    expect(timeAgo(base)).toBe('3 days ago');
  });
});
