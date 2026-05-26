import type { ReactNode } from 'react';

interface SectionHeadProps {
  eyebrow?: string;
  title: string;
  count?: number;
  action?: ReactNode;
}

export function SectionHead({ eyebrow, title, count, action }: SectionHeadProps) {
  return (
    <div className="section-head">
      <div>
        {eyebrow && <div className="t-eyebrow" style={{ marginBottom: 4 }}>{eyebrow}</div>}
        <div className="t-h2">
          {title}
          {count != null && (
            <span style={{ color: 'var(--fg-3)', marginLeft: 10, fontSize: '0.7em' }}>· {count}</span>
          )}
        </div>
      </div>
      {action}
    </div>
  );
}
