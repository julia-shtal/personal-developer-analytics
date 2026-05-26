interface LogoProps {
  size?: number;
  withWordmark?: boolean;
}

export function LogoBracket({ size = 28, withWordmark = false }: LogoProps) {
  return (
    <span className="row gap-2" style={{ lineHeight: 0 }}>
      <svg width={size} height={size} viewBox="0 0 32 32" fill="none">
        <rect x="0.5" y="0.5" width="31" height="31" rx="6.5" fill="currentColor" />
        <g stroke="var(--bg-card)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" fill="none">
          <path d="M10 8 H7 V24 H10" />
          <path d="M22 8 H25 V24 H22" />
        </g>
        <rect x="14" y="14" width="4" height="6" fill="var(--bg-card)" />
      </svg>
      {withWordmark && (
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: size * 0.46,
            fontWeight: 600,
            letterSpacing: '-0.04em',
            color: 'var(--fg)',
          }}
        >
          dev<span style={{ color: 'var(--accent)' }}>·</span>analytics
        </span>
      )}
    </span>
  );
}