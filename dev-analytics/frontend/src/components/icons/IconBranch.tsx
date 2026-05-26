const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconBranch(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M5 4h11a3 3 0 0 1 3 3v14H8a3 3 0 0 1-3-3z"/>
      <path d="M5 17h11a3 3 0 0 1 3 3"/>
      <path d="M9 4v9l2-1.5L13 13V4"/>
    </svg>
  );
}