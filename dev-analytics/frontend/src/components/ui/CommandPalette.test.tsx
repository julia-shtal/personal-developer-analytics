import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CommandPalette } from './CommandPalette';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ isManager: true, isAdmin: true }),
}));

vi.mock('@/context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark', setTheme: vi.fn() }),
}));

function setup(props: Partial<React.ComponentProps<typeof CommandPalette>> = {}) {
  return render(
    <MemoryRouter>
      <CommandPalette open onClose={vi.fn()} {...props} />
    </MemoryRouter>
  );
}

describe('CommandPalette', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it('renders all 8 commands for an admin user', () => {
    setup();
    expect(screen.getByText('Go to Dashboard')).toBeTruthy();
    expect(screen.getByText('Go to Team')).toBeTruthy();
    expect(screen.getByText('Go to Manage Teams')).toBeTruthy();
    expect(screen.getByText('Go to Data Sources')).toBeTruthy();
    expect(screen.getByText('Go to Settings')).toBeTruthy();
    expect(screen.getByText('Go to Admin')).toBeTruthy();
    expect(screen.getByText('Toggle dark mode')).toBeTruthy();
    expect(screen.getByText('Open date range picker')).toBeTruthy();
  });

  it('typing "dash" filters to one result', () => {
    setup();
    const input = screen.getByPlaceholderText('Type a command…');
    fireEvent.change(input, { target: { value: 'dash' } });
    expect(screen.getByText('Go to Dashboard')).toBeTruthy();
    expect(screen.queryByText('Go to Team')).toBeNull();
    expect(screen.queryByText('Go to Settings')).toBeNull();
  });

  it('pressing Enter fires the selected command action', () => {
    setup();
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });

  it('Escape closes without navigating', () => {
    const onClose = vi.fn();
    setup({ onClose });
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('ArrowDown moves selection; Enter fires the new selection', () => {
    setup();
    fireEvent.keyDown(window, { key: 'ArrowDown' });
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(mockNavigate).toHaveBeenCalledWith('/team');
  });

  it('does not render when closed', () => {
    render(
      <MemoryRouter>
        <CommandPalette open={false} onClose={vi.fn()} />
      </MemoryRouter>
    );
    expect(screen.queryByPlaceholderText('Type a command…')).toBeNull();
  });
});
