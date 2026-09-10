import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { Team } from '@/types';

vi.mock('@/api/teams', () => ({
  teamsApi: {
    list: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
  },
}));
vi.mock('@/lib/api', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: [] }) },
}));
vi.mock('@/context/DateRangeContext', () => ({
  useDateRange: () => ({ range: { from: '2026-06-01', to: '2026-06-08' }, setRange: vi.fn() }),
}));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ isManager: true, isAdmin: false }),
}));

import { teamsApi } from '@/api/teams';
import { TeamManagePage } from './TeamManagePage';

const mocked = (fn: unknown) => fn as unknown as ReturnType<typeof vi.fn>;

const platform: Team = { id: 1, name: 'platform', members: [] } as unknown as Team;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TeamManagePage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mocked(teamsApi.list).mockResolvedValue({ data: [platform] });
});

describe('TeamManagePage — modal text fields keep focus while typing', () => {
  it('createTeam_typingName_retainsFocusAndFullValue', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Create new team' }));

    const name = await screen.findByLabelText('Team name');
    await user.type(name, 'infrastructure');

    expect((name as HTMLInputElement).value).toBe('infrastructure');
    expect(document.activeElement).toBe(name);
  });

  it('createTeam_typingDescription_retainsFocusAndFullValue', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Create new team' }));

    const desc = await screen.findByLabelText('Team description (optional)');
    await user.type(desc, 'owns the build pipeline');

    expect((desc as HTMLInputElement).value).toBe('owns the build pipeline');
    expect(document.activeElement).toBe(desc);
  });

  it('deleteTeam_typingConfirmationName_retainsFocusAndEnablesDelete', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('button', { name: 'Delete team permanently' }));

    const confirm = await screen.findByLabelText('Type team name to confirm deletion');
    await user.type(confirm, 'platform');

    expect((confirm as HTMLInputElement).value).toBe('platform');
    expect(document.activeElement).toBe(confirm);
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Confirm delete' })).not.toBeDisabled()
    );
  });

  it('addMember_typingSearch_retainsFocusAndFullValue', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Manage members of platform' }));

    const search = await screen.findByLabelText('Search users to add');
    await user.type(search, 'octocat');

    expect((search as HTMLInputElement).value).toBe('octocat');
    expect(document.activeElement).toBe(search);
  });

  it('configTeam_typingName_retainsFocusAndFullValue', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Configure platform' }));

    const name = await screen.findByLabelText('Team name');
    await user.clear(name);
    await user.type(name, 'platform-core');

    expect((name as HTMLInputElement).value).toBe('platform-core');
    expect(document.activeElement).toBe(name);
  });
});
