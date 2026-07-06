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
import type { MetricPointDto, TeamMetricPointDto } from '@/types';

/** One explicitly-styled line, used by the `series` (comparison) mode. */
export interface LineSeriesDef {
  data: MetricPointDto[];
  label: string;
  color: string;
  /** Render as a dashed stroke — used for the comparison member's series. */
  dashed?: boolean;
}

interface MultiLineChartProps {
  /** Team-pivot mode: per-member rows keyed by `username`. */
  data?: TeamMetricPointDto[];
  /**
   * Explicit-series mode (FC-8 member comparison): each entry becomes one line
   * with its own color and stroke style. Takes precedence over `data` when set.
   */
  series?: LineSeriesDef[];
  height?: number;
  unit?: string;
  ariaLabel?: string;
}

// Semantic violet: first data series — intentional fixed color, not an accent surface
const COLORS = ['#7c3aed', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

export function MultiLineChart({ data, series, height = 280, unit = '', ariaLabel }: MultiLineChartProps) {
  if (series && series.length > 0) {
    return (
      <ExplicitSeriesChart series={series} height={height} unit={unit} ariaLabel={ariaLabel} />
    );
  }
  return <TeamPivotChart data={data ?? []} height={height} unit={unit} ariaLabel={ariaLabel} />;
}

/**
 * FC-8 comparison mode: renders one styled line per {@link LineSeriesDef}. Both
 * members share the same date range, so the x-axis is absolute calendar date and
 * the two series align tick-for-tick.
 */
function ExplicitSeriesChart({ series, height, unit, ariaLabel }: {
  series: LineSeriesDef[];
  height: number;
  unit: string;
  ariaLabel?: string;
}) {
  // Union of all dates across series → one row per date with a value per label.
  const byDate: Record<string, Record<string, number>> = {};
  for (const s of series) {
    for (const p of s.data) {
      (byDate[p.date] ??= {})[s.label] = p.value;
    }
  }
  const chartData = Object.entries(byDate)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, vals]) => ({ date: formatDate(date), ...vals }));

  return (
    <figure aria-label={ariaLabel ?? 'Member comparison chart'} style={{ margin: 0 }}>
      <figcaption className="sr-only">
        Comparison chart with {series.length} series over {Object.keys(byDate).length} data points
      </figcaption>
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 4, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--line-2)" />
          <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--fg-3)' }} tickLine={false} axisLine={false} />
          <YAxis tick={{ fontSize: 11, fill: 'var(--fg-3)' }} tickLine={false} axisLine={false} unit={unit} width={36} />
          <Tooltip contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--line)', borderRadius: 8, fontSize: 13 }} />
          <Legend iconType="plainline" iconSize={14} wrapperStyle={{ fontSize: 12 }} />
          {series.map((s) => (
            <Line
              key={s.label}
              type="monotone"
              dataKey={s.label}
              stroke={s.color}
              strokeWidth={2}
              strokeDasharray={s.dashed ? '4 3' : undefined}
              dot={false}
              activeDot={{ r: 4 }}
              connectNulls
              // Recharts hijacks strokeDasharray for its draw-in animation, which
              // clobbers the dashed comparison style; disable animation so the
              // requested stroke pattern renders directly.
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </figure>
  );
}

/** Original team-dashboard mode: pivot per-member rows keyed by `username`. */
function TeamPivotChart({ data, height, unit, ariaLabel }: {
  data: TeamMetricPointDto[];
  height: number;
  unit: string;
  ariaLabel?: string;
}) {
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
