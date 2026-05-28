import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { type ThemeState, DEFAULT_THEME_STATE } from '@/lib/theme';
import { ACTIVE_LOGO } from '@/config/branding';

const LS_KEY = 'da-theme-v1';

function loadFromStorage(): ThemeState {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (raw) return { ...DEFAULT_THEME_STATE, logo: ACTIVE_LOGO, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return { ...DEFAULT_THEME_STATE, logo: ACTIVE_LOGO };
}

function applyToDOM(state: ThemeState) {
  const root = document.documentElement;
  root.dataset.theme = state.theme;
  const isDark = state.theme === 'dark';
  const accent = state.accent || '#7a5ae0';
  root.style.setProperty('--accent', accent);
  root.style.setProperty(
    '--accent-strong',
    isDark
      ? `color-mix(in oklab, ${accent} 72%, #fff)`
      : `color-mix(in oklab, ${accent} 72%, #000)`
  );
  root.style.setProperty(
    '--accent-bg',
    isDark
      ? `color-mix(in oklab, ${accent} 26%, var(--bg))`
      : `color-mix(in oklab, ${accent} 12%, var(--bg))`
  );
}

interface ThemeContextValue extends ThemeState {
  setTheme: (patch: Partial<ThemeState>) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<ThemeState>(loadFromStorage);

  useEffect(() => {
    applyToDOM(state);
  }, [state]);

  const setTheme = useCallback((patch: Partial<ThemeState>) => {
    setState((prev) => {
      const next = { ...prev, ...patch };
      try {
        localStorage.setItem(LS_KEY, JSON.stringify(next));
      } catch { /* ignore */ }
      return next;
    });
  }, []);

  return (
    <ThemeContext.Provider value={{ ...state, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}