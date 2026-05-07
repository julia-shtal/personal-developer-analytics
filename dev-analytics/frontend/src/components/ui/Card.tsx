import { type ReactNode } from 'react';
import { clsx } from 'clsx';

interface CardProps {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
  hoverable?: boolean;
}

export function Card({ children, className, onClick, hoverable }: CardProps) {
  return (
    <div
      onClick={onClick}
      className={clsx(
        'bg-white rounded-xl border border-gray-200 shadow-sm',
        hoverable && 'cursor-pointer hover:shadow-md hover:border-violet-200 transition-all duration-200',
        onClick && 'cursor-pointer',
        className
      )}
    >
      {children}
    </div>
  );
}

export function CardHeader({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('px-5 pt-5 pb-3 border-b border-gray-100', className)}>
      {children}
    </div>
  );
}

export function CardBody({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('px-5 py-4', className)}>
      {children}
    </div>
  );
}

type IconVariant = 'violet' | 'teal' | 'amber';

const iconVariantClasses: Record<IconVariant, string> = {
  violet: 'bg-violet-50 text-violet-600',
  teal: 'bg-teal-50 text-teal-600',
  amber: 'bg-amber-50 text-amber-600',
};

interface KpiCardProps {
  label: string;
  value: string | number;
  subtitle?: string;
  icon?: ReactNode;
  iconVariant?: IconVariant;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
  tooltip?: string;
}

export function KpiCard({ label, value, subtitle, icon, iconVariant = 'violet', trend, trendValue, tooltip }: KpiCardProps) {
  const trendColor = trend === 'up' ? 'text-emerald-600' : trend === 'down' ? 'text-red-500' : 'text-gray-500';

  return (
    <Card>
      <div className="px-5 py-4">
        <div className="flex items-start justify-between">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-500 truncate">{label}</p>
            <p className="mt-1 text-3xl font-semibold text-gray-900">{value}</p>
            {subtitle && <p className="mt-0.5 text-xs text-gray-400">{subtitle}</p>}
          </div>
          {icon && (
            <div className="ml-3 flex-shrink-0 relative group">
              <div className={clsx('p-2.5 rounded-lg cursor-default', iconVariantClasses[iconVariant])}>
                {icon}
              </div>
              {tooltip && (
                <div className="absolute right-0 bottom-full mb-2 z-50 w-60 rounded-lg bg-white border border-gray-200 text-gray-700 text-xs px-3 py-2.5 shadow-lg opacity-0 group-hover:opacity-100 transition-opacity duration-150 pointer-events-none leading-relaxed">
                  <div className="absolute right-3 -bottom-1.5 w-3 h-3 bg-white border-r border-b border-gray-200 rotate-45 rounded-sm" />
                  {tooltip}
                </div>
              )}
            </div>
          )}
        </div>
        {trendValue && (
          <p className={`mt-2 text-xs font-medium ${trendColor}`}>
            {trend === 'up' ? '↑' : trend === 'down' ? '↓' : '—'} {trendValue}
          </p>
        )}
      </div>
    </Card>
  );
}
