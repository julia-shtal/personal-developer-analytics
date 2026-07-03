import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCompareRange } from './useCompareRange';

describe('useCompareRange', () => {
  it('starts disabled', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-31' }));
    expect(result.current.enabled).toBe(false);
  });

  it('defaults compareRange to a same-length window ending the day before primary.from', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-31' }));
    expect(result.current.compareRange).toEqual({ from: '2026-03-31', to: '2026-04-30' });
  });

  it('handles a single-day primary range', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-15', to: '2026-05-15' }));
    expect(result.current.compareRange).toEqual({ from: '2026-05-14', to: '2026-05-14' });
  });

  it('toggle flips enabled from false to true', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-31' }));
    act(() => result.current.toggle());
    expect(result.current.enabled).toBe(true);
  });

  it('toggle flips enabled back to false', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-31' }));
    act(() => result.current.toggle());
    act(() => result.current.toggle());
    expect(result.current.enabled).toBe(false);
  });

  it('recomputes the default compareRange each time toggle turns comparison on', () => {
    const { result, rerender } = renderHook(
      ({ primary }) => useCompareRange(primary),
      { initialProps: { primary: { from: '2026-05-01', to: '2026-05-31' } } },
    );
    act(() => result.current.toggle()); // on, default = Mar 31 - Apr 30
    act(() => result.current.setCompareRange({ from: '2026-01-01', to: '2026-01-31' })); // user overrides
    act(() => result.current.toggle()); // off
    rerender({ primary: { from: '2026-06-01', to: '2026-06-30' } });
    act(() => result.current.toggle()); // on again, for the new primary range
    expect(result.current.compareRange).toEqual({ from: '2026-05-02', to: '2026-05-31' });
  });

  it('setCompareRange overrides the computed default', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-31' }));
    act(() => result.current.setCompareRange({ from: '2026-01-01', to: '2026-01-31' }));
    expect(result.current.compareRange).toEqual({ from: '2026-01-01', to: '2026-01-31' });
  });

  it('exposes windowDays equal to the inclusive primary length', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-10' }));
    expect(result.current.windowDays).toBe(10);
  });

  it('snaps compareTo when the user edits compareFrom, preserving the locked length', () => {
    // primary length = 10 days; default compare = Apr 21 – Apr 30.
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-10' }));
    // User moves the start; the end must follow so the window stays 10 days.
    act(() => result.current.setCompareRange({ from: '2026-03-15', to: '2026-04-30' }));
    expect(result.current.compareRange).toEqual({ from: '2026-03-15', to: '2026-03-24' });
  });

  it('snaps compareFrom when the user edits compareTo, preserving the locked length', () => {
    const { result } = renderHook(() => useCompareRange({ from: '2026-05-01', to: '2026-05-10' }));
    // User moves the end; the start must follow so the window stays 10 days.
    act(() => result.current.setCompareRange({ from: '2026-04-21', to: '2026-05-20' }));
    expect(result.current.compareRange).toEqual({ from: '2026-05-11', to: '2026-05-20' });
  });

  it('resizes the comparison window when the primary range changes, preserving the offset', () => {
    const { result, rerender } = renderHook(
      ({ primary }) => useCompareRange(primary),
      { initialProps: { primary: { from: '2026-06-10', to: '2026-06-19' } } },
    );
    // default = May 31 – Jun 9, offset −10 days from primary.from.
    rerender({ primary: { from: '2026-06-20', to: '2026-06-29' } });
    // Offset preserved: still 10 days ending the day before the new primary.from.
    expect(result.current.compareRange).toEqual({ from: '2026-06-10', to: '2026-06-19' });
  });

  it('falls back to the default prior window when preserving the offset would overlap the primary', () => {
    const { result, rerender } = renderHook(
      ({ primary }) => useCompareRange(primary),
      { initialProps: { primary: { from: '2026-05-01', to: '2026-05-10' } } },
    );
    // Growing the primary to 25 days would push the offset-preserved window
    // into the primary range, so it falls back to the default prior period.
    rerender({ primary: { from: '2026-05-01', to: '2026-05-25' } });
    expect(result.current.compareRange).toEqual({ from: '2026-04-06', to: '2026-04-30' });
  });

  it('does not throw when vi is unused (placeholder guard for lint)', () => {
    expect(vi.isMockFunction).toBeTypeOf('function');
  });
});
