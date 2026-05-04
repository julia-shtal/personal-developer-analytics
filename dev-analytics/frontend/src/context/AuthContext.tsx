import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { clearTokens } from '@/lib/api';
import api from '@/lib/api';
import type { UserProfile, LoginRequest, RegisterRequest, AuthResponse } from '@/types';

interface AuthContextValue {
  user: UserProfile | null;
  isLoading: boolean;
  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  isManager: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      setIsLoading(false);
      return;
    }
    api.get<UserProfile>('/users/me')
      .then((r) => setUser(r.data))
      .catch(() => clearTokens())
      .finally(() => setIsLoading(false));
  }, []);

  async function login(req: LoginRequest) {
    const { data } = await api.post<AuthResponse>('/auth/login', req);
    if (!data.accessToken) {
      throw new Error('No access token in response');
    }
    // Clear any cached data from a previous user before setting the new tokens.
    qc.clear();
    localStorage.setItem('access_token', data.accessToken);
    localStorage.setItem('refresh_token', data.refreshToken);
    try {
      const profile = await api.get<UserProfile>('/users/me');
      setUser(profile.data);
    } catch (profileErr) {
      // Login succeeded but profile fetch failed — still navigate, log the error
      console.error('[AuthContext] /users/me failed after login:', profileErr);
      throw profileErr;
    }
  }

  async function register(req: RegisterRequest) {
    await api.post('/auth/register', req);
  }

  async function logout() {
    const refreshToken = localStorage.getItem('refresh_token');
    if (refreshToken) {
      await api.post('/auth/logout', { refreshToken }).catch(() => {});
    }
    clearTokens();
    qc.clear();
    setUser(null);
  }

  const isManager = user?.role === 'MANAGER' || user?.role === 'ADMIN';
  const isAdmin = user?.role === 'ADMIN';

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, logout, isManager, isAdmin }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
