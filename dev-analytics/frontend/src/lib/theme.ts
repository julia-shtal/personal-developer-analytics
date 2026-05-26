export type Theme = 'light' | 'dark';
export type LogoVariant = 'pulse' | 'bracket' | 'slash' | 'crystal';

export interface ThemeState {
  theme: Theme;
  logo: LogoVariant;
  accent: string;
  showStatusBar: boolean;
}

export const ACCENT_PRESETS: readonly string[] = [
  '#7a5ae0',
  '#3d9cc4',
  '#c98931',
  '#3a9a73',
  '#d96650',
];

export const DEFAULT_THEME_STATE: Readonly<ThemeState> = {
  theme: 'light',
  logo: 'pulse',
  accent: '#7a5ae0',
  showStatusBar: true,
};
