const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconFirstCommit(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <circle cx="5" cy="12" r="2" fill="currentColor"/>
      <path d="M7 12h10"/>
      <path d="M14 8l4 4-4 4"/>
    </svg>
  );
}