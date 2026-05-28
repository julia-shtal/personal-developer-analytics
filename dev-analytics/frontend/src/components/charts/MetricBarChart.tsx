import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { formatDate } from '@/lib/dates';
import type { MetricPointDto } from '@/types';

interface MetricBarChartProps {
  data: MetricPointDto[];
  color?: string;
  label?: string;
  unit?: string;
  height?: number;
}

export function MetricBarChart({
  data,
  color = '#7c3aed',
  label = 'Value',
  unit = '',
  height = 240,
}: MetricBarChartProps) {
  const chartData = [...data]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((d) => ({ date: formatDate(d.date), value: d.value }));

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--line-2)" vertical={false} />
        <XAxis
          dataKey="date"
          tick={{ fontSize: 11, fill: 'var(--fg-3)' }}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          tick={{ fontSize: 11, fill: 'var(--fg-3)' }}
          tickLine={false}
          axisLine={false}
          unit={unit}
          width={36}
        />
        <Tooltip
          contentStyle={{
            background: 'var(--bg-card)',
            border: '1px solid var(--line)',
            borderRadius: 8,
            fontSize: 13,
            color: 'var(--fg)',
          }}
          cursor={{ fill: 'var(--bg-2)' }}
          formatter={(val) => [`${val}${unit}`, label]}
        />
        <Bar dataKey="value" name={label} fill={color} radius={[4, 4, 0, 0]} maxBarSize={32} />
      </BarChart>
    </ResponsiveContainer>
  );
}
