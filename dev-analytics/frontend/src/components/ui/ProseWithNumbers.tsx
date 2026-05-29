import React from 'react';
import { tokeniseNumbers } from '@/lib/prose';

interface Props {
  text: string;
  className?: string;
  style?: React.CSSProperties;
}

export function ProseWithNumbers({ text, className, style }: Props) {
  const tokens = tokeniseNumbers(text);
  return (
    <p className={className} style={style}>
      {tokens.map((t, i) =>
        t.type === 'number'
          ? <strong key={i} style={{ fontFamily: 'var(--font-mono)', fontSize: '0.96em', color: 'var(--fg)' }}>{t.value}</strong>
          : <React.Fragment key={i}>{t.value}</React.Fragment>
      )}
    </p>
  );
}