const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconDeepWork(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M4 21V13"/>
      <path d="M9 21V9"/>
      <path d="M14 21V6"/>
      <path d="M19 21V3"/>
    </svg>
  );
}