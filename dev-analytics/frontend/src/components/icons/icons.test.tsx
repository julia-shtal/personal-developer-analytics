import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import * as Icons from './index';

const iconNames = Object.keys(Icons) as (keyof typeof Icons)[];

describe('Metric icons', () => {
  it.each(iconNames)('%s renders an svg with correct viewBox', (name) => {
    const Icon = Icons[name] as React.ComponentType<React.SVGProps<SVGSVGElement>>;
    const { container } = render(<Icon />);
    const svg = container.querySelector('svg');
    expect(svg).toBeTruthy();
    expect(svg?.getAttribute('viewBox')).toBe('0 0 24 24');
  });
});