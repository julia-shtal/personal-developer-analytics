import { GitCompare } from 'lucide-react';
import { clsx } from 'clsx';
import type { DateRange } from '@/types';

interface CompareToggleProps {
  enabled: boolean;
  onToggle: () => void;
  compareRange: DateRange;
  onCompareRangeChange: (range: DateRange) => void;
  /** Locked comparison length; when set, renders the "locked to N days" caption. */
  windowDays?: number;
}

export function CompareToggle({ enabled, onToggle, compareRange, onCompareRangeChange, windowDays }: CompareToggleProps) {
  return (
    <div className="col" style={{ gap: 4, alignItems: 'flex-end' }}>
      <div className="row gap-2" style={{ alignItems: 'center' }}>
        <button
          type="button"
          className={clsx('btn btn-sm', enabled && 'btn-accent')}
          aria-pressed={enabled}
          onClick={onToggle}
        >
          <GitCompare width={12} height={12} />
          Compare
        </button>
        {enabled && (
          <div className="row gap-1" style={{ alignItems: 'center' }}>
            {/* No min/max: the window length is locked, so either end can move
                freely and the hook snaps the opposite end. */}
            <input
              type="date"
              className="input"
              aria-label="Comparison range start"
              value={compareRange.from}
              onChange={(e) => onCompareRangeChange({ ...compareRange, from: e.target.value })}
              style={{ width: 132, padding: '4px 8px', fontSize: 11 }}
            />
            <span className="t-label" style={{ color: 'var(--fg-3)' }}>→</span>
            <input
              type="date"
              className="input"
              aria-label="Comparison range end"
              value={compareRange.to}
              onChange={(e) => onCompareRangeChange({ ...compareRange, to: e.target.value })}
              style={{ width: 132, padding: '4px 8px', fontSize: 11 }}
            />
          </div>
        )}
      </div>
      {enabled && windowDays != null && (
        <span className="t-label" style={{ fontSize: 10, color: 'var(--fg-3)' }}>
          Comparison window locked to {windowDays} {windowDays === 1 ? 'day' : 'days'}
        </span>
      )}
    </div>
  );
}
