import { ACCENT_SWATCHES } from './accentSwatches.constants';

export { ACCENT_SWATCHES };

interface AccentSwatchesProps {
  value: string;
  onChange: (hex: string) => void;
  size?: number;
}

function norm(s: string) { return (s || '').toLowerCase(); }

export function AccentSwatches({ value, onChange, size = 26 }: AccentSwatchesProps) {
  const isPreset = ACCENT_SWATCHES.some((s) => norm(s.hex) === norm(value));

  return (
    <div className="row gap-2" style={{ flexWrap: 'wrap' }}>
      {ACCENT_SWATCHES.map((s) => {
        const active = norm(s.hex) === norm(value);
        return (
          <button
            key={s.hex}
            onClick={() => onChange(s.hex)}
            title={`${s.name} · ${s.hex}`}
            aria-label={s.name}
            style={{
              width: size, height: size, borderRadius: '50%',
              background: s.hex,
              border: '2px solid ' + (active ? 'var(--fg)' : 'transparent'),
              outline: '1px solid var(--line)',
              outlineOffset: active ? -3 : -1,
              cursor: 'pointer', padding: 0,
              transition: 'transform .12s',
              transform: active ? 'scale(1.05)' : 'none',
            }}
          />
        );
      })}

      {/* Custom hex */}
      <label
        title="Pick a custom hex color"
        aria-label="Custom hex color"
        style={{
          position: 'relative',
          width: size, height: size, borderRadius: '50%',
          border: '2px solid ' + (!isPreset ? 'var(--fg)' : 'transparent'),
          outline: '1px dashed var(--line)',
          outlineOffset: !isPreset ? -3 : -1,
          cursor: 'pointer',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          background: !isPreset
            ? value
            : 'conic-gradient(from 0deg, #d96650, #c98931, #3a9a73, #3d9cc4, #7a5ae0, #d96650)',
          overflow: 'hidden',
          transition: 'transform .12s',
          transform: !isPreset ? 'scale(1.05)' : 'none',
        }}
      >
        <input
          type="color"
          value={isPreset ? '#7a5ae0' : value}
          onChange={(e) => onChange(e.target.value)}
          style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', border: 0, padding: 0 }}
        />
        {isPreset && (
          <span
            style={{
              width: 9, height: 9, borderRadius: '50%',
              background: 'var(--bg-card)',
              border: '1px solid var(--fg-3)',
              position: 'relative', zIndex: 0,
            }}
          />
        )}
      </label>
    </div>
  );
}
