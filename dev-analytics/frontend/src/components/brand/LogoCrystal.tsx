interface LogoProps {
  size?: number;
  withWordmark?: boolean;
}

export function LogoCrystal({ size = 28, withWordmark = false }: LogoProps) {
  return (
    <span className="row gap-2" style={{ lineHeight: 0 }}>
      <svg width={size} height={size} viewBox="0 0 32 32" fill="none">
        <circle cx="16" cy="16" r="15.5" fill="var(--bg-2)" stroke="var(--line)" />
        <text
          x="9" y="25"
          fontFamily="var(--font-serif)"
          fontSize="24"
          fontStyle="italic"
          fontWeight="400"
          fill="currentColor"
        >
          D
        </text>
        <circle cx="23" cy="10" r="1.8" fill="var(--accent)" />
      </svg>
      {withWordmark && (
        <span
          style={{
            fontFamily: 'var(--font-serif)',
            fontSize: size * 0.62,
            fontWeight: 400,
            letterSpacing: '-0.015em',
            color: 'var(--fg)',
            lineHeight: 1,
          }}
        >
          <em style={{ color: 'var(--accent)' }}>Dev</em> Analytics
        </span>
      )}
    </span>
  );
}