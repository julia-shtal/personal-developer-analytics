interface LogoProps {
  size?: number;
  withWordmark?: boolean;
}

export function LogoSlash({ size = 28, withWordmark = false }: LogoProps) {
  return (
    <span className="row gap-2" style={{ lineHeight: 0 }}>
      <svg width={size} height={size} viewBox="0 0 32 32" fill="none">
        <rect
          x="0.5" y="0.5" width="31" height="31" rx="8"
          fill="var(--bg-card)" stroke="currentColor" strokeWidth="1.5"
        />
        <g fontFamily="var(--font-mono)" fontWeight="600" fill="currentColor">
          <text x="6"  y="22" fontSize="13" letterSpacing="-1">~/</text>
          <text x="17.5" y="22" fontSize="13" fill="var(--accent)">d</text>
        </g>
        <rect x="25" y="13" width="2.5" height="10" fill="var(--accent)">
          <animate attributeName="opacity" values="1;0;1" dur="1s" repeatCount="indefinite" />
        </rect>
      </svg>
      {withWordmark && (
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: size * 0.44,
            fontWeight: 500,
            letterSpacing: '-0.04em',
            color: 'var(--fg)',
          }}
        >
          dev<span style={{ color: 'var(--fg-3)' }}>/</span>analytics
        </span>
      )}
    </span>
  );
}