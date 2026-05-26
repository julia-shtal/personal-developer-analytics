const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconReview(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M4 5h16v11H9l-5 4z"/>
      <path d="M8 10l2.5 2.5L16 7"/>
    </svg>
  );
}