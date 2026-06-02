import type { ReactNode } from 'react';
import { Tooltip } from './Tooltip';

type AccentColor = 'violet' | 'cyan' | 'amber' | 'emerald' | 'coral';

interface KpiTileProps {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon?: ReactNode;
  tooltip?: string;
  accent?: AccentColor;
  size?: 'md' | 'lg';
  emphasis?: boolean;
  anomaly?: boolean;
}

export function KpiTile({
  label,
  value,
  sub,
  icon,
  tooltip,
  accent = 'violet',
  size = 'md',
  emphasis = false,
  anomaly = false,
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
        <div className="row" style={{ alignItems: 'center', gap: 6 }}>
          <div className="t-eyebrow">{label}</div>
          {anomaly && (
            <Tooltip content="Anomaly detected — this metric deviates significantly from its recent pattern">
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: 'var(--amber)',
                  display: 'inline-block',
                  flexShrink: 0,
                  cursor: 'default',
                }}
              />
            </Tooltip>
          )}
        </div>
        {icon && tooltip ? (
          <Tooltip content={tooltip}>
            <span style={{ color: `var(--${accent})`, opacity: 0.9, cursor: 'default' }}>{icon}</span>
          </Tooltip>
        ) : icon ? (
          <span style={{ color: `var(--${accent})`, opacity: 0.9 }}>{icon}</span>
        ) : null}
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
