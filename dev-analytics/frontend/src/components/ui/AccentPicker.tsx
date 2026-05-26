import { useState } from 'react';
import { AccentSwatches } from './AccentSwatches';
import { ACCENT_SWATCHES } from './accentSwatches.constants';
import { useTheme } from '@/context/ThemeContext';

function norm(s: string) { return (s || '').toLowerCase(); }

interface AccentPickerProps {
  value: string;
  onChange: (hex: string) => void;
}

export function AccentPicker({ value, onChange }: AccentPickerProps) {
  const [open, setOpen] = useState(false);
  const [hex, setHex] = useState(value);

  const matched = ACCENT_SWATCHES.find((s) => norm(s.hex) === norm(value));
  const label = matched ? matched.name : 'custom';

  function handleToggle() {
    if (!open) setHex(value);
    setOpen((v) => !v);
  }

  function commitHex(v: string) {
    setHex(v);
    if (/^#[0-9a-fA-F]{6}$/.test(v)) onChange(v.toLowerCase());
  }

  return (
    <div style={{ position: 'relative' }}>
      <button className="btn btn-sm" onClick={handleToggle} title="Change accent color">
        <span
          style={{
            width: 12, height: 12, borderRadius: '50%',
            background: value,
            border: '1px solid color-mix(in oklab, var(--fg) 20%, transparent)',
            display: 'inline-block',
          }}
        />
        <span style={{ color: 'var(--fg-3)' }}>accent</span>
        <span style={{ fontWeight: 500, textTransform: 'capitalize' }}>{label}</span>
      </button>

      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 40 }} />
          <div
            style={{
              position: 'absolute', top: '100%', right: 0, marginTop: 4,
              background: 'var(--bg-card)', border: '1px solid var(--line)',
              borderRadius: 8, padding: 14, minWidth: 260, zIndex: 41,
              boxShadow: '0 12px 32px rgba(0,0,0,.12)',
            }}
          >
            <div className="t-eyebrow" style={{ marginBottom: 10 }}>── accent color</div>
            <AccentSwatches value={value} onChange={onChange} size={28} />
            <div className="t-eyebrow" style={{ marginTop: 14, marginBottom: 6 }}>── custom hex</div>
            <div className="row gap-2">
              <input
                className="input"
                value={hex}
                onChange={(e) => commitHex(e.target.value)}
                spellCheck={false}
                style={{ fontFamily: 'var(--font-mono)', fontSize: 12, padding: '6px 10px', textTransform: 'lowercase' }}
              />
              <span
                style={{
                  width: 28, height: 28, borderRadius: 6,
                  background: value, flexShrink: 0,
                  border: '1px solid var(--line)',
                }}
              />
            </div>
            <div className="t-label" style={{ marginTop: 8, fontSize: 10.5 }}>
              applies to highlights, focus rings &amp; selection
            </div>
          </div>
        </>
      )}
    </div>
  );
}

export function AccentPickerConnected() {
  const { accent, setTheme } = useTheme();
  return <AccentPicker value={accent} onChange={(hex) => setTheme({ accent: hex })} />;
}