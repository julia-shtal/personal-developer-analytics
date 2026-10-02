import type { MetricType } from '@/types';
import { METRIC_QUALIFIERS } from '@/config/metricQualifiers';
import { Chip } from './Chip';
import { Tooltip } from './Tooltip';

interface DoraProxyBadgeProps {
  metricKey: MetricType;
}

/** Visible "DORA proxy" qualifier for metrics that approximate a DORA metric (R-F-08). */
export function DoraProxyBadge({ metricKey }: DoraProxyBadgeProps) {
  const qualifier = METRIC_QUALIFIERS[metricKey];
  if (!qualifier) return null;

  return (
    <Tooltip content={qualifier.note}>
      <Chip color="amber">DORA proxy</Chip>
    </Tooltip>
  );
}
