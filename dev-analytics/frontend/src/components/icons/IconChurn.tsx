const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconChurn(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M4 8h11"/>
      <path d="M12 5l3 3-3 3"/>
      <path d="M20 16H9"/>
      <path d="M12 13l-3 3 3 3"/>
    </svg>
  );
}