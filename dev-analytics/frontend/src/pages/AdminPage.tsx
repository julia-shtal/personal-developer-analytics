import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Navigate } from 'react-router-dom';
import { Shield, Mail, Plus, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Chip } from '@/components/ui/Chip';
import { Modal } from '@/components/ui/Modal';
import { Avatar } from '@/components/ui/Avatar';
import api from '@/lib/api';
import { adminApi } from '@/api/admin';
import type { UserProfile, Role } from '@/types';

const ROLE_CHIP: Record<Role, 'coral' | 'violet' | undefined> = {
  ADMIN:     'coral',
  MANAGER:   'violet',
  DEVELOPER: undefined,
};

const ROLES: Role[] = ['DEVELOPER', 'MANAGER', 'ADMIN'];

function RoleDropdown({ userId, currentRole }: { userId: number; currentRole: Role }) {
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: (role: Role) => api.put(`/admin/users/${userId}/role`, { role }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-users'] }); setOpen(false); },
  });
  return (
    <div style={{ position: 'relative' }}>
      <button className="btn btn-sm" onClick={() => setOpen((v) => !v)}>
        <Chip color={ROLE_CHIP[currentRole]}>{currentRole.toLowerCase()}</Chip>
      </button>
      {open && (
        <>
          <div style={{ position: 'fixed', inset: 0, zIndex: 10 }} onClick={() => setOpen(false)} />
          <div style={{
            position: 'absolute', right: 0, top: '100%', marginTop: 4,
            background: 'var(--bg-card)', border: '1px solid var(--line)',
            borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,.12)',
            zIndex: 20, minWidth: 140, padding: '4px 0',
          }}>
            {ROLES.map((role) => (
              <button
                key={role}
                onClick={() => mutation.mutate(role)}
                style={{
                  width: '100%', textAlign: 'left', padding: '8px 12px',
                  fontSize: 12.5, background: role === currentRole ? 'var(--bg-2)' : 'transparent',
                  color: role === currentRole ? 'var(--accent)' : 'var(--fg)',
                  border: 'none', cursor: 'pointer',
                }}
              >{role.toLowerCase()}</button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export function AdminPage() {
  const { isAdmin, user: currentUser } = useAuth();
  const qc = useQueryClient();

  const [inviteOpen, setInviteOpen] = useState(false);
  const [adminOpen, setAdminOpen] = useState(false);
  const [searchQ, setSearchQ] = useState('');
  const [selectedUser, setSelectedUser] = useState<UserProfile | null>(null);

  const { data: users = [], isLoading } = useQuery<UserProfile[]>({
    queryKey: ['admin-users'],
    queryFn: () => api.get<UserProfile[]>('/admin/users').then((r) => r.data),
    enabled: isAdmin,
  });

  const { data: stats } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => adminApi.stats().then((r) => r.data),
    enabled: isAdmin,
  });

  // B5.2: search query for promote-to-admin modal
  const { data: searchResults = [] } = useQuery<UserProfile[]>({
    queryKey: ['admin-users-search', searchQ],
    queryFn: () => {
      const params = searchQ.trim() ? `?q=${encodeURIComponent(searchQ.trim())}` : '';
      return api.get<UserProfile[]>(`/admin/users${params}`).then((r) => r.data);
    },
    enabled: isAdmin && adminOpen,
    staleTime: 5000,
  });

  const deleteUserMutation = useMutation({
    mutationFn: (userId: number) => api.delete(`/admin/users/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  const promoteMutation = useMutation({
    mutationFn: (userId: number) => api.put(`/admin/users/${userId}/role`, { role: 'ADMIN' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      qc.invalidateQueries({ queryKey: ['admin-users-search'] });
      setAdminOpen(false);
      setSelectedUser(null);
      setSearchQ('');
    },
  });

  const nonAdmins = searchResults.filter((u) => u.role !== 'ADMIN');

  if (!isAdmin) return <Navigate to="/dashboard" replace />;

  return (
    <div className="page fade-in">
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 28 }}>
        <div>
          <div className="t-eyebrow" style={{ marginBottom: 10 }}>── Admin</div>
          <h1 className="t-h1">
            <em>{users.length} users</em>.
          </h1>
        </div>
        <div className="row gap-2">
          <button className="btn" onClick={() => setInviteOpen(true)}>
            <Mail width={13} height={13} />invite
          </button>
          <button className="btn btn-accent" onClick={() => { setAdminOpen(true); setSearchQ(''); setSelectedUser(null); }}>
            <Plus width={13} height={13} />new admin
          </button>
        </div>
      </div>

      {/* KPI strip */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="grid-kpi">
          <div style={{ padding: '18px 20px' }}>
            <div className="t-eyebrow">users</div>
            <div className="t-number-big" style={{ marginTop: 6 }}>{isLoading ? '—' : users.length}</div>
          </div>
          <div style={{ padding: '18px 20px' }}>
            <div className="t-eyebrow">active 24h</div>
            <div className="t-number-big" style={{ marginTop: 6 }}>
              {stats == null ? '—' : stats.activeUsers24h}
            </div>
          </div>
          <div style={{ padding: '18px 20px' }}>
            <div className="t-eyebrow">db size</div>
            <div className="t-number-big" style={{ marginTop: 6 }}>
              {stats == null ? '—' : `${(stats.databaseSizeBytes / 1024 / 1024).toFixed(1)} MB`}
            </div>
          </div>
          <div style={{ padding: '18px 20px' }}>
            <div className="t-eyebrow">ai calls today</div>
            <div className="t-number-big" style={{ marginTop: 6 }}>
              {stats == null ? '—' : stats.aiCallsToday}
            </div>
          </div>
        </div>
      </div>

      {/* Users table */}
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="row" style={{ padding: '14px 20px' }}>
          <div className="t-h2" style={{ fontSize: 22 }}>Users</div>
          <div style={{ flex: 1 }} />
        </div>
        {isLoading ? (
          <div className="t-label" style={{ textAlign: 'center', padding: '32px 0' }}>Loading…</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>email</th>
                <th>role</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <span className="row gap-2" style={{ alignItems: 'center' }}>
                      <Avatar user={u} size="sm" />
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: 'var(--fg)' }}>
                        {u.email}
                        {u.id === currentUser?.id && (
                          <span className="t-label" style={{ marginLeft: 6 }}>(you)</span>
                        )}
                      </span>
                    </span>
                  </td>
                  <td>
                    <RoleDropdown userId={u.id} currentRole={u.role} />
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    {u.id !== currentUser?.id && (
                      <button
                        className="btn btn-sm btn-icon"
                        aria-label="Delete user"
                        onClick={() => {
                          if (confirm(`Delete user "${u.email}"? This cannot be undone.`)) {
                            deleteUserMutation.mutate(u.id);
                          }
                        }}
                        style={{ color: 'var(--fg-3)' }}
                      >
                        <Trash2 width={13} height={13} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div style={{ height: 32 }} />

      {/* Invite modal — TODO(admin-invites-backend): invite-by-email subsystem not yet implemented */}
      <Modal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        eyebrow="── admin"
        title="Invite people"
        width={540}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-sm" onClick={() => setInviteOpen(false)}>cancel</button>
            <button className="btn btn-sm btn-accent" onClick={() => setInviteOpen(false)} disabled>
              <Mail width={12} height={12} />send invites
            </button>
          </div>
        }
      >
        <div className="col gap-3">
          <div className="card-quiet" style={{ padding: '12px 14px', borderRadius: 6 }}>
            <div style={{ fontSize: 13, color: 'var(--amber)' }}>
              ── Invite subsystem coming soon
            </div>
            <div className="t-label" style={{ marginTop: 4 }}>
              Email invitations require a dedicated invite-token subsystem. This is a visual preview.
            </div>
          </div>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>emails</div>
            <textarea
              className="input"
              rows={4}
              placeholder={'alice@team.dev\nbob@team.dev\n…'}
              disabled
              style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, resize: 'vertical', opacity: 0.5 }}
            />
          </div>
        </div>
      </Modal>

      {/* Promote to admin modal (wired to B5.2 search) */}
      <Modal
        open={adminOpen}
        onClose={() => { setAdminOpen(false); setSelectedUser(null); setSearchQ(''); }}
        eyebrow="── admin"
        title="Promote to admin"
        width={500}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'space-between' }}>
            <span className="t-label" style={{ fontSize: 10.5, color: 'var(--coral)' }}>
              <Shield width={11} height={11} style={{ verticalAlign: 'middle', marginRight: 4 }} />
              admins can change roles, delete users, and access all teams
            </span>
            <button
              className="btn btn-sm btn-accent"
              disabled={!selectedUser || promoteMutation.isPending}
              onClick={() => selectedUser && promoteMutation.mutate(selectedUser.id)}
            >
              promote
            </button>
          </div>
        }
      >
        <div className="col gap-3">
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>search users</div>
            <input
              className="input"
              placeholder="type a name or email…"
              value={searchQ}
              onChange={(e) => setSearchQ(e.target.value)}
              autoFocus
            />
          </div>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 10 }}>existing users</div>
            <div className="col gap-1">
              {nonAdmins.slice(0, 6).map((u) => {
                const active = selectedUser?.id === u.id;
                return (
                  <button
                    key={u.id}
                    className="row gap-3"
                    onClick={() => setSelectedUser(active ? null : u)}
                    style={{
                      padding: '10px 12px', borderRadius: 6,
                      background: active ? 'var(--accent-bg)' : 'var(--bg-2)',
                      border: active ? '1px solid var(--accent)' : '1px solid var(--line-2)',
                      cursor: 'pointer', textAlign: 'left',
                    }}
                  >
                    <span className="avatar avatar-sm" style={{
                      background: 'var(--violet-bg)', color: 'var(--violet)', borderColor: 'transparent',
                    }}>
                      {(u.email ?? u.username ?? '?').slice(0, 2).toUpperCase()}
                    </span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: 'var(--fg)' }}>
                        {u.email}
                      </div>
                      <div className="t-label" style={{ fontSize: 10, marginTop: 1 }}>
                        current role · {u.role.toLowerCase()}
                      </div>
                    </div>
                    {active && <Chip color="violet">selected</Chip>}
                  </button>
                );
              })}
              {nonAdmins.length === 0 && (
                <div className="t-label" style={{ textAlign: 'center', padding: '16px 0' }}>
                  {searchQ ? 'No users match that search.' : 'No non-admin users found.'}
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
}
