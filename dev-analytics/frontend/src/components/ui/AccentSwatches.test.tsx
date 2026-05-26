import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AccentSwatches } from './AccentSwatches';
import { ACCENT_SWATCHES } from './accentSwatches.constants';

describe('AccentSwatches', () => {
  it('renders all preset swatches', () => {
    const onChange = vi.fn();
    render(<AccentSwatches value="#7a5ae0" onChange={onChange} />);
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBe(ACCENT_SWATCHES.length);
  });

  it('clicking a swatch calls onChange once', () => {
    const onChange = vi.fn();
    render(<AccentSwatches value="#7a5ae0" onChange={onChange} />);
    fireEvent.click(screen.getByTitle(/cyan/));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('#3d9cc4');
  });

  it('typing a 6-char hex in the color input fires onChange once', () => {
    const onChange = vi.fn();
    render(<AccentSwatches value="#aabbcc" onChange={onChange} />);
    const colorInput = document.querySelector('input[type="color"]') as HTMLInputElement;
    fireEvent.change(colorInput, { target: { value: '#ff0099' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});