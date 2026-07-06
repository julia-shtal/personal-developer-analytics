import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
    memberDailyPrCreated: vi.fn().mockResolvedValue({ data: [] }),
    memberDailyPrMerged: vi.fn().mockResolvedValue({ data: [] }),
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
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ isManager: true, isAdmin: false }),
}));

import { usersApi } from '@/api/users';
import { teamMetricsApi } from '@/api/metrics';

const baseMember: MemberSummaryDto = {
  userId: 7,
  username: 'octocat',
  metrics: {},
  email: 'octocat@example.com',
};

function renderModal(
  member: MemberSummaryDto = baseMember,
  prefs: { defaultContactMethod: 'IN_APP' | 'EMAIL' } = { defaultContactMethod: 'IN_APP' },
  teamMembers: MemberSummaryDto[] = [member],
) {
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
          teamMembers={teamMembers}
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

describe('MemberDetailModal — member comparison (FC-8)', () => {
  const other: MemberSummaryDto = { userId: 9, username: 'hubot', metrics: {}, email: undefined };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hides the "Compare with" selector when the member is the only one in the team', () => {
    renderModal(baseMember, { defaultContactMethod: 'IN_APP' }, [baseMember]);
    expect(screen.queryByLabelText('Compare with')).not.toBeInTheDocument();
  });

  it('lists other team members but not the viewed member in the selector', () => {
    renderModal(baseMember, { defaultContactMethod: 'IN_APP' }, [baseMember, other]);
    const select = screen.getByLabelText('Compare with');
    expect(select).toBeInTheDocument();
    // Options: "None" + the other member; the viewed member is excluded.
    expect(screen.getByRole('option', { name: 'None' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'hubot' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'octocat' })).not.toBeInTheDocument();
  });

  it('does not fetch comparison series until a member is selected, then fetches on selection', async () => {
    renderModal(baseMember, { defaultContactMethod: 'IN_APP' }, [baseMember, other]);

    // Primary series fetched for the viewed member (id 7); none for id 9 yet.
    await waitFor(() => expect(teamMetricsApi.memberDailyCommits).toHaveBeenCalledWith(1, 7, '2026-06-01', '2026-06-08'));
    expect(teamMetricsApi.memberDailyCommits).not.toHaveBeenCalledWith(1, 9, '2026-06-01', '2026-06-08');

    fireEvent.change(screen.getByLabelText('Compare with'), { target: { value: '9' } });

    // Selecting the comparison member triggers its three series fetches.
    await waitFor(() => {
      expect(teamMetricsApi.memberDailyCommits).toHaveBeenCalledWith(1, 9, '2026-06-01', '2026-06-08');
      expect(teamMetricsApi.memberDailyPrCreated).toHaveBeenCalledWith(1, 9, '2026-06-01', '2026-06-08');
      expect(teamMetricsApi.memberDailyPrMerged).toHaveBeenCalledWith(1, 9, '2026-06-01', '2026-06-08');
    });
  });
});
