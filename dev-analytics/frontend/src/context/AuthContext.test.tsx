import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { REPO_SCOPE_STORAGE_KEY } from '@/lib/repoScopeSignal';
import { AuthProvider, useAuth } from './AuthContext';

vi.mock('axios', () => ({
  default: { post: vi.fn(() => Promise.reject({ response: { status: 401 } })) },
}));

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn(), get: vi.fn() },
  setAccessToken: vi.fn(),
  clearTokens: vi.fn(),
}));

import api from '@/lib/api';

const mockedApi = api as unknown as { post: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn> };

function Consumer() {
  const { user, login } = useAuth();
  return (
    <div>
      <span data-testid="user">{user?.username ?? 'none'}</span>
      <button onClick={() => login({ usernameOrEmail: 'julia', password: 'pw' })}>log in</button>
    </div>
  );
}

function renderAuth() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AuthProvider><Consumer /></AuthProvider>
    </QueryClientProvider>
  );
}

describe('AuthContext — login', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    mockedApi.post.mockResolvedValue({ data: { accessToken: 'token' } });
    mockedApi.get.mockResolvedValue({ data: { username: 'julia', role: 'DEVELOPER' } });
  });
  afterEach(() => localStorage.clear());

  it('clears a repo scope left behind by the previous account', async () => {
    // The query cache is already cleared on login; the persisted repo scope was not, so a
    // second account in the same browser inherited the first one's selection and every
    // metric request it carried was answered 404.
    const user = userEvent.setup();
    localStorage.setItem(REPO_SCOPE_STORAGE_KEY, '35');
    renderAuth();

    await user.click(screen.getByText('log in'));

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('julia'));
    expect(localStorage.getItem(REPO_SCOPE_STORAGE_KEY)).toBeNull();
  });
});
