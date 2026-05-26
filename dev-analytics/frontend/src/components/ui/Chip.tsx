import { clsx } from 'clsx';
import type { ReactNode } from 'react';

type ChipColor = 'violet' | 'cyan' | 'amber' | 'emerald' | 'coral';

interface ChipProps {
  children: ReactNode;
  color?: ChipColor;
  dot?: boolean;
  className?: string;
}

export function Chip({ children, color, dot = false, className }: ChipProps) {
  return (
    <span className={clsx('chip', color && `chip-${color}`, className)}>
      {dot && <span className="chip-dot" />}
      {children}
    </span>
  );
}
