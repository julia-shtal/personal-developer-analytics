import { useState, type ReactNode } from 'react';

interface TooltipProps {
  children: ReactNode;
  content: ReactNode;
}

export function Tooltip({ children, content }: TooltipProps) {
  const [show, setShow] = useState(false);

  return (
    <span
      style={{ position: 'relative', display: 'inline-flex' }}
      onMouseEnter={() => setShow(true)}
      onMouseLeave={() => setShow(false)}
    >
      {children}
      {show && (
        <span
          style={{
            position: 'absolute', bottom: '100%', left: '50%',
            transform: 'translate(-50%, -8px)',
            background: 'var(--fg)', color: 'var(--bg)',
            padding: '6px 10px', borderRadius: 6,
            fontFamily: 'var(--font-mono)', fontSize: 10.5,
            whiteSpace: 'normal', maxWidth: 260, lineHeight: 1.45,
            textAlign: 'left', zIndex: 50, pointerEvents: 'none',
          }}
        >
          {content}
        </span>
      )}
    </span>
  );
}