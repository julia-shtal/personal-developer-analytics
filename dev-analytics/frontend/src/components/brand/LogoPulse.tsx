interface LogoProps {
  size?: number;
  withWordmark?: boolean;
}

export function LogoPulse({ size = 28, withWordmark = false }: LogoProps) {
  return (
    <span className="row gap-2" style={{ lineHeight: 0 }}>
      <svg width={size} height={size} viewBox="0 0 32 32" fill="none">
        <rect x="0.5" y="0.5" width="31" height="31" rx="6.5" fill="var(--accent)" />
        <g fill="white">
          <rect x="7"  y="20" width="3" height="6"  rx="1" />
          <rect x="12" y="14" width="3" height="12" rx="1" />
          <rect x="17" y="9"  width="3" height="17" rx="1" />
          <rect x="22" y="16" width="3" height="10" rx="1" />
        </g>
        <circle cx="23.5" cy="9" r="2" fill="white" />
      </svg>
      {withWordmark && (
        <span
          style={{
            fontFamily: 'var(--font-sans)',
            fontSize: size * 0.5,
            fontWeight: 600,
            letterSpacing: '-0.03em',
            color: 'var(--fg)',
          }}
        >
          Dev Analytics
        </span>
      )}
    </span>
  );
}