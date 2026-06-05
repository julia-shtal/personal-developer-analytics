import { clsx } from 'clsx';
import type { ReactNode } from 'react';

type ChipColor = 'violet' | 'cyan' | 'amber' | 'emerald' | 'coral';

interface ChipProps {
  children: ReactNode;
  color?: ChipColor;
  accent?: boolean;
  dot?: boolean;
  className?: string;
}

export function Chip({ children, color, accent = false, dot = false, className }: ChipProps) {
  return (
    <span className={clsx('chip', accent ? 'chip-accent' : color && `chip-${color}`, className)}>
      {dot && <span className="chip-dot" />}
      {children}
    </span>
  );
}
