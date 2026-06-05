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
    const cx = rect.left + rect.width / 2;
    // Clamp so the tooltip (max 320px wide) stays 8px inside the viewport on both sides
    const half = 320 / 2;
    const margin = 8;
    const clamped = Math.max(margin + half, Math.min(cx, window.innerWidth - margin - half));
    setPos({ x: clamped, y: rect.top });
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
            top: pos.y - 10,
            transform: 'translate(-50%, -100%)',
            background: 'var(--fg)', color: 'var(--bg)',
            padding: '6px 10px', borderRadius: 6,
            fontFamily: 'var(--font-mono)', fontSize: 10.5,
            whiteSpace: 'normal', maxWidth: 320, minWidth: 200, lineHeight: 1.45,
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
