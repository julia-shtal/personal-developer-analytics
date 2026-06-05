import { useState } from 'react';
import { Calendar, ChevronDown } from 'lucide-react';
import { PRESET_RANGES } from '@/lib/dates';
import type { DateRange } from '@/types';
import { clsx } from 'clsx';

interface DateRangePickerProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
}

export function DateRangePicker({ value, onChange }: DateRangePickerProps) {
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState(false);

  const activePreset = PRESET_RANGES.find(
    (p) => p.range.from === value.from && p.range.to === value.to
  );

  function selectPreset(range: DateRange) {
    onChange(range);
    setCustom(false);
    setOpen(false);
  }

  return (
    <div className="relative inline-block">
      <button
        onClick={() => setOpen((v) => !v)}
        className={clsx(
          'inline-flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-lg border',
          'bg-white text-gray-700 border-gray-300 hover:bg-gray-50 shadow-sm',
          'focus:outline-none focus:ring-2 focus:ring-[var(--accent)]',
          'transition-colors duration-150'
        )}
      >
        <Calendar className="h-4 w-4 text-gray-400" />
        <span>{activePreset?.label ?? `${value.from} → ${value.to}`}</span>
        <ChevronDown className={clsx('h-4 w-4 text-gray-400 transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1 w-64 bg-white rounded-xl border border-gray-200 shadow-lg overflow-hidden">
            <div className="p-1">
              {PRESET_RANGES.map((p) => (
                <button
                  key={p.label}
                  onClick={() => selectPreset(p.range)}
                  className={clsx(
                    'w-full text-left px-3 py-2 text-sm rounded-lg transition-colors',
                    p.range.from === value.from && p.range.to === value.to
                      ? 'bg-[var(--accent-bg)] text-[var(--accent-strong)] font-medium'
                      : 'text-gray-700 hover:bg-gray-50'
                  )}
                >
                  {p.label}
                </button>
              ))}
              <button
                onClick={() => { setCustom(true); }}
                className={clsx(
                  'w-full text-left px-3 py-2 text-sm rounded-lg transition-colors',
                  custom ? 'bg-[var(--accent-bg)] text-[var(--accent-strong)] font-medium' : 'text-gray-700 hover:bg-gray-50'
                )}
              >
                Custom range…
              </button>
            </div>

            {custom && (
              <div className="border-t border-gray-100 p-3 flex flex-col gap-2">
                <div>
                  <label className="text-xs text-gray-500 font-medium">From</label>
                  <input
                    type="date"
                    value={value.from}
                    max={value.to}
                    onChange={(e) => onChange({ ...value, from: e.target.value })}
                    className="mt-1 block w-full px-2 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
                  />
                </div>
                <div>
                  <label className="text-xs text-gray-500 font-medium">To</label>
                  <input
                    type="date"
                    value={value.to}
                    min={value.from}
                    onChange={(e) => onChange({ ...value, to: e.target.value })}
                    className="mt-1 block w-full px-2 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[var(--accent)]"
                  />
                </div>
                <button
                  onClick={() => setOpen(false)}
                  className="mt-1 w-full py-1.5 text-sm font-medium bg-[var(--accent)] text-white rounded-lg hover:brightness-90"
                >
                  Apply
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
