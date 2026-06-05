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
import type { TeamMetricPointDto } from '@/types';

interface MultiLineChartProps {
  data: TeamMetricPointDto[];
  height?: number;
  unit?: string;
  ariaLabel?: string;
}

// Semantic violet: first data series — intentional fixed color, not an accent surface
const COLORS = ['#7c3aed', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

export function MultiLineChart({ data, height = 280, unit = '', ariaLabel }: MultiLineChartProps) {
  // Get unique members (excluding team aggregate)
  const members = [...new Set(data.map((d) => d.username).filter(Boolean))];

  // Pivot: date → { member: value }
  const dateMap: Record<string, Record<string, number>> = {};
  for (const d of data) {
    if (!dateMap[d.date]) dateMap[d.date] = {};
    if (d.username) dateMap[d.date][d.username] = d.value;
  }
  const chartData = Object.entries(dateMap)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, vals]) => ({ date: formatDate(date), ...vals }));

  return (
    <figure aria-label={ariaLabel ?? 'Team metric chart'} style={{ margin: 0 }}>
      <figcaption className="sr-only">
        Team metric chart with {members.length} members over {Object.keys(dateMap).length} data points
      </figcaption>
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--line-2)" />
          <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--fg-3)' }} tickLine={false} axisLine={false} />
          <YAxis tick={{ fontSize: 11, fill: 'var(--fg-3)' }} tickLine={false} axisLine={false} unit={unit} width={36} />
          <Tooltip contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--line)', borderRadius: 8, fontSize: 13 }} />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
          {members.map((member, i) => (
            <Line
              key={member as string}
              type="monotone"
              dataKey={member as string}
              stroke={COLORS[i % COLORS.length]}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4 }}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </figure>
  );
}
