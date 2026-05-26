interface DataPoint {
  value: number;
  date?: string;
}

interface SparklineProps {
  data: DataPoint[];
  color?: string;
  height?: number;
  width?: number;
  area?: boolean;
  responsive?: boolean;
}

export function Sparkline({
  data,
  color = 'var(--accent)',
  height = 40,
  width = 120,
  area = true,
  responsive = false,
}: SparklineProps) {
  if (!data || data.length === 0) return null;

  const vals = data.map((d) => d.value);
  const max = Math.max(...vals, 1);
  const min = Math.min(...vals, 0);
  const range = max - min || 1;
  const step = width / (data.length - 1 || 1);

  const points = data.map((d, i) => {
    const x = i * step;
    const y = height - ((d.value - min) / range) * (height - 4) - 2;
    return [x, y] as [number, number];
  });

  const path = points
    .map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`)
    .join(' ');
  const areaPath = `${path} L${width},${height} L0,${height} Z`;

  return (
    <svg
      width={responsive ? '100%' : width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      style={{ display: 'block' }}
    >
      {area && <path d={areaPath} fill={color} opacity="0.12" />}
      <path
        d={path}
        stroke={color}
        strokeWidth="1.5"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
