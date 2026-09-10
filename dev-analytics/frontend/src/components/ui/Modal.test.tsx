import { useState } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
describe('Modal focus stability', () => {
  // Mirrors real usage: the component owning the input state also creates the onClose
  // closure, so onClose gets a fresh identity on every keystroke re-render.
  function Host() {
    const [value, setValue] = useState('');
    return (
      <Modal open onClose={() => setValue('')} title="Host modal">
        <input aria-label="field" value={value} onChange={(e) => setValue(e.target.value)} />
      </Modal>
    );
  }

  it('keeps focus in a text field while typing when onClose identity changes each render', async () => {
    const user = userEvent.setup();
    render(<Host />);

    const field = screen.getByLabelText('field') as HTMLInputElement;
    field.focus();
    await user.type(field, 'infra');

    expect(field.value).toBe('infra');
    expect(document.activeElement).toBe(field);
  });

  it('autofocuses the field marked autoFocus rather than the close button', () => {
    render(
      <Modal open onClose={() => {}} title="Autofocus modal">
        <input aria-label="named" autoFocus />
      </Modal>
    );
    expect(document.activeElement).toBe(screen.getByLabelText('named'));
  });
});
