const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconPRMerged(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <circle cx="6" cy="5" r="2"/>
      <circle cx="18" cy="19" r="2"/>
      <path d="M6 7v6a4 4 0 0 0 4 4h6"/>
    </svg>
  );
}