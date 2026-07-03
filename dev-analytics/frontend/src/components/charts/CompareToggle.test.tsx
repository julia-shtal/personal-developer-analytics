import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CompareToggle } from './CompareToggle';

describe('CompareToggle', () => {
  it('renders a Compare button and no date inputs when disabled', () => {
    render(
      <CompareToggle
        enabled={false}
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: /compare/i })).toBeInTheDocument();
    expect(screen.queryByLabelText('Comparison range start')).not.toBeInTheDocument();
  });

  it('shows the comparison date inputs pre-filled when enabled', () => {
    render(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Comparison range start')).toHaveValue('2026-04-01');
    expect(screen.getByLabelText('Comparison range end')).toHaveValue('2026-04-30');
  });

  it('calls onToggle when the Compare button is clicked', async () => {
    const onToggle = vi.fn();
    render(
      <CompareToggle
        enabled={false}
        onToggle={onToggle}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={vi.fn()}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /compare/i }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('calls onCompareRangeChange with the updated from date', async () => {
    const onCompareRangeChange = vi.fn();
    render(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={onCompareRangeChange}
      />,
    );
    const startInput = screen.getByLabelText('Comparison range start') as HTMLInputElement;
    fireEvent.change(startInput, { target: { value: '2026-03-01' } });
    expect(onCompareRangeChange).toHaveBeenCalledWith({ from: '2026-03-01', to: '2026-04-30' });
  });

  it('shows the locked-length caption with correct pluralisation when windowDays is provided', () => {
    const { rerender } = render(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-08' }}
        onCompareRangeChange={vi.fn()}
        windowDays={8}
      />,
    );
    expect(screen.getByText('Comparison window locked to 8 days')).toBeInTheDocument();

    rerender(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-01' }}
        onCompareRangeChange={vi.fn()}
        windowDays={1}
      />,
    );
    expect(screen.getByText('Comparison window locked to 1 day')).toBeInTheDocument();
  });

  it('omits the caption when windowDays is not provided', () => {
    render(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={vi.fn()}
      />,
    );
    expect(screen.queryByText(/Comparison window locked/)).not.toBeInTheDocument();
  });

  it('calls onCompareRangeChange with the updated to date', () => {
    const onCompareRangeChange = vi.fn();
    render(
      <CompareToggle
        enabled
        onToggle={vi.fn()}
        compareRange={{ from: '2026-04-01', to: '2026-04-30' }}
        onCompareRangeChange={onCompareRangeChange}
      />,
    );
    const endInput = screen.getByLabelText('Comparison range end') as HTMLInputElement;
    fireEvent.change(endInput, { target: { value: '2026-05-31' } });
    expect(onCompareRangeChange).toHaveBeenCalledWith({ from: '2026-04-01', to: '2026-05-31' });
  });
});
