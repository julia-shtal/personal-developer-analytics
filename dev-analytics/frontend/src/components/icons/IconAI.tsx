const BASE = {
  width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'currentColor', strokeWidth: 1.6,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const,
};

export default function IconAI(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg {...BASE} {...props}>
      <path d="M12 3a5 5 0 0 0-5 5v1a4 4 0 0 0-1 7v3a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-3a4 4 0 0 0-1-7V8a5 5 0 0 0-5-5z"/>
      <path d="M12 11l.7 1.6 1.8.4-1.4 1.2.3 1.8L12 15.2l-1.4.8.3-1.8L9.5 13l1.8-.4z" fill="currentColor" stroke="none"/>
    </svg>
  );
}