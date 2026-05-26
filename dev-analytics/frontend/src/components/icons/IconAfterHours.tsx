const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconAfterHours(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M3 19h18"/>
      <path d="M16 14a5 5 0 1 1-5-7 4 4 0 0 0 5 7z"/>
    </svg>
  );
}