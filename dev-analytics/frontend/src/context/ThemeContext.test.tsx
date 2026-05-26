import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { ThemeProvider, useTheme } from './ThemeContext';
import { DEFAULT_THEME_STATE } from '@/lib/theme';
import type { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.cssText = '';
  });

  it('yields default state on first load', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe(DEFAULT_THEME_STATE.theme);
    expect(result.current.accent).toBe(DEFAULT_THEME_STATE.accent);
    expect(result.current.logo).toBe(DEFAULT_THEME_STATE.logo);
  });

  it('setTheme({ theme: "dark" }) updates dataset.theme and localStorage', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    act(() => { result.current.setTheme({ theme: 'dark' }); });
    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    const stored = JSON.parse(localStorage.getItem('da-theme-v1') || '{}');
    expect(stored.theme).toBe('dark');
  });

  it('setTheme({ accent }) updates CSS variable', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    act(() => { result.current.setTheme({ accent: '#c98931' }); });
    expect(result.current.accent).toBe('#c98931');
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#c98931');
  });
});