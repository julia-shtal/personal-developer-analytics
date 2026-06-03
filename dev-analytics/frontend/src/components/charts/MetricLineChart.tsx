import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import { formatDate } from '@/lib/dates';
import type { MetricPointDto } from '@/types';

interface MetricLineChartProps {
  data: MetricPointDto[];
  color?: string;
  label?: string;
  unit?: string;
  height?: number;
  ariaLabel?: string;
}

export function MetricLineChart({
  data,
  color = '#7c3aed',
  label = 'Value',
  unit = '',
  height = 240,
  ariaLabel,
}: MetricLineChartProps) {
  const chartData = [...data]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((d) => ({ date: formatDate(d.date), value: d.value }));

  const total = data.reduce((sum, d) => sum + d.value, 0);

  return (
    <figure aria-label={ariaLabel ?? `${label} chart`} style={{ margin: 0 }}>
      <figcaption className="sr-only">
        {label}: {total.toFixed(1)}{unit} over {data.length} data points
      </figcaption>
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            tick={{ fontSize: 11, fill: '#9ca3af' }}
            tickLine={false}
            axisLine={false}
            unit={unit}
            width={36}
          />
          <Tooltip
            contentStyle={{ border: '1px solid #e5e7eb', borderRadius: 8, fontSize: 13 }}
            cursor={{ stroke: color, strokeWidth: 1, strokeDasharray: '4 4' }}
            formatter={(val) => [`${val}${unit}`, label]}
          />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
          <Line
            type="monotone"
            dataKey="value"
            name={label}
            stroke={color}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, fill: color }}
          />
        </LineChart>
      </ResponsiveContainer>
    </figure>
  );
}
