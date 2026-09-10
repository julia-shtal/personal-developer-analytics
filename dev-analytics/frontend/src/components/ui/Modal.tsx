import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  eyebrow?: string;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}

export function Modal({
  open,
  onClose,
  title,
  eyebrow,
  children,
  footer,
  width = 540,
}: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  // onClose is an inline arrow from the owning page, so its identity changes every render.
  // Read it through a ref to keep the effects below keyed on `open` alone.
  const onCloseRef = useRef(onClose);
  useEffect(() => { onCloseRef.current = onClose; });

  // Escape-to-close + body scroll lock, subscribed once per open.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCloseRef.current(); };
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open]);

  // Initial focus, exactly once per open — keyed on `open` alone, or a parent re-render
  // would yank the caret out of the field being typed in. A child that already claimed
  // focus (an autoFocus input does so during commit) keeps it.
  useEffect(() => {
    if (!open) return;
    const dialog = dialogRef.current;
    if (!dialog || dialog.contains(document.activeElement)) return;
    dialog.querySelector<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )?.focus();
  }, [open]);

  if (!open) return null;

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, zIndex: 50,
        background: 'color-mix(in oklab, #000 35%, transparent)',
        backdropFilter: 'blur(2px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 20,
        animation: 'mfade .14s ease',
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%', maxWidth: width,
          background: 'var(--bg-card)',
          border: '1px solid var(--line)',
          borderRadius: 12,
          boxShadow: '0 24px 56px rgba(0,0,0,.18)',
          display: 'flex', flexDirection: 'column',
          maxHeight: '90vh',
          animation: 'mpop .18s cubic-bezier(.2,.9,.3,1.2)',
          overflow: 'hidden',
        }}
      >
        {/* Header */}
        <div style={{ padding: '18px 22px 14px', borderBottom: '1px solid var(--line-2)' }}>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              {eyebrow && <div className="t-eyebrow" style={{ marginBottom: 6 }}>{eyebrow}</div>}
              <div id={titleId} className="t-h2" style={{ fontSize: 18 }}>{title}</div>
            </div>
            <button className="btn btn-sm btn-icon" onClick={onClose} aria-label="Close dialog">
              <X width={13} height={13} />
            </button>
          </div>
        </div>

        {/* Body */}
        <div style={{ padding: '18px 22px', overflow: 'auto', flex: 1 }}>{children}</div>

        {/* Footer */}
        {footer && (
          <div style={{ padding: '14px 22px', borderTop: '1px solid var(--line-2)', background: 'var(--bg-2)' }}>
            {footer}
          </div>
        )}
      </div>

      <style>{`
        @keyframes mfade { from { opacity: 0 } to { opacity: 1 } }
        @keyframes mpop  { from { opacity: 0; transform: translateY(8px) scale(.98) } to { opacity: 1; transform: none } }
      `}</style>
    </div>
  );
}
