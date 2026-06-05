import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider, useTheme } from '@/context/ThemeContext';
import { AuthProvider } from '@/context/AuthContext';
import { DateRangeProvider } from '@/context/DateRangeContext';
import { RepoScopeProvider } from '@/context/RepoScopeContext';
import { AppShell } from '@/components/layout/AppShell';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { DataSourcesPage } from '@/pages/DataSourcesPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { TeamDashboardPage } from '@/pages/TeamDashboardPage';
import { TeamManagePage } from '@/pages/TeamManagePage';
import { AdminPage } from '@/pages/AdminPage';
import { WelcomePage } from '@/pages/WelcomePage';
import { MessagesPage } from '@/pages/MessagesPage';
import { ErrorBoundary } from '@/components/ErrorBoundary';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 2,
      retry: 1,
    },
  },
});

function FaviconSync() {
  const { logo } = useTheme();
  useEffect(() => {
    const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (link) link.href = `/favicons/${logo}.svg`;
  }, [logo]);
  return null;
}

function AppRoutes() {
  return (
    <>
      <FaviconSync />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/welcome" element={<WelcomePage />} />

        <Route element={<DateRangeProvider><RepoScopeProvider><AppShell /></RepoScopeProvider></DateRangeProvider>}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/team" element={<TeamDashboardPage />} />
          <Route path="/team-manage" element={<TeamManagePage />} />
          <Route path="/datasources" element={<DataSourcesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            <BrowserRouter>
              <AppRoutes />
            </BrowserRouter>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}