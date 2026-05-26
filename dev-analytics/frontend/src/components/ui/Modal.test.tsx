import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Modal } from './Modal';

function Setup({ onClose = vi.fn() } = {}) {
  return (
    <Modal open onClose={onClose} title="Test modal">
      <button>First</button>
      <button>Second</button>
    </Modal>
  );
}

describe('Modal', () => {
  it('renders title and children', () => {
    render(<Setup />);
    expect(screen.getByText('Test modal')).toBeTruthy();
    expect(screen.getByText('First')).toBeTruthy();
  });

  it('Escape key calls onClose', () => {
    const onClose = vi.fn();
    render(<Setup onClose={onClose} />);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('clicking backdrop calls onClose', () => {
    const onClose = vi.fn();
    const { container } = render(<Setup onClose={onClose} />);
    const backdrop = container.firstChild as HTMLElement;
    fireEvent.click(backdrop);
    expect(onClose).toHaveBeenCalled();
  });

  it('clicking inside modal does not close', () => {
    const onClose = vi.fn();
    render(<Setup onClose={onClose} />);
    fireEvent.click(screen.getByText('First'));
    expect(onClose).not.toHaveBeenCalled();
  });
});