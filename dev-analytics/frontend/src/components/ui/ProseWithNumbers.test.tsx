import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { ProseWithNumbers } from './ProseWithNumbers';

describe('ProseWithNumbers', () => {
  it('wraps numbers in <strong> and plain text in fragments', () => {
    const { container } = render(<ProseWithNumbers text="290 commits and 4.1× faster" />);
    const strongs = container.querySelectorAll('strong');
    expect(strongs).toHaveLength(2);
    expect(strongs[0].textContent).toBe('290');
    expect(strongs[1].textContent).toBe('4.1×');
  });

  it('renders no <strong> when there are no numbers', () => {
    const { container } = render(<ProseWithNumbers text="no numbers here" />);
    const strongs = container.querySelectorAll('strong');
    expect(strongs).toHaveLength(0);
  });

  it('applies className and style to the wrapping <p>', () => {
    const { container } = render(
      <ProseWithNumbers text="test" className="t-body" style={{ color: 'red' }} />
    );
    const p = container.querySelector('p');
    expect(p?.className).toBe('t-body');
    expect(p?.style.color).toBe('red');
  });
});
