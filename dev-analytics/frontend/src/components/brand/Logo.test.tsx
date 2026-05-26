import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { LogoPulse } from './LogoPulse';
import { LogoBracket } from './LogoBracket';
import { LogoSlash } from './LogoSlash';
import { LogoCrystal } from './LogoCrystal';

const variants = [
  { name: 'pulse',   Component: LogoPulse   },
  { name: 'bracket', Component: LogoBracket },
  { name: 'slash',   Component: LogoSlash   },
  { name: 'crystal', Component: LogoCrystal },
] as const;

describe('Logo variants', () => {
  it.each(variants)('$name renders an svg at size 32', ({ Component }) => {
    const { container } = render(<Component size={32} />);
    const svg = container.querySelector('svg');
    expect(svg).toBeTruthy();
    expect(svg?.getAttribute('width')).toBe('32');
    expect(svg?.getAttribute('height')).toBe('32');
  });

  it.each(variants)('$name renders wordmark when withWordmark=true', ({ Component }) => {
    const { container } = render(<Component size={32} withWordmark />);
    expect(container.querySelector('span > span')).toBeTruthy();
  });
});