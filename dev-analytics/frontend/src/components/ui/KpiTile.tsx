import type { ReactNode } from 'react';

type AccentColor = 'violet' | 'cyan' | 'amber' | 'emerald' | 'coral';

interface KpiTileProps {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon?: ReactNode;
  accent?: AccentColor;
  size?: 'md' | 'lg';
  emphasis?: boolean;
}

export function KpiTile({
  label,
  value,
  sub,
  icon,
  accent = 'violet',
  size = 'md',
  emphasis = false,
}: KpiTileProps) {
  return (
    <div
      style={{
        padding: size === 'lg' ? '22px 24px' : '18px 20px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        minHeight: size === 'lg' ? 130 : 100,
      }}
    >
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div className="t-eyebrow">{label}</div>
        {icon && (
          <span style={{ color: `var(--${accent})`, opacity: 0.9 }}>{icon}</span>
        )}
      </div>
      <div
        className={emphasis || size === 'lg' ? 't-number-hero' : 't-number-big'}
        style={{ color: 'var(--fg)', lineHeight: 1 }}
      >
        {value}
      </div>
      {sub && <div className="t-label" style={{ marginTop: -4 }}>{sub}</div>}
    </div>
  );
}
