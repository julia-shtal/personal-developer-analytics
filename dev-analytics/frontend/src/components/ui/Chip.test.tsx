import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Chip } from './Chip';

describe('Chip', () => {
  it('renders children', () => {
    render(<Chip>Active</Chip>);
    expect(screen.getByText('Active')).toBeTruthy();
  });

  it('applies color class', () => {
    const { container } = render(<Chip color="violet">V</Chip>);
    expect(container.firstChild).toHaveClass('chip-violet');
  });

  it('renders dot when dot=true', () => {
    const { container } = render(<Chip dot>D</Chip>);
    expect(container.querySelector('.chip-dot')).toBeTruthy();
  });

  it('applies chip-accent class when accent=true', () => {
    const { container } = render(<Chip accent>Generating…</Chip>);
    expect(container.firstChild).toHaveClass('chip-accent');
  });

  it('accent prop overrides color prop', () => {
    const { container } = render(<Chip accent color="violet">X</Chip>);
    expect(container.firstChild).toHaveClass('chip-accent');
    expect(container.firstChild).not.toHaveClass('chip-violet');
  });
});