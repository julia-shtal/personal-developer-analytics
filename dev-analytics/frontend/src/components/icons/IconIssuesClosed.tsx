const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconIssuesClosed(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <rect x="4" y="4" width="16" height="16" rx="3"/>
      <path d="M8 12l3 3 5-6"/>
    </svg>
  );
}