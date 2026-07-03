import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import { formatDate } from '@/lib/dates';
import type { MetricPointDto } from '@/types';

const BAR_MAX_SIZE = 28;
/** Comparison bars stay visually secondary via reduced fill opacity. */
const COMPARE_BAR_OPACITY = 0.4;

interface MetricBarChartProps {
  data: MetricPointDto[];
  color?: string;
  label?: string;
  unit?: string;
  height?: number;
  ariaLabel?: string;
  compareData?: MetricPointDto[];
  compareColor?: string;
  primaryRangeLabel?: string;
  compareRangeLabel?: string;
}

export function MetricBarChart({
  data,
  color = '#7c3aed',
  label = 'Value',
  unit = '',
  height = 240,
  ariaLabel,
  compareData,
  compareColor = 'var(--fg-3)',
  primaryRangeLabel,
  compareRangeLabel,
}: MetricBarChartProps) {
  const isComparing = !!compareData && compareData.length > 0;
  const sortedPrimary = [...data].sort((a, b) => a.date.localeCompare(b.date));
  const total = data.reduce((s, d) => s + d.value, 0);

  const tooltipStyle = {
    background: 'var(--bg-card)',
    border: '1px solid var(--line)',
    borderRadius: 8,
    fontSize: 13,
    color: 'var(--fg)',
  };
  const axisTick = { fontSize: 11, fill: 'var(--fg-3)' };

  if (!isComparing) {
    const chartData = sortedPrimary.map((d) => ({ date: formatDate(d.date), value: d.value }));
    return (
      <figure aria-label={ariaLabel ?? `${label} chart`} style={{ margin: 0 }}>
        <figcaption className="sr-only">
          {label}: {total.toFixed(1)}{unit} over {data.length} data points
        </figcaption>
        <ResponsiveContainer width="100%" height={height}>
          <BarChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--line-2)" vertical={false} />
            <XAxis dataKey="date" tick={axisTick} tickLine={false} axisLine={false} />
            <YAxis tick={axisTick} tickLine={false} axisLine={false} unit={unit} width={36} />
            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'var(--bg-2)' }} formatter={(val) => [`${val}${unit}`, label]} />
            <Bar dataKey="value" name={label} fill={color} radius={[4, 4, 0, 0]} maxBarSize={32} />
          </BarChart>
        </ResponsiveContainer>
      </figure>
    );
  }

  const sortedCompare = [...compareData!].sort((a, b) => a.date.localeCompare(b.date));
  const maxLen = Math.max(sortedPrimary.length, sortedCompare.length);
  const chartData = Array.from({ length: maxLen }, (_, i) => ({
    idx: i + 1,
    primaryValue: sortedPrimary[i]?.value,
    compareValue: sortedCompare[i]?.value,
  }));

  return (
    <figure aria-label={ariaLabel ?? `${label} chart`} style={{ margin: 0 }}>
      <figcaption className="sr-only">
        {label}: {total.toFixed(1)}{unit} over {data.length} data points, compared against {compareData!.length} prior data points
      </figcaption>
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--line-2)" vertical={false} />
          <XAxis dataKey="idx" tick={axisTick} tickLine={false} axisLine={false} />
          <YAxis tick={axisTick} tickLine={false} axisLine={false} unit={unit} width={36} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'var(--bg-2)' }} formatter={(val, name) => [`${val}${unit}`, name]} />
          <Legend iconType="square" wrapperStyle={{ fontSize: 11 }} />
          <Bar
            dataKey="primaryValue"
            name={`This period — ${primaryRangeLabel ?? label}`}
            fill={color}
            radius={[4, 4, 0, 0]}
            maxBarSize={BAR_MAX_SIZE}
          />
          <Bar
            dataKey="compareValue"
            name={`Previous period — ${compareRangeLabel ?? label}`}
            fill={compareColor}
            fillOpacity={COMPARE_BAR_OPACITY}
            radius={[4, 4, 0, 0]}
            maxBarSize={BAR_MAX_SIZE}
          />
        </BarChart>
      </ResponsiveContainer>
      <p className="t-label" style={{ marginTop: 4, fontSize: 10, color: 'var(--fg-3)' }}>
        x-axis: day of period
      </p>
    </figure>
  );
}
