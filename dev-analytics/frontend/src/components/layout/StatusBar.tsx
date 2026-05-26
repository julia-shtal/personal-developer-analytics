import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { datasourcesApi } from '@/api/datasources';
import { useTheme } from '@/context/ThemeContext';
import { APP_VERSION } from '@/config/branding';
import type { DataSourceConfig } from '@/types';

function timeSince(iso: string | undefined): string | null {
  if (!iso) return null;
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60_000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function StatusBar() {
  const { showStatusBar } = useTheme();
  const { pathname } = useLocation();
  const [isOnline, setIsOnline] = useState(navigator.onLine);

  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => setIsOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  const { data: sources } = useQuery({
    queryKey: ['datasources'],
    queryFn: () => datasourcesApi.list().then((r) => r.data),
    staleTime: 1000 * 60 * 2,
  });

  if (!showStatusBar) return null;

  const sourceCount = sources?.length ?? 0;
  const lastSync = sources
    ?.map((s: DataSourceConfig) => s.lastSuccessSync)
    .filter((t): t is string => Boolean(t))
    .sort()
    .at(-1);
  const lastSyncLabel = timeSince(lastSync) ?? 'never';
  const view = pathname.replace(/^\//, '') || 'dashboard';
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <div className="statusbar">
      <span>
        <span
          className={isOnline ? 'dot dot-live' : 'dot dot-fail'}
          style={{ marginRight: 6, verticalAlign: 'middle' }}
        />
        {isOnline ? 'online' : 'offline'}
      </span>
      <span className="sep">│</span>
      <span>{sourceCount} source{sourceCount !== 1 ? 's' : ''} connected</span>
      <span className="sep">│</span>
      <span>last sync {lastSyncLabel}</span>
      <span className="sep">│</span>
      <span>recalc avail</span>
      <span style={{ flex: 1 }} />
      <span>view: {view}</span>
      <span className="sep">│</span>
      <span>tz: {tz}</span>
      <span className="sep">│</span>
      <span>build {APP_VERSION}</span>
    </div>
  );
}
