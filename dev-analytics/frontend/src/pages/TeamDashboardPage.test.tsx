import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { MemberSummaryDto } from '@/types';
import { MemberDetailModal } from './TeamDashboardPage';

vi.mock('@/api/users', () => ({
  usersApi: {
    notifications: {
      get: vi.fn(),
    },
  },
}));
vi.mock('@/api/metrics', () => ({
  teamMetricsApi: {
    memberDailyCommits: vi.fn().mockResolvedValue({ data: [] }),
  },
}));
vi.mock('@/api/ai', () => ({
  aiApi: {
    generateMemberSummary: vi.fn(),
  },
}));
vi.mock('@/context/DateRangeContext', () => ({
  useDateRange: () => ({ range: { from: '2026-06-01', to: '2026-06-08' }, setRange: vi.fn() }),
}));

import { usersApi } from '@/api/users';

const baseMember: MemberSummaryDto = {
  userId: 7,
  username: 'octocat',
  metrics: {},
  email: 'octocat@example.com',
};

function renderModal(member: MemberSummaryDto = baseMember, prefs: { defaultContactMethod: 'IN_APP' | 'EMAIL' } = { defaultContactMethod: 'IN_APP' }) {
  (usersApi.notifications.get as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
    data: { aiBrief: false, syncFailures: false, afterHours: false, newTeamMember: false, defaultContactMethod: prefs.defaultContactMethod },
  });

  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <MemberDetailModal
          member={member}
          teamId={1}
          open={true}
          onClose={() => {}}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('MemberDetailModal contact buttons', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders both Message and Email buttons when the member has an email', async () => {
    renderModal();
    expect(await screen.findByRole('button', { name: /message octocat/i })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /email octocat/i })).toBeInTheDocument();
  });

  it('hides the Email button when the member has no email', async () => {
    renderModal({ ...baseMember, email: undefined });
    expect(await screen.findByRole('button', { name: /message octocat/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /email octocat/i })).not.toBeInTheDocument();
  });

  it('renders Message as primary when the preference is IN_APP', async () => {
    renderModal(baseMember, { defaultContactMethod: 'IN_APP' });
    await waitFor(() => {
      const messageBtn = screen.getByRole('button', { name: /message octocat/i });
      expect(messageBtn.className).toMatch(/btn-accent/);
    });
  });

  it('renders Email as primary when the preference is EMAIL', async () => {
    renderModal(baseMember, { defaultContactMethod: 'EMAIL' });
    await waitFor(() => {
      const emailLink = screen.getByRole('link', { name: /email octocat/i });
      expect(emailLink.className).toMatch(/btn-accent/);
    });
  });
});
