import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { KpiTile } from './KpiTile';

describe('KpiTile', () => {
  it('renders label and value', () => {
    render(<KpiTile label="commits" value="42" />);
    expect(screen.getByText('commits')).toBeTruthy();
    expect(screen.getByText('42')).toBeTruthy();
  });

  it('renders sub text when provided', () => {
    render(<KpiTile label="commits" value="42" sub="+5 this week" />);
    expect(screen.getByText('+5 this week')).toBeTruthy();
  });

  it('four tiles in grid-kpi render correctly', () => {
    const { container } = render(
      <div className="card">
        <div className="grid-kpi">
          <KpiTile label="a" value="1" />
          <KpiTile label="b" value="2" />
          <KpiTile label="c" value="3" />
          <KpiTile label="d" value="4" />
        </div>
      </div>
    );
    expect(container.querySelectorAll('.grid-kpi > *').length).toBe(4);
  });
});