// Comparison series stays visually secondary and shares one dash convention
// with every other compare card. The sparkline is straight-segment SVG (no
// spline smoothing) by construction.
const STROKE_WIDTH = '1.5';
const COMPARE_DASH = '4 3';
const AREA_OPACITY = '0.12';

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
  ariaLabel?: string;
  compareData?: DataPoint[];
  compareColor?: string;
}

export function Sparkline({
  data,
  color = 'var(--accent)',
  height = 40,
  width = 120,
  area = true,
  responsive = false,
  ariaLabel,
  compareData,
  compareColor = 'var(--fg-3)',
}: SparklineProps) {
  if (!data || data.length === 0) return null;

  const isComparing = !!compareData && compareData.length > 0;
  const allVals = isComparing
    ? [...data.map((d) => d.value), ...compareData!.map((d) => d.value)]
    : data.map((d) => d.value);
  const max = Math.max(...allVals, 1);
  const min = Math.min(...allVals, 0);
  const range = max - min || 1;

  function toPath(points: DataPoint[]): string {
    const step = width / (points.length - 1 || 1);
    return points
      .map((d, i) => {
        const x = i * step;
        const y = height - ((d.value - min) / range) * (height - 4) - 2;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }

  const path = toPath(data);
  const areaPath = `${path} L${width},${height} L0,${height} Z`;
  const comparePath = isComparing ? toPath(compareData!) : null;

  return (
    <figure aria-label={ariaLabel ?? 'Sparkline chart'} style={{ margin: 0 }}>
      <figcaption className="sr-only">
        Sparkline: {data.length} data points
        {isComparing ? `, compared against ${compareData!.length} prior data points` : ''}
      </figcaption>
      <svg
        width={responsive ? '100%' : width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        style={{ display: 'block' }}
      >
        {area && <path d={areaPath} fill={color} opacity={AREA_OPACITY} />}
        {comparePath && (
          <path
            d={comparePath}
            stroke={compareColor}
            strokeWidth={STROKE_WIDTH}
            strokeDasharray={COMPARE_DASH}
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
          />
        )}
        <path
          d={path}
          stroke={color}
          strokeWidth={STROKE_WIDTH}
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
    </figure>
  );
}
