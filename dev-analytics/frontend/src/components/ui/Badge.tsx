import { type ReactNode } from 'react';
import { clsx } from 'clsx';

type Color = 'violet' | 'emerald' | 'amber' | 'red' | 'gray' | 'blue';

interface BadgeProps {
  children: ReactNode;
  color?: Color;
  className?: string;
}

const colorClasses: Record<Color, string> = {
  violet: 'bg-violet-100 text-violet-700',
  emerald: 'bg-emerald-100 text-emerald-700',
  amber: 'bg-amber-100 text-amber-700',
  red: 'bg-red-100 text-red-700',
  gray: 'bg-gray-100 text-gray-600',
  blue: 'bg-blue-100 text-blue-700',
};

export function Badge({ children, color = 'gray', className }: BadgeProps) {
  return (
    <span className={clsx(
      'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
      colorClasses[color],
      className
    )}>
      {children}
    </span>
  );
}
