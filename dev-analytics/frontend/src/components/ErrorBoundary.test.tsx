import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from './ErrorBoundary';

function Bomb(): never {
  throw new Error('Test render crash');
}

describe('ErrorBoundary', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders fallback when a child throws', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Something went wrong')).toBeTruthy();
    expect(screen.getByText('Test render crash')).toBeTruthy();
  });

  it('renders a Reload button', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('button', { name: /reload/i })).toBeTruthy();
  });

  it('renders a custom fallback when provided', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary fallback={<div>Custom fallback</div>}>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Custom fallback')).toBeTruthy();
  });

  it('renders children normally when no error is thrown', () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('All good')).toBeTruthy();
  });
});