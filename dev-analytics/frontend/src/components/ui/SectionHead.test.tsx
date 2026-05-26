import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SectionHead } from './SectionHead';

describe('SectionHead', () => {
  it('renders title', () => {
    render(<SectionHead title="My section" />);
    expect(screen.getByText('My section')).toBeTruthy();
  });

  it('renders eyebrow and count', () => {
    render(<SectionHead eyebrow="overview" title="Stats" count={5} />);
    expect(screen.getByText('overview')).toBeTruthy();
    expect(screen.getByText(/· 5/)).toBeTruthy();
  });
});