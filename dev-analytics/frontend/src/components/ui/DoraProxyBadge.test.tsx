import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DoraProxyBadge } from './DoraProxyBadge';

describe('DoraProxyBadge', () => {
  it('renders nothing for a metric with no DORA qualifier', () => {
    render(<DoraProxyBadge metricKey="DAILY_COMMITS_COUNT" />);
    expect(screen.queryByText('DORA proxy')).not.toBeInTheDocument();
  });

  it('shows the "DORA proxy" label without hovering', () => {
    render(<DoraProxyBadge metricKey="PR_LEAD_TIME_HOURS_MEDIAN" />);
    expect(screen.getByText('DORA proxy')).toBeInTheDocument();
  });

  it('reveals the proxy caveat on hover', async () => {
    render(<DoraProxyBadge metricKey="PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN" />);

    await userEvent.hover(screen.getByText('DORA proxy'));

    expect(await screen.findByText(/not from commit to production deploy/)).toBeInTheDocument();
  });
});
