import { useState, useEffect } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { StatusBar } from './StatusBar';
import { useAuth } from '@/context/AuthContext';
import { PageSpinner } from '@/components/ui/Spinner';

export function AppShell() {
  const { user, isLoading } = useAuth();
  const [paletteToast, setPaletteToast] = useState(false);

  useEffect(() => {
    let t: ReturnType<typeof setTimeout>;
    function handlePalette() {
      setPaletteToast(true);
      clearTimeout(t);
      t = setTimeout(() => setPaletteToast(false), 2000);
    }
    window.addEventListener('da:open-palette', handlePalette);
    return () => {
      window.removeEventListener('da:open-palette', handlePalette);
      clearTimeout(t);
    };
  }, []);

  if (isLoading) return <PageSpinner />;
  if (!user) return <Navigate to="/login" replace />;

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden', background: 'var(--bg)' }}>
      <Sidebar />
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <TopBar />
        <main style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
          <Outlet />
        </main>
        <StatusBar />
      </div>

      {paletteToast && (
        <div style={{
          position: 'fixed',
          bottom: 48,
          left: '50%',
          transform: 'translateX(-50%)',
          background: 'var(--bg-2)',
          border: '1px solid var(--line)',
          borderRadius: 8,
          padding: '8px 16px',
          fontSize: 13,
          fontFamily: 'var(--font-sans)',
          color: 'var(--fg)',
          zIndex: 100,
          boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
        }}>
          Command palette coming soon
        </div>
      )}
    </div>
  );
}