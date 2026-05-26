const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconIssueLead(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M6 3h12"/>
      <path d="M6 21h12"/>
      <path d="M7 3v3l5 6-5 6v3"/>
      <path d="M17 3v3l-5 6 5 6v3"/>
    </svg>
  );
}