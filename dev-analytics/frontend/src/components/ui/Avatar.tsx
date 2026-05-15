import { useState } from 'react';
import clsx from 'clsx';

interface AvatarUser {
  id: number;
  username: string;
  hasCustomAvatar?: boolean;
  avatarPreset?: string;
}

interface AvatarProps {
  user: AvatarUser;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZES = {
  xs: 'w-6 h-6 text-xs',
  sm: 'w-7 h-7 text-xs',
  md: 'w-9 h-9 text-sm',
  lg: 'w-20 h-20 text-2xl',
};

// Deterministic colour from username — cycles through a violet/blue/teal/rose palette
const PALETTE = [
  'bg-violet-500', 'bg-blue-500', 'bg-teal-500', 'bg-rose-500',
  'bg-indigo-500', 'bg-emerald-500', 'bg-amber-500', 'bg-sky-500',
];
function initialsColor(username: string) {
  let h = 0;
  for (let i = 0; i < username.length; i++) h = (h * 31 + username.charCodeAt(i)) | 0;
  return PALETTE[Math.abs(h) % PALETTE.length];
}

export function Avatar({ user, size = 'md', className }: AvatarProps) {
  const [imgError, setImgError] = useState(false);
  const sizeClass = SIZES[size];
  const initials = user.username.slice(0, 2).toUpperCase();

  if (user.hasCustomAvatar && !imgError) {
    return (
      <img
        src={`/api/users/${user.id}/avatar`}
        alt={user.username}
        loading="lazy"
        className={clsx('rounded-full object-cover flex-shrink-0', sizeClass, className)}
        onError={() => setImgError(true)}
      />
    );
  }

  if (user.avatarPreset && !imgError) {
    return (
      <img
        src={`/avatars/${user.avatarPreset}.svg`}
        alt={user.username}
        loading="lazy"
        className={clsx('rounded-full object-cover flex-shrink-0', sizeClass, className)}
        onError={() => setImgError(true)}
      />
    );
  }

  return (
    <div
      className={clsx(
        'rounded-full flex items-center justify-center font-semibold text-white flex-shrink-0',
        initialsColor(user.username),
        sizeClass,
        className,
      )}
      aria-label={user.username}
    >
      {initials}
    </div>
  );
}