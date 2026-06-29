import {
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  Radar,
  Legend,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import type { MemberSummaryDto, MetricType } from '@/types';

// Reuses the same semantic color sequence as MultiLineChart.
const COLORS = ['#7c3aed', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

const RADAR_METRICS: MetricType[] = [
  'DAILY_COMMITS_COUNT',
  'DAILY_CHURN_RATIO',
  'PR_LEAD_TIME_HOURS_MEDIAN',
  'MERGE_WITHOUT_REVIEW_RATIO',
  'FOCUS_RATIO_DAYS_TASKS',
  'REVIEW_RESPONSE_TIME_HOURS_MEDIAN',
];

/** Divergent metrics: lower raw value = better performance → invert normalization so 1.0 = best. */
const DIVERGENT: Set<MetricType> = new Set([
  'DAILY_CHURN_RATIO',
  'PR_LEAD_TIME_HOURS_MEDIAN',
  'MERGE_WITHOUT_REVIEW_RATIO',
  'REVIEW_RESPONSE_TIME_HOURS_MEDIAN',
]);

const LABEL: Record<MetricType, string> = {
  DAILY_COMMITS_COUNT: 'Daily Commits',
  DAILY_CHURN_RATIO: 'Churn Ratio',
  PR_LEAD_TIME_HOURS_MEDIAN: 'PR Lead Time',
  MERGE_WITHOUT_REVIEW_RATIO: 'Merge w/o Review',
  FOCUS_RATIO_DAYS_TASKS: 'Focus Ratio',
  REVIEW_RESPONSE_TIME_HOURS_MEDIAN: 'Review Response',
  DAILY_PR_CREATED: '',
  DAILY_PR_MERGED: '',
  DAILY_ISSUES_CLOSED: '',
  DAILY_ISSUES_CREATED: '',
  PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: '',
  ISSUE_LEAD_TIME_HOURS_MEDIAN: '',
  AFTER_HOURS_COMMIT_RATIO: '',
  DEEP_WORK_STREAK_DAYS: '',
  KNOWLEDGE_SILO_SCORE: '',
  REFACTOR_RATIO: '',
  PR_SIZE_COMPLEXITY_SCORE: '',
  MERGE_TO_MAIN_FREQUENCY_PER_WEEK: '',
};

/**
 * Normalizes the 6 radar metrics for each member to 0–1 within the team's own range.
 * Divergent metrics (lower = better) are inverted so 1.0 always means best.
 * Edge cases:
 *   - min === max (all members identical): everyone receives 0.5.
 *   - metric absent for all members: everyone receives 0.
 */
export function normalize(
  members: MemberSummaryDto[],
): Array<{ username: string; [key: string]: number | string }> {
  const minMax: Record<string, { min: number; max: number; allMissing: boolean }> = {};

  for (const metric of RADAR_METRICS) {
    const values = members
      .map((m) => m.metrics[metric])
      .filter((v): v is number => v != null && v > 0);

    if (values.length === 0) {
      minMax[metric] = { min: 0, max: 0, allMissing: true };
    } else {
      minMax[metric] = {
        min: Math.min(...values),
        max: Math.max(...values),
        allMissing: false,
      };
    }
  }

  return members.map((m) => {
    const point: { username: string; [key: string]: number | string } = { username: m.username };
    for (const metric of RADAR_METRICS) {
      const { min, max, allMissing } = minMax[metric];
      const rawValue = m.metrics[metric];
      const hasData = rawValue != null && rawValue > 0;

      let normalized: number;
      if (allMissing || !hasData) {
        normalized = 0;
      } else if (min === max) {
        normalized = 0.5;
      } else {
        normalized = (rawValue - min) / (max - min);
        if (DIVERGENT.has(metric)) {
          normalized = 1 - normalized;
        }
      }
      point[LABEL[metric]] = Math.round(normalized * 100) / 100;
    }
    return point;
  });
}

interface TeamHealthRadarChartProps {
  members: MemberSummaryDto[];
  height?: number;
}

export function TeamHealthRadarChart({ members, height = 340 }: TeamHealthRadarChartProps) {
  if (members.length === 0) return null;

  const normalized = normalize(members);

  const radarData = RADAR_METRICS.map((metric) => {
    const label = LABEL[metric];
    const point: Record<string, string | number> = { metric: label };
    for (const memberPoint of normalized) {
      point[memberPoint.username as string] = (memberPoint[label] as number) ?? 0;
    }
    return point;
  });

  return (
    <figure aria-label="Team health radar chart" style={{ margin: 0 }}>
      <figcaption className="sr-only">
        Radar chart showing {members.length} team members across {RADAR_METRICS.length} normalized metrics
      </figcaption>
      <ResponsiveContainer width="100%" height={height}>
        <RadarChart data={radarData} margin={{ top: 16, right: 32, bottom: 16, left: 32 }}>
          <PolarGrid stroke="var(--line-2)" />
          <PolarAngleAxis
            dataKey="metric"
            tick={{ fontSize: 11, fill: 'var(--fg-3)', fontFamily: 'var(--font-mono)' }}
          />
          <Tooltip
            contentStyle={{
              background: 'var(--bg-card)',
              border: '1px solid var(--line)',
              borderRadius: 8,
              fontSize: 12,
            }}
            formatter={(value: number) => [`${(value * 100).toFixed(0)}%`, '']}
          />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
          {members.map((m, i) => (
            <Radar
              key={m.userId}
              name={m.username}
              dataKey={m.username}
              stroke={COLORS[i % COLORS.length]}
              fill={COLORS[i % COLORS.length]}
              fillOpacity={0.12}
              strokeWidth={2}
            />
          ))}
        </RadarChart>
      </ResponsiveContainer>
    </figure>
  );
}
