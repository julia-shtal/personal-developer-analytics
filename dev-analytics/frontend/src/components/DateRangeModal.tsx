import { useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { PRESET_RANGES, today } from '@/lib/dates';
import type { DateRange } from '@/types';

interface DateRangeModalProps {
  open: boolean;
  onClose: () => void;
  value: DateRange;
  onChange: (range: DateRange) => void;
}

export function DateRangeModal({ open, onClose, value, onChange }: DateRangeModalProps) {
  const [from, setFrom] = useState(value.from);
  const [to, setTo] = useState(value.to);

  function selectPreset(range: DateRange) {
    onChange(range);
    onClose();
  }

  function applyCustom() {
    if (from && to && from <= to) {
      onChange({ from, to });
      onClose();
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Date range" eyebrow="── select period" width={460}>
      {/* Presets — 3-column grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 20 }}>
        {PRESET_RANGES.map((p) => (
          <button
            key={p.label}
            className="btn btn-sm"
            style={
              value.from === p.range.from && value.to === p.range.to
                ? { background: 'var(--accent-bg)', borderColor: 'var(--accent)', color: 'var(--accent)' }
                : {}
            }
            onClick={() => selectPreset(p.range)}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* Custom range */}
      <div className="t-eyebrow" style={{ marginBottom: 8 }}>── custom range</div>
      <div className="row gap-2" style={{ alignItems: 'flex-end' }}>
        <div className="col gap-1" style={{ flex: 1 }}>
          <label className="t-label">From</label>
          <input
            type="date"
            className="input"
            value={from}
            max={to || today()}
            onChange={(e) => setFrom(e.target.value)}
          />
        </div>
        <div className="col gap-1" style={{ flex: 1 }}>
          <label className="t-label">To</label>
          <input
            type="date"
            className="input"
            value={to}
            min={from}
            max={today()}
            onChange={(e) => setTo(e.target.value)}
          />
        </div>
        <button className="btn btn-accent" onClick={applyCustom} disabled={!from || !to || from > to}>
          Apply
        </button>
      </div>
    </Modal>
  );
}
