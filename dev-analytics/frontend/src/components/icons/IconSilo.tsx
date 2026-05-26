const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconSilo(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <rect x="6" y="3" width="12" height="18" rx="2"/>
      <circle cx="12" cy="9" r="2"/>
      <path d="M8 17c.6-2 2-3 4-3s3.4 1 4 3"/>
    </svg>
  );
}