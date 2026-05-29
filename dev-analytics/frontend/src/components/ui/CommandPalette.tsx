import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Home, Users, UserCog, Database, Settings, Shield, Sun, Moon, CalendarRange,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';

interface Command {
  label: string;
  description?: string;
  icon: React.FC<{ width?: number; height?: number }>;
  action: () => void;
  role?: 'MANAGER' | 'ADMIN';
}

interface Props {
  open: boolean;
  onClose: () => void;
  onOpenDateRange?: () => void;
}

export function CommandPalette({ open, onClose, onOpenDateRange }: Props) {
  const navigate = useNavigate();
  const { isManager, isAdmin } = useAuth();
  const { theme, setTheme } = useTheme();
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const allCommands: Command[] = [
    { label: 'Go to Dashboard',      description: 'Personal metrics overview', icon: Home,       action: () => navigate('/dashboard') },
    { label: 'Go to Team',            description: 'Team analytics view',       icon: Users,      action: () => navigate('/team'),       role: 'MANAGER' },
    { label: 'Go to Manage Teams',    description: 'Team administration',       icon: UserCog,    action: () => navigate('/team-manage'), role: 'MANAGER' },
    { label: 'Go to Data Sources',    description: 'Connected repositories',    icon: Database,   action: () => navigate('/datasources') },
    { label: 'Go to Settings',        description: 'Preferences and profile',   icon: Settings,   action: () => navigate('/settings') },
    { label: 'Go to Admin',           description: 'User and system admin',     icon: Shield,     action: () => navigate('/admin'),       role: 'ADMIN' },
    { label: 'Toggle dark mode',      description: theme === 'dark' ? 'Switch to light' : 'Switch to dark', icon: theme === 'dark' ? Sun : Moon, action: () => { setTheme({ theme: theme === 'dark' ? 'light' : 'dark' }); onClose(); } },
    { label: 'Open date range picker', description: 'Change the analysis period', icon: CalendarRange, action: () => { onOpenDateRange?.(); onClose(); } },
  ];

  const visibleCommands = allCommands.filter((cmd) => {
    if (cmd.role === 'ADMIN' && !isAdmin) return false;
    if (cmd.role === 'MANAGER' && !isManager) return false;
    return true;
  });

  const filtered = query
    ? visibleCommands.filter((c) => c.label.toLowerCase().includes(query.toLowerCase()))
    : visibleCommands;

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSelected(0);
  }, [query]);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setQuery('');
      setSelected(0);
      setTimeout(() => inputRef.current?.focus(), 30);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') { onClose(); return; }
      if (e.key === 'ArrowDown') { e.preventDefault(); setSelected((s) => Math.min(s + 1, filtered.length - 1)); return; }
      if (e.key === 'ArrowUp')   { e.preventDefault(); setSelected((s) => Math.max(s - 1, 0)); return; }
      if (e.key === 'Enter' && filtered[selected]) {
        e.preventDefault();
        filtered[selected].action();
        onClose();
      }
    }
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [open, filtered, selected, onClose]);

  if (!open) return null;

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, zIndex: 50,
        background: 'color-mix(in oklab, #000 35%, transparent)',
        backdropFilter: 'blur(2px)',
        display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
        paddingTop: '14vh',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%', maxWidth: 560,
          background: 'var(--bg-card)',
          border: '1px solid var(--line)',
          borderRadius: 12,
          boxShadow: '0 24px 56px rgba(0,0,0,.22)',
          overflow: 'hidden',
          animation: 'mpop .18s cubic-bezier(.2,.9,.3,1.2)',
        }}
      >
        {/* Search input */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--line-2)' }}>
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Type a command…"
            aria-label="Command palette search"
            style={{
              width: '100%', background: 'transparent', border: 'none', outline: 'none',
              fontFamily: 'var(--font-sans)', fontSize: 15, color: 'var(--fg)',
            }}
          />
        </div>

        {/* Command list */}
        <div style={{ maxHeight: 360, overflowY: 'auto' }}>
          {filtered.length === 0 && (
            <div className="t-muted" style={{ padding: '20px 16px', textAlign: 'center', fontSize: 13 }}>
              No commands match "{query}"
            </div>
          )}
          {filtered.map((cmd, i) => {
            const Icon = cmd.icon;
            const isSelected = i === selected;
            return (
              <button
                key={cmd.label}
                onClick={() => { cmd.action(); onClose(); }}
                onMouseEnter={() => setSelected(i)}
                aria-selected={isSelected}
                style={{
                  width: '100%', display: 'flex', alignItems: 'center', gap: 12,
                  padding: '10px 16px', border: 'none', cursor: 'pointer', textAlign: 'left',
                  background: isSelected ? 'var(--bg-2)' : 'transparent',
                  color: 'var(--fg)',
                  borderLeft: isSelected ? '2px solid var(--accent)' : '2px solid transparent',
                  transition: 'background .08s',
                  fontFamily: 'var(--font-sans)',
                }}
              >
                <Icon width={16} height={16} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13.5, fontWeight: isSelected ? 500 : 400 }}>{cmd.label}</div>
                  {cmd.description && (
                    <div className="t-label" style={{ fontSize: 11, marginTop: 1 }}>{cmd.description}</div>
                  )}
                </div>
              </button>
            );
          })}
        </div>

        {/* Footer hint */}
        <div style={{
          padding: '8px 16px', borderTop: '1px solid var(--line-2)',
          display: 'flex', gap: 12, alignItems: 'center',
        }}>
          <span className="t-label" style={{ fontSize: 10.5 }}><kbd className="kbd">↑↓</kbd> navigate</span>
          <span className="t-label" style={{ fontSize: 10.5 }}><kbd className="kbd">↵</kbd> select</span>
          <span className="t-label" style={{ fontSize: 10.5 }}><kbd className="kbd">Esc</kbd> close</span>
        </div>
      </div>
      <style>{`@keyframes mpop { from { opacity: 0; transform: translateY(8px) scale(.98) } to { opacity: 1; transform: none } }`}</style>
    </div>
  );
}