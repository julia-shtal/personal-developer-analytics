import type { LogoVariant } from '@/lib/theme';

/**
 * The active brand logo for the entire application.
 *
 * To switch the logo across every surface (sidebar, login, welcome,
 * favicon), change ONLY this constant. No other file is touched.
 *
 * Variants (all four kept in src/components/brand/):
 *   - 'pulse'    — Pulse bars. Default. The chart-as-D mark.
 *   - 'bracket'  — Bracket monogram [d] with a cursor terminal.
 *   - 'slash'    — Terminal-prompt: ~/d with blinking cursor.
 *   - 'crystal'  — Editorial serif italic D in a circle.
 *
 * Settings → Appearance also exposes a per-user override; the
 * constant below is the application's shipping default.
 */
export const ACTIVE_LOGO: LogoVariant = 'pulse';
export const APP_VERSION = 'v 0.4.1';