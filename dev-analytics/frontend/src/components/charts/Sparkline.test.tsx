import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Sparkline } from './Sparkline';

const data = [
  { value: 5, date: '2024-01-01' },
  { value: 10, date: '2024-01-02' },
  { value: 3, date: '2024-01-03' },
];

const compareData = [
  { value: 8, date: '2023-12-01' },
  { value: 2, date: '2023-12-02' },
  { value: 6, date: '2023-12-03' },
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

  it('renders two paths when compareData is provided', () => {
    const { container } = render(<Sparkline data={data} compareData={compareData} />);
    expect(container.querySelectorAll('path[stroke-dasharray]')).toHaveLength(1);
    // primary path (no dasharray) + compare path (dasharray) + optional area fill
    expect(container.querySelectorAll('path').length).toBeGreaterThanOrEqual(2);
  });

  it('renders only one path when compareData is absent', () => {
    const { container } = render(<Sparkline data={data} area={false} />);
    expect(container.querySelectorAll('path[stroke-dasharray]')).toHaveLength(0);
    expect(container.querySelectorAll('path')).toHaveLength(1);
  });
});