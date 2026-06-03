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

describe('Modal accessibility', () => {
  it('has role=dialog and aria-modal', () => {
    render(
      <Modal open title="Test Modal" onClose={() => {}}>
        <span>content</span>
      </Modal>
    );
    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  it('is labelled by its title text', () => {
    render(
      <Modal open title="My Modal" onClose={() => {}}>
        <span>content</span>
      </Modal>
    );
    expect(screen.getByRole('dialog')).toHaveAccessibleName('My Modal');
  });

  it('renders nothing when closed', () => {
    render(
      <Modal open={false} title="Closed Modal" onClose={() => {}}>
        <span>content</span>
      </Modal>
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});