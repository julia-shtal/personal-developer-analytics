const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconRefactor(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M4 9a8 8 0 0 1 13-3l3 3"/>
      <path d="M20 4v5h-5"/>
      <path d="M20 15a8 8 0 0 1-13 3l-3-3"/>
      <path d="M4 20v-5h5"/>
    </svg>
  );
}