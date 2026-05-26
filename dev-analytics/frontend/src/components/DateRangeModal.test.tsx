import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DateRangeModal } from './DateRangeModal';

const defaultRange = { from: '2024-01-01', to: '2024-01-07' };

describe('DateRangeModal', () => {
  it('renders preset buttons', () => {
    render(
      <DateRangeModal
        open
        onClose={vi.fn()}
        value={defaultRange}
        onChange={vi.fn()}
      />
    );
    expect(screen.getByText('Last 7 days')).toBeTruthy();
    expect(screen.getByText('4 weeks')).toBeTruthy();
  });

  it('selecting a preset calls onChange once and closes modal', () => {
    const onChange = vi.fn();
    const onClose = vi.fn();
    render(
      <DateRangeModal
        open
        onClose={onClose}
        value={defaultRange}
        onChange={onChange}
      />
    );
    fireEvent.click(screen.getByText('Last 7 days'));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});