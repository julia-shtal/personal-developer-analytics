import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { RotateCcw, CalendarRange } from 'lucide-react';
import { useDateRange } from '@/context/DateRangeContext';
import { DateRangeModal } from '@/components/DateRangeModal';
import { formatDate } from '@/lib/dates';

const CRUMB_MAP: Record<string, string[]> = {
  '/dashboard':    ['dev/', 'analytics', 'personal'],
  '/team':         ['dev/', 'analytics', 'team'],
  '/team-manage':  ['dev/', 'analytics', 'manage-teams'],
  '/datasources':  ['dev/', 'analytics', 'sources'],
  '/settings':     ['dev/', 'analytics', 'settings'],
  '/admin':        ['dev/', 'analytics', 'admin'],
};

export function TopBar() {
  const { pathname } = useLocation();
  const { range, setRange } = useDateRange();
  const [rangeOpen, setRangeOpen] = useState(false);

  const crumbs = CRUMB_MAP[pathname] ?? ['dev/', 'analytics', pathname.replace(/^\//, '') || 'dashboard'];

  function handleRecalculate() {
    console.log('[TopBar] recalculate dispatched');
    window.dispatchEvent(new CustomEvent('da:recalculate'));
  }

  const periodLabel = `${formatDate(range.from)} – ${formatDate(range.to)}`;

  return (
    <>
      <header style={{
        borderBottom: '1px solid var(--line)',
        background: 'var(--bg)',
        padding: '0 32px',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        position: 'sticky',
        top: 0,
        zIndex: 5,
        minHeight: 56,
        flexShrink: 0,
      }}>
        {/* Breadcrumbs */}
        <div style={{
          flex: 1, minWidth: 0,
          display: 'flex', alignItems: 'center',
          gap: 0, flexWrap: 'nowrap', overflow: 'hidden',
        }}>
          {crumbs.map((c, i) => (
            <span key={i} style={{ display: 'flex', alignItems: 'center' }}>
              {i > 0 && (
                <span className="tick" style={{ color: 'var(--line)', margin: '0 6px' }}>/</span>
              )}
              <span style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: i === crumbs.length - 1 ? 'var(--fg)' : 'var(--fg-3)',
                fontWeight: i === crumbs.length - 1 ? 500 : 400,
                whiteSpace: 'nowrap',
              }}>
                {c}
              </span>
            </span>
          ))}
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
          <button
            className="btn btn-sm"
            onClick={() => setRangeOpen(true)}
            aria-label="Select date range"
          >
            <CalendarRange width={13} height={13} />
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>{periodLabel}</span>
          </button>
          <button
            className="btn btn-sm btn-icon"
            onClick={handleRecalculate}
            title="Recalculate metrics"
            aria-label="Recalculate metrics"
          >
            <RotateCcw width={13} height={13} />
          </button>
        </div>
      </header>

      <DateRangeModal
        open={rangeOpen}
        onClose={() => setRangeOpen(false)}
        value={range}
        onChange={setRange}
      />
    </>
  );
}