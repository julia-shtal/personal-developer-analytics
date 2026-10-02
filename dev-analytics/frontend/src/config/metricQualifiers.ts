import type { MetricType } from '@/types';

export interface MetricQualifier {
  dora: string;
  kind: 'proxy';
  note: string;
}

/**
 * Metrics that approximate, but do not satisfy, a DORA metric definition (R-F-08).
 * Keyed by `MetricType` so the UI can look up whether a figure needs the
 * "DORA proxy" qualifier without re-deriving the attribution rules inline.
 */
export const METRIC_QUALIFIERS: Partial<Record<MetricType, MetricQualifier>> = {
  PR_LEAD_TIME_HOURS_MEDIAN: {
    dora: 'Lead time for changes',
    kind: 'proxy',
    note: 'Proxy for DORA lead time for changes: measured from pull-request open (or first commit) to merge, not from commit to production deploy.',
  },
  PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: {
    dora: 'Lead time for changes',
    kind: 'proxy',
    note: 'Proxy for DORA lead time for changes: measured from pull-request open (or first commit) to merge, not from commit to production deploy.',
  },
  MERGES_TO_DEFAULT_BRANCH_PER_WEEK: {
    dora: 'Deployment frequency',
    kind: 'proxy',
    note: 'Proxy for DORA deployment frequency: counts pull requests merged into the default branch, not actual deployments — this platform has no CI/CD pipeline integration.',
  },
};
