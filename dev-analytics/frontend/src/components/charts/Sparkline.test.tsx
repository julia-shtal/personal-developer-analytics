import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Sparkline } from './Sparkline';

const data = [
  { value: 5, date: '2024-01-01' },
  { value: 10, date: '2024-01-02' },
  { value: 3, date: '2024-01-03' },
];

describe('Sparkline', () => {
  it('renders svg with path', () => {
    const { container } = render(<Sparkline data={data} />);
    expect(container.querySelector('svg')).toBeTruthy();
    expect(container.querySelector('path')).toBeTruthy();
  });

  it('returns null for empty data', () => {
    const { container } = render(<Sparkline data={[]} />);
    expect(container.firstChild).toBeNull();
  });
});