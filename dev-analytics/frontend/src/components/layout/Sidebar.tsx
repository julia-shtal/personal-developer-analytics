import type { CSSProperties, ReactNode } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import {
  Home,
  Users,
  UserCog,
  Database,
  Settings,
  Shield,
  ChevronRight,
  Sun,
  Moon,
  LogOut,
  Search,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';
import { Avatar } from '@/components/ui/Avatar';
import { APP_VERSION } from '@/config/branding';

const NAV_LINK_BASE: CSSProperties = {
  position: 'relative',
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  width: '100%',
  padding: '7px 10px',
  border: 'none',
  borderRadius: 6,
  fontFamily: 'var(--font-sans)',
  fontSize: 13,
  cursor: 'pointer',
  textDecoration: 'none',
};

function NavItem({ to, icon, label }: { to: string; icon: ReactNode; label: string }) {
  return (
    <NavLink
      to={to}
      style={({ isActive }) => ({
        ...NAV_LINK_BASE,
        background: isActive ? 'var(--bg-2)' : 'transparent',
        color: isActive ? 'var(--fg)' : 'var(--fg-3)',
        fontWeight: isActive ? 500 : 400,
      })}
    >
      {({ isActive }) => (
        <>
          {isActive && (
            <span style={{
              position: 'absolute', left: 0, top: 6, bottom: 6,
              width: 2, background: 'var(--accent)', borderRadius: 1,
            }} />
          )}
          {icon}
          <span style={{ flex: 1 }}>{label}</span>
        </>
      )}
    </NavLink>
  );
}

export function Sidebar() {
  const { user, logout, isManager, isAdmin } = useAuth();
  const { logo, theme, setTheme } = useTheme();
  const navigate = useNavigate();

  function handleSearch() {
    window.dispatchEvent(new CustomEvent('da:open-palette'));
  }

  function toggleTheme() {
    setTheme({ theme: theme === 'dark' ? 'light' : 'dark' });
  }

  return (
    <aside style={{
      width: 'var(--sidebar-w)',
      borderRight: '1px solid var(--line)',
      background: 'var(--bg)',
      flexShrink: 0,
      height: '100vh',
      position: 'sticky',
      top: 0,
      display: 'flex',
      flexDirection: 'column',
    }}>
      {/* Logo block */}
      <div style={{ padding: '18px 18px 14px', borderBottom: '1px solid var(--line-2)' }}>
        <Logo variant={logo} size={26} withWordmark />
        <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span className="t-label" style={{ fontSize: 10 }}>
            <span className="dot dot-live" style={{ marginRight: 6, verticalAlign: 'middle' }} />
            ALL SYSTEMS LIVE
          </span>
          <span className="kbd">{APP_VERSION}</span>
        </div>
      </div>

      {/* TODO(palette): wire full command palette here — tracked in docs/REDESIGN_FOLLOWUPS.md (PR6/T10.2) */}
      <div style={{ padding: '12px 14px 8px' }}>
        <button
          className="btn"
          onClick={handleSearch}
          aria-label="Open command palette"
          style={{
            width: '100%',
            justifyContent: 'flex-start',
            color: 'var(--fg-3)',
            fontFamily: 'var(--font-sans)',
            fontSize: 12.5,
            padding: '7px 10px',
          }}
        >
          <Search width={13} height={13} />
          <span style={{ flex: 1, textAlign: 'left' }}>Search…</span>
          <span className="kbd">⌘K</span>
        </button>
      </div>

      {/* Nav */}
      <nav style={{ padding: '4px 8px', flex: 1, overflowY: 'auto' }}>
        <div className="t-label" style={{ padding: '8px 10px 6px', fontSize: 9.5, color: 'var(--muted)' }}>
          ── workspace
        </div>
        <NavItem to="/dashboard" icon={<Home width={15} height={15} />} label="Personal" />
        {(isManager || isAdmin) && (
          <NavItem to="/team" icon={<Users width={15} height={15} />} label="Team" />
        )}
        {(isManager || isAdmin) && (
          <NavItem to="/team-manage" icon={<UserCog width={15} height={15} />} label="Manage" />
        )}
        <NavItem to="/datasources" icon={<Database width={15} height={15} />} label="Sources" />

        <div className="t-label" style={{ padding: '16px 10px 6px', fontSize: 9.5, color: 'var(--muted)' }}>
          ── account
        </div>
        <NavItem to="/settings" icon={<Settings width={15} height={15} />} label="Settings" />
        {isAdmin && (
          <NavItem to="/admin" icon={<Shield width={15} height={15} />} label="Admin" />
        )}
      </nav>

      {/* User card */}
      <div style={{ borderTop: '1px solid var(--line-2)', padding: 12 }}>
        {user && (
          <button
            onClick={() => navigate('/settings')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              width: '100%',
              padding: '8px 8px',
              background: 'var(--bg-2)',
              border: '1px solid var(--line-2)',
              borderRadius: 8,
              cursor: 'pointer',
              textAlign: 'left',
            }}
            title="Open profile & settings"
          >
            <Avatar user={user} size="sm" />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{
                fontSize: 12, fontWeight: 500, color: 'var(--fg)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {user.email}
              </div>
              <div className="t-label" style={{ fontSize: 10 }}>
                {user.role.toLowerCase()} · personal
              </div>
            </div>
            <ChevronRight width={12} height={12} style={{ color: 'var(--fg-3)', flexShrink: 0 }} />
          </button>
        )}
        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <button
            className="btn btn-sm"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            style={{ flex: 1, justifyContent: 'center' }}
          >
            {theme === 'dark' ? <Sun width={12} height={12} /> : <Moon width={12} height={12} />}
            <span>{theme === 'dark' ? 'light' : 'dark'}</span>
          </button>
          <button
            className="btn btn-sm btn-icon"
            onClick={() => logout()}
            title="Sign out"
            aria-label="Sign out"
          >
            <LogOut width={13} height={13} />
          </button>
        </div>
      </div>
    </aside>
  );
}
