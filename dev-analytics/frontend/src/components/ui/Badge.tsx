import { type ReactNode } from 'react';
import { clsx } from 'clsx';

type Color = 'violet' | 'emerald' | 'amber' | 'red' | 'gray' | 'blue' | 'teal';

interface BadgeProps {
  children: ReactNode;
  color?: Color;
  dot?: boolean;
  className?: string;
}

const colorClasses: Record<Color, string> = {
  violet: 'bg-violet-100 text-violet-700',
  emerald: 'bg-emerald-100 text-emerald-700',
  amber: 'bg-amber-100 text-amber-700',
  red: 'bg-red-100 text-red-700',
  gray: 'bg-gray-100 text-gray-600',
  blue: 'bg-blue-100 text-blue-700',
  teal: 'bg-teal-100 text-teal-700',
};

const dotColorClasses: Record<Color, string> = {
  violet: 'bg-violet-700',
  emerald: 'bg-emerald-700',
  amber: 'bg-amber-700',
  red: 'bg-red-700',
  gray: 'bg-gray-600',
  blue: 'bg-blue-700',
  teal: 'bg-teal-700',
};

export function Badge({ children, color = 'gray', dot, className }: BadgeProps) {
  return (
    <span className={clsx(
      'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium',
      colorClasses[color],
      className
    )}>
      {dot && (
        <span className={clsx('w-1.5 h-1.5 rounded-full flex-shrink-0', dotColorClasses[color])} />
      )}
      {children}
    </span>
  );
}
