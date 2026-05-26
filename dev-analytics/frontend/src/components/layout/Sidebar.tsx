import { NavLink } from 'react-router-dom';
import { Avatar } from '@/components/ui/Avatar';
import {
  LayoutDashboard,
  Users,
  UserCog,
  Database,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
} from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';

interface SidebarProps {
  collapsed: boolean;
  onToggle: () => void;
}

const navItemClass = ({ isActive }: { isActive: boolean }) =>
  clsx(
    'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150',
    isActive
      ? 'bg-violet-50 text-violet-700'
      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
  );

export function Sidebar({ collapsed, onToggle }: SidebarProps) {
  const { user, logout, isManager, isAdmin } = useAuth();
  const { logo } = useTheme();

  return (
    <aside
      className={clsx(
        'flex flex-col h-screen bg-white border-r border-gray-200 transition-all duration-300 flex-shrink-0',
        collapsed ? 'w-16' : 'w-60'
      )}
    >
      {/* Logo / brand */}
      <div className="flex items-center gap-3 px-4 h-16 border-b border-gray-100 flex-shrink-0">
        <Logo variant={logo} size={26} withWordmark={!collapsed} />
      </div>

      {/* Nav items */}
      <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
        <NavLink to="/dashboard" className={navItemClass} title="Dashboard">
          <LayoutDashboard className="h-4 w-4 flex-shrink-0" />
          {!collapsed && <span>Dashboard</span>}
        </NavLink>

        {(isManager || isAdmin) && (
          <NavLink to="/team" className={navItemClass} title="Team metrics">
            <Users className="h-4 w-4 flex-shrink-0" />
            {!collapsed && <span>Team metrics</span>}
          </NavLink>
        )}

        {(isManager || isAdmin) && (
          <NavLink to="/team-manage" className={navItemClass} title="Manage teams">
            <UserCog className="h-4 w-4 flex-shrink-0" />
            {!collapsed && <span>Manage teams</span>}
          </NavLink>
        )}

        <NavLink to="/datasources" className={navItemClass} title="Data Sources">
          <Database className="h-4 w-4 flex-shrink-0" />
          {!collapsed && <span>Data Sources</span>}
        </NavLink>

        <NavLink to="/settings" className={navItemClass} title="Settings">
          <Settings className="h-4 w-4 flex-shrink-0" />
          {!collapsed && <span>Settings</span>}
        </NavLink>

        {isAdmin && (
          <NavLink to="/admin" className={navItemClass} title="Admin">
            <ShieldAlert className="h-4 w-4 flex-shrink-0" />
            {!collapsed && <span>Admin</span>}
          </NavLink>
        )}
      </nav>

      {/* User + logout */}
      <div className="flex-shrink-0 border-t border-gray-100 p-2 space-y-1">
        {!collapsed && user && (
          <div className="px-3 py-2 flex items-center gap-2.5">
            <Avatar user={user} size="sm" />
            <div className="min-w-0">
              <p className="text-xs font-semibold text-gray-900 truncate">{user.username}</p>
              <p className="text-xs text-gray-400 truncate">{user.email}</p>
            </div>
          </div>
        )}
        <button
          onClick={() => logout()}
          className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-red-50 hover:text-red-600 transition-colors"
          title="Sign out"
        >
          <LogOut className="h-4 w-4 flex-shrink-0" />
          {!collapsed && <span>Sign out</span>}
        </button>

        {/* Collapse toggle */}
        <button
          onClick={onToggle}
          className="flex items-center gap-3 w-full px-3 py-2 rounded-lg text-xs text-gray-400 hover:bg-gray-100 transition-colors"
        >
          {collapsed
            ? <ChevronRight className="h-4 w-4" />
            : <><ChevronLeft className="h-4 w-4" /><span>Collapse</span></>
          }
        </button>
      </div>
    </aside>
  );
}
