import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { LoginPage } from './LoginPage';

const mockLogin = vi.fn();

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin }),
}));

vi.mock('@/context/ThemeContext', () => ({
  useTheme: () => ({ logo: 'slash' }),
}));

vi.mock('@/components/brand/Logo', () => ({
  Logo: () => <span data-testid="logo" />,
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/welcome" element={<div>welcome page</div>} />
        <Route path="/forgot-password" element={<div>forgot page</div>} />
        <Route path="/register" element={<div>register page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders the sign-in form with username, password, and submit button', () => {
    renderPage();
    expect(screen.getByText(/welcome back/i)).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('navigates to /welcome after a successful login', async () => {
    mockLogin.mockResolvedValue(undefined);
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.type(screen.getByRole('textbox'), 'julia@example.com');
    await user.type(container.querySelector('input[type="password"]')!, 'secret');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByText('welcome page')).toBeInTheDocument(),
    );
    expect(mockLogin).toHaveBeenCalledWith({
      usernameOrEmail: 'julia@example.com',
      password: 'secret',
    });
  });

  it('shows an error message on 401', async () => {
    mockLogin.mockRejectedValue({ response: { status: 401 } });
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.type(screen.getByRole('textbox'), 'bad@user.com');
    await user.type(container.querySelector('input[type="password"]')!, 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(
        screen.getByText(/invalid username\/email or password/i),
      ).toBeInTheDocument(),
    );
  });

  it('shows a server-down message when no response is received', async () => {
    mockLogin.mockRejectedValue(new Error('Network Error'));
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.type(screen.getByRole('textbox'), 'julia@example.com');
    await user.type(container.querySelector('input[type="password"]')!, 'secret');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByText(/could not reach the server/i)).toBeInTheDocument(),
    );
  });
});
