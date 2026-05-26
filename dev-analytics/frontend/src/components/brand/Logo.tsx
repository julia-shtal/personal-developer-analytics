import type { LogoVariant } from '@/lib/theme';
import { LogoPulse } from './LogoPulse';
import { LogoBracket } from './LogoBracket';
import { LogoSlash } from './LogoSlash';
import { LogoCrystal } from './LogoCrystal';

interface LogoProps {
  size?: number;
  withWordmark?: boolean;
}

const REGISTRY = {
  pulse:   LogoPulse,
  bracket: LogoBracket,
  slash:   LogoSlash,
  crystal: LogoCrystal,
} as const;

export function Logo({ variant, ...rest }: { variant: LogoVariant } & LogoProps) {
  const C = REGISTRY[variant] ?? LogoPulse;
  return <C {...rest} />;
}