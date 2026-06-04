import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { clearTokens, setAccessToken } from '@/lib/api';
import api from '@/lib/api';
import type { UserProfile, LoginRequest, RegisterRequest, AuthResponse } from '@/types';

const IDLE_TIMEOUT_MS = 15 * 60 * 1000; // mirrors backend access-token TTL
// Persists last user-activity time across browser restarts so the idle check
// survives tab close + reopen (setTimeout alone does not).
const LAST_ACTIVITY_KEY = 'last_activity_ts';
const OFFLINE_RETRY_MS = 5_000;

interface AuthContextValue {
  user: UserProfile | null;
  isLoading: boolean;
  /** True while the bootstrap refresh failed with a network error and is retrying. */
  isOffline: boolean;
  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  isManager: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isOffline, setIsOffline] = useState(false);

  // Idle session timeout — logs out the user after IDLE_TIMEOUT_MS of inactivity.
  // Does not POST /auth/logout (token may already be stale); mirrors the logout state only.
  useEffect(() => {
    if (!user) return;

    let timer: ReturnType<typeof setTimeout>;

    function reset() {
      clearTimeout(timer);
      localStorage.setItem(LAST_ACTIVITY_KEY, Date.now().toString());
      timer = setTimeout(() => {
        // last_activity_ts intentionally not cleared here — it records the last
        // real activity (now IDLE_TIMEOUT_MS old), so the bootstrap check on the
        // next page load will see a stale timestamp and block session restoration.
        clearTokens();
        qc.clear();
        setUser(null);
      }, IDLE_TIMEOUT_MS);
    }

    const events = ['mousemove', 'keydown', 'pointerdown', 'scroll', 'touchstart'] as const;
    events.forEach((e) => window.addEventListener(e, reset, { passive: true }));
    reset();

    return () => {
      clearTimeout(timer);
      events.forEach((e) => window.removeEventListener(e, reset));
    };
  }, [user, qc]);

  // On mount: attempt a silent refresh via the httpOnly cookie.
  // Distinguishes two failure modes:
  //   - auth failure (err.response set, e.g. 401): session invalid → show login page
  //   - network error (!err.response): backend temporarily down → keep spinner, retry
  // This prevents the login page from appearing just because the backend is restarting.
  useEffect(() => {
    const raw = localStorage.getItem(LAST_ACTIVITY_KEY);
    const lastActivityMs = raw ? Number(raw) : NaN;
    if (Number.isFinite(lastActivityMs) && Date.now() - lastActivityMs >= IDLE_TIMEOUT_MS) {
      setIsLoading(false);
      return;
    }

    let networkError = false;
    axios
      .post<AuthResponse>('/api/auth/refresh', null, { withCredentials: true })
      .then(({ data }) => {
        setAccessToken(data.accessToken);
        return api.get<UserProfile>('/users/me');
      })
      .then((r) => setUser(r.data))
      .catch((err) => {
        if (!err.response) {
          networkError = true;
          setIsOffline(true); // triggers retry effect below
          // isLoading intentionally stays true: spinner shows instead of login redirect
        }
        // err.response present: real auth failure — fall through to finally → show login
      })
      .finally(() => {
        if (!networkError) setIsLoading(false);
      });
  }, []);

  // Retry loop: fires whenever isOffline becomes true, polls until the backend responds.
  // On success: restores session in place, no page reload needed.
  // On real auth error: clears offline state so login page is shown.
  useEffect(() => {
    if (!isOffline) return;

    const interval = setInterval(() => {
      axios
        .post<AuthResponse>('/api/auth/refresh', null, { withCredentials: true })
        .then(({ data }) => {
          setIsOffline(false);
          setAccessToken(data.accessToken);
          return api.get<UserProfile>('/users/me');
        })
        .then((r) => {
          setUser(r.data);
          setIsLoading(false);
        })
        .catch((err) => {
          if (err.response) {
            // Backend is reachable but session is no longer valid
            setIsOffline(false);
            setIsLoading(false);
          }
          // Still a network error — keep retrying
        });
    }, OFFLINE_RETRY_MS);

    return () => clearInterval(interval);
  }, [isOffline]);

  async function login(req: LoginRequest) {
    const { data } = await api.post<AuthResponse>('/auth/login', req);
    if (!data.accessToken) {
      throw new Error('No access token in response');
    }
    // Clear any cached data from a previous user before activating the new session.
    qc.clear();
    setAccessToken(data.accessToken);
    // refresh_token is in the httpOnly cookie — no localStorage write needed.
    try {
      const profile = await api.get<UserProfile>('/users/me');
      setUser(profile.data);
    } catch (profileErr) {
      console.error('[AuthContext] /users/me failed after login:', profileErr);
      throw profileErr;
    }
  }

  async function register(req: RegisterRequest) {
    await api.post('/auth/register', req);
  }

  async function refreshUser() {
    const data = await api.get<UserProfile>('/users/me');
    setUser(data.data);
  }

  async function logout() {
    // POST /auth/logout — backend revokes tokens (JWT identifies the user) and clears the cookie.
    await api.post('/auth/logout').catch(() => {});
    clearTokens();
    localStorage.removeItem(LAST_ACTIVITY_KEY);
    setIsOffline(false); // stop any in-progress retry loop
    qc.clear();
    setUser(null);
  }

  const isManager = user?.role === 'MANAGER' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider value={{ user, isLoading, isOffline, login, register, logout, refreshUser, isManager, isAdmin }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
