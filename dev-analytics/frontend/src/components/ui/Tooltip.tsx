import { useState, useCallback, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

interface TooltipProps {
  children: ReactNode;
  content: ReactNode;
}

export function Tooltip({ children, content }: TooltipProps) {
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);

  const handleEnter = useCallback((e: React.MouseEvent<HTMLSpanElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    setPos({ x: rect.left + rect.width / 2, y: rect.top });
  }, []);

  const handleLeave = useCallback(() => setPos(null), []);

  return (
    <span
      style={{ position: 'relative', display: 'inline-flex' }}
      onMouseEnter={handleEnter}
      onMouseLeave={handleLeave}
    >
      {children}
      {pos && createPortal(
        <span
          style={{
            position: 'fixed',
            left: pos.x,
            top: pos.y - 8,
            transform: 'translate(-50%, -100%)',
            background: 'var(--fg)', color: 'var(--bg)',
            padding: '6px 10px', borderRadius: 6,
            fontFamily: 'var(--font-mono)', fontSize: 10.5,
            whiteSpace: 'normal', maxWidth: 260, lineHeight: 1.45,
            textAlign: 'left', zIndex: 9999, pointerEvents: 'none',
          }}
        >
          {content}
        </span>,
        document.body,
      )}
    </span>
  );
}
