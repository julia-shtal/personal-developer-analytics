import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import axios from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { clearTokens, setAccessToken } from '@/lib/api';
import api from '@/lib/api';
import type { UserProfile, LoginRequest, RegisterRequest, AuthResponse } from '@/types';

interface AuthContextValue {
  user: UserProfile | null;
  isLoading: boolean;
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

  // On mount: attempt a silent refresh via the httpOnly cookie.
  // If the cookie is present and valid, this restores the session without any user action.
  useEffect(() => {
    axios
      .post<AuthResponse>('/api/auth/refresh', null, { withCredentials: true })
      .then(({ data }) => {
        setAccessToken(data.accessToken);
        return api.get<UserProfile>('/users/me');
      })
      .then((r) => setUser(r.data))
      .catch(() => {
        // No valid session — stay unauthenticated.
      })
      .finally(() => setIsLoading(false));
  }, []);

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
    qc.clear();
    setUser(null);
  }

  const isManager = user?.role === 'MANAGER' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, logout, refreshUser, isManager, isAdmin }}>
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
