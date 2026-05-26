const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconCommits(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <circle cx="6" cy="6" r="2.4"/>
      <circle cx="6" cy="12" r="2.4"/>
      <circle cx="6" cy="18" r="2.4"/>
      <path d="M6 8.4v1.2M6 14.4v1.2"/>
      <path d="M8.5 6h10M8.5 12h7M8.5 18h12"/>
    </svg>
  );
}