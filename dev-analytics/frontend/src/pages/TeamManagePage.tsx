import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, X, Mail, Sparkles, Code, Trash2, ChevronRight, AlertCircle } from 'lucide-react';
import { teamsApi } from '@/api/teams';
import { Modal } from '@/components/ui/Modal';
import { Chip } from '@/components/ui/Chip';
import { Avatar } from '@/components/ui/Avatar';
import { PageSpinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import { Navigate } from 'react-router-dom';
import api from '@/lib/api';
import type { Team, UserProfile } from '@/types';

function errMsg(err: unknown, fallback: string) {
  return (
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
  );
}

type ModalMode = 'members' | 'config' | 'more' | 'new' | 'delete-confirm' | null;

const ACCENT_COLORS = ['violet', 'cyan', 'amber', 'emerald', 'coral'] as const;

export function TeamManagePage() {
  const { isManager, isAdmin } = useAuth();
  const qc = useQueryClient();

  // All hooks must be declared before any conditional return
  const [activeTeamId, setActiveTeamId] = useState<number | null>(null);
  const [mode, setMode] = useState<ModalMode>(null);
  const [deleteConfirmName, setDeleteConfirmName] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [addSearch, setAddSearch] = useState('');
  const [addError, setAddError] = useState('');
  const [newTeamName, setNewTeamName] = useState('');
  const [newTeamDesc, setNewTeamDesc] = useState('');
  const [createError, setCreateError] = useState('');
  const [actionError, setActionError] = useState('');

  const { data: teams, isLoading: teamsLoading } = useQuery<Team[]>({
    queryKey: ['teams'],
    queryFn: () => teamsApi.list().then((r) => r.data),
    enabled: isManager || isAdmin,
  });

  const activeTeam = teams?.find((t) => t.id === activeTeamId) ?? null;

  const { data: allUsers = [] } = useQuery<UserProfile[]>({
    queryKey: ['all-users'],
    queryFn: () => api.get<UserProfile[]>('/users').then((r) => r.data),
    enabled: isManager || isAdmin,
  });

  const createMutation = useMutation({
    mutationFn: (name: string) => teamsApi.create(name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      setNewTeamName('');
      setNewTeamDesc('');
      setCreateError('');
      close();
    },
    onError: (err) => setCreateError(errMsg(err, 'Failed to create team.')),
  });

  const addMutation = useMutation({
    mutationFn: (userId: number) => teamsApi.addMember(activeTeam!.id, userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      setAddSearch('');
      setAddError('');
    },
    onError: (err) => setAddError(errMsg(err, 'Failed to add member.')),
  });

  const removeMutation = useMutation({
    mutationFn: ({ teamId, userId }: { teamId: number; userId: number }) =>
      teamsApi.removeMember(teamId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['teams'] }),
    onError: (err) => setActionError(errMsg(err, 'Failed to remove member.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (teamId: number) => teamsApi.delete(teamId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      close();
    },
    onError: (err) => setDeleteError(errMsg(err, 'Failed to delete team.')),
  });

  // Auth guard — after all hooks
  if (!isManager && !isAdmin) return <Navigate to="/dashboard" replace />;
  if (teamsLoading) return <PageSpinner />;

  function open(team: Team | null, m: ModalMode) {
    setActiveTeamId(team?.id ?? null);
    setMode(m);
    setDeleteConfirmName('');
    setDeleteError('');
    setAddSearch('');
    setAddError('');
    setActionError('');
  }

  function close() {
    setMode(null);
    setActiveTeamId(null);
    setDeleteConfirmName('');
    setDeleteError('');
    setAddSearch('');
    setAddError('');
    setActionError('');
  }

  const totalMembers = teams?.reduce((sum, t) => sum + (t.members?.length ?? 0), 0) ?? 0;

  const addCandidates = allUsers.filter(
    (u) =>
      !(activeTeam?.members ?? []).some((m) => m.id === u.id) &&
      (u.username.toLowerCase().includes(addSearch.toLowerCase()) ||
        u.email.toLowerCase().includes(addSearch.toLowerCase()))
  );

  const isOpen = mode !== null;

  return (
    <div className="page fade-in">
      {/* Hero */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 28 }}>
        <div>
          <div className="t-eyebrow" style={{ marginBottom: 10 }}>── Manage</div>
          <h1 className="t-h1">
            <em>{teams?.length ?? 0} team{teams?.length !== 1 ? 's' : ''}</em>, <em>{totalMembers} member{totalMembers !== 1 ? 's' : ''}</em>.
          </h1>
        </div>
        <button className="btn btn-accent" onClick={() => open(null, 'new')} aria-label="Create new team">
          <Plus width={13} height={13} />new team
        </button>
      </div>

      {/* Team cards */}
      <div className="col gap-3">
        {teams?.length === 0 && (
          <div style={{ textAlign: 'center', padding: '64px 0', color: 'var(--fg-3)' }}>
            <p className="t-muted">No teams yet. Create one to get started.</p>
          </div>
        )}

        {teams?.map((team) => {
          const members = team.members ?? [];
          return (
            <div key={team.id} className="card" style={{ padding: 18 }}>
              <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
                <div className="row gap-3">
                  <div
                    className="avatar avatar-lg"
                    style={{ background: 'var(--violet-bg)', color: 'var(--violet)', borderColor: 'transparent' }}
                  >
                    {team.name[0].toUpperCase()}
                  </div>
                  <div>
                    <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, fontSize: 16, letterSpacing: '-0.02em' }}>
                      {team.name}
                    </div>
                    <div className="t-label" style={{ marginTop: 4 }}>
                      {members.length} member{members.length !== 1 ? 's' : ''}
                    </div>
                  </div>
                </div>
                <div className="row gap-2">
                  <button className="btn btn-sm" onClick={() => open(team, 'members')} aria-label={`Manage members of ${team.name}`}>
                    members
                  </button>
                  <button className="btn btn-sm" onClick={() => open(team, 'config')} aria-label={`Configure ${team.name}`}>
                    config
                  </button>
                  <button className="btn btn-sm btn-icon" title="More actions" onClick={() => open(team, 'more')} aria-label="More actions">
                    <ChevronRight width={13} height={13} />
                  </button>
                </div>
              </div>

              {members.length > 0 && (
                <>
                  <div className="divider-2" style={{ margin: '14px 0' }} />
                  <div className="row gap-3" style={{ flexWrap: 'wrap' }}>
                    {members.map((m, i) => (
                      <span
                        key={m.id}
                        style={{ outline: `2px solid var(--${ACCENT_COLORS[i % ACCENT_COLORS.length]})`, outlineOffset: 1, borderRadius: '50%', display: 'inline-flex' }}
                      >
                        <Avatar
                          user={{ id: m.id, username: m.username, hasCustomAvatar: m.hasCustomAvatar ?? false, avatarPreset: m.avatarPreset }}
                          size="sm"
                        />
                      </span>
                    ))}
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>

      <div style={{ height: 32 }} />

      {/* ── Members modal ── */}
      <Modal
        open={isOpen && mode === 'members'}
        onClose={close}
        eyebrow={`── team · ${activeTeam?.name}`}
        title="Members"
        width={580}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'space-between' }}>
            {/* TODO(admin-invites-backend): invite by email needs substantial subsystem */}
            <button className="btn btn-sm" title="Coming soon" aria-label="Invite by email (coming soon)">
              <Mail width={12} height={12} />invite by email
            </button>
          </div>
        }
      >
        {/* Search + add */}
        <div style={{ marginBottom: 14 }}>
          <input
            className="input"
            placeholder="Search users to add…"
            value={addSearch}
            onChange={(e) => setAddSearch(e.target.value)}
            aria-label="Search users to add"
          />
          {addSearch && (
            <div style={{
              marginTop: 4, border: '1px solid var(--line)', borderRadius: 8,
              background: 'var(--bg-card)', overflow: 'hidden', maxHeight: 180, overflowY: 'auto',
            }}>
              {addCandidates.length === 0 ? (
                <p style={{ padding: '10px 14px', fontSize: 13, color: 'var(--fg-3)' }}>No users found.</p>
              ) : addCandidates.map((u) => (
                <div key={u.id} className="row gap-3" style={{ padding: '8px 14px', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, fontWeight: 500 }}>{u.username}</div>
                    <div className="t-label" style={{ fontSize: 10 }}>{u.email}</div>
                  </div>
                  <button
                    className="btn btn-sm btn-accent"
                    onClick={() => addMutation.mutate(u.id)}
                    disabled={addMutation.isPending}
                    aria-label={`Add ${u.username}`}
                  >
                    <Plus width={11} height={11} />add
                  </button>
                </div>
              ))}
            </div>
          )}
          {addError && (
            <div className="row gap-2" style={{ marginTop: 8, color: 'var(--coral)', fontSize: 13 }}>
              <AlertCircle width={13} height={13} />{addError}
            </div>
          )}
        </div>

        {/* Member list */}
        <div className="col gap-1">
          {(activeTeam?.members ?? []).length === 0 ? (
            <p style={{ padding: '16px 0', textAlign: 'center', fontSize: 13, color: 'var(--fg-3)' }}>
              No members yet.
            </p>
          ) : (activeTeam?.members ?? []).map((member, i) => (
            <div
              key={member.id}
              className="row gap-3"
              style={{
                padding: '10px 12px', borderRadius: 6,
                background: i === 0 ? 'var(--bg-2)' : 'transparent',
              }}
            >
              <Avatar
                user={{ id: member.id, username: member.username, hasCustomAvatar: member.hasCustomAvatar ?? false, avatarPreset: member.avatarPreset }}
                size="sm"
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: 'var(--fg)', fontWeight: 500 }}>
                  {member.username}
                </div>
                <div className="t-label" style={{ fontSize: 10, marginTop: 1 }}>{member.email}</div>
              </div>
              {member.role === 'MANAGER' || member.role === 'ADMIN' ? (
                <Chip color="violet">{member.role.toLowerCase()}</Chip>
              ) : null}
              <button
                className="btn btn-sm btn-icon"
                title={`Remove ${member.username} from team`}
                aria-label={`Remove ${member.username}`}
                onClick={() => removeMutation.mutate({ teamId: activeTeam!.id, userId: member.id })}
                disabled={removeMutation.isPending}
              >
                <X width={12} height={12} />
              </button>
            </div>
          ))}
        </div>

        {actionError && (
          <div className="row gap-2" style={{ marginTop: 10, color: 'var(--coral)', fontSize: 13 }}>
            <AlertCircle width={13} height={13} />{actionError}
          </div>
        )}
      </Modal>

      {/* ── Config modal ── (TODO: team-config-backend — needs visibility/sources/brief schema, separate ticket) */}
      <Modal
        open={isOpen && mode === 'config'}
        onClose={close}
        eyebrow={`── team · ${activeTeam?.name}`}
        title="Configuration"
        width={520}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-sm" onClick={close}>cancel</button>
            <button className="btn btn-sm btn-accent" disabled title="Coming soon">save changes</button>
          </div>
        }
      >
        <div
          style={{
            background: 'var(--bg-2)', border: '1px solid var(--line)', borderRadius: 8,
            padding: '10px 14px', marginBottom: 20,
          }}
        >
          <span className="t-label" style={{ fontSize: 11 }}>
            Settings preview — visibility, default sources, and weekly brief require a schema migration.
            Coming in a future sprint.
          </span>
        </div>
        <div className="col gap-4" style={{ opacity: 0.4, pointerEvents: 'none' }}>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>team name</div>
            <input className="input" defaultValue={activeTeam?.name} readOnly />
          </div>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>visibility</div>
            <div className="row gap-2">
              {['private', 'workspace', 'public'].map((v) => (
                <button key={v} className="btn btn-sm" style={{ flex: 1, justifyContent: 'center' }}>{v}</button>
              ))}
            </div>
          </div>
          <div>
            <div className="t-eyebrow" style={{ marginBottom: 6 }}>weekly AI brief</div>
            <input className="input" defaultValue="every Monday 09:00" readOnly />
          </div>
        </div>
      </Modal>

      {/* ── More menu ── */}
      <Modal
        open={isOpen && mode === 'more'}
        onClose={close}
        eyebrow={`── team · ${activeTeam?.name}`}
        title="Actions"
        width={420}
      >
        <div className="col gap-1">
          {/* TODO(team-export): export CSV needs team metrics aggregation endpoint */}
          <button
            className="btn"
            style={{ width: '100%', justifyContent: 'flex-start', padding: '10px 12px' }}
            onClick={() => { close(); alert('Export coming soon'); }}
            aria-label="Export team report (CSV)"
          >
            <Code width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Export team report (CSV)</span>
            <ChevronRight width={11} height={11} style={{ color: 'var(--fg-3)' }} />
          </button>

          <button
            className="btn"
            style={{ width: '100%', justifyContent: 'flex-start', padding: '10px 12px' }}
            onClick={() => { close(); alert('Generate AI summary coming soon'); }}
            aria-label="Generate AI summary"
          >
            <Sparkles width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Generate AI summary</span>
            <ChevronRight width={11} height={11} style={{ color: 'var(--fg-3)' }} />
          </button>

          {/* TODO(team-duplicate): team duplication needs schema/endpoint */}
          <button
            className="btn"
            style={{ width: '100%', justifyContent: 'flex-start', padding: '10px 12px' }}
            onClick={() => { close(); alert('Duplicate coming soon'); }}
            aria-label="Duplicate team"
          >
            <Plus width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Duplicate team</span>
            <ChevronRight width={11} height={11} style={{ color: 'var(--fg-3)' }} />
          </button>

          {/* TODO(team-archive): archive needs a flag in schema */}
          <button
            className="btn"
            style={{ width: '100%', justifyContent: 'flex-start', padding: '10px 12px', color: 'var(--coral)' }}
            onClick={() => { close(); alert('Archive coming soon'); }}
            aria-label="Archive team"
          >
            <Trash2 width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Archive team</span>
            <ChevronRight width={11} height={11} style={{ color: 'var(--fg-3)' }} />
          </button>

          <button
            className="btn"
            style={{ width: '100%', justifyContent: 'flex-start', padding: '10px 12px', color: 'var(--coral)' }}
            onClick={() => { setMode('delete-confirm'); }}
            aria-label="Delete team permanently"
          >
            <Trash2 width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Delete team permanently</span>
            <ChevronRight width={11} height={11} style={{ color: 'var(--fg-3)' }} />
          </button>
        </div>
      </Modal>

      {/* ── Delete confirm modal ── */}
      <Modal
        open={isOpen && mode === 'delete-confirm'}
        onClose={close}
        eyebrow="── destructive action"
        title="Delete team permanently"
        width={460}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-sm" onClick={close}>cancel</button>
            <button
              className="btn btn-sm"
              style={{
                background: deleteConfirmName === activeTeam?.name ? 'var(--coral)' : 'var(--bg-inset)',
                color: deleteConfirmName === activeTeam?.name ? 'white' : 'var(--fg-3)',
                borderColor: 'var(--coral)',
              }}
              disabled={deleteConfirmName !== activeTeam?.name || deleteMutation.isPending}
              onClick={() => activeTeam && deleteMutation.mutate(activeTeam.id)}
              aria-label="Confirm delete"
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete permanently'}
            </button>
          </div>
        }
      >
        <p className="t-body" style={{ marginBottom: 16 }}>
          This will permanently delete <strong>{activeTeam?.name}</strong> and all its member associations.
          Data sources must be removed first. This cannot be undone.
        </p>
        <div className="t-eyebrow" style={{ marginBottom: 6 }}>
          Type <strong style={{ fontFamily: 'var(--font-mono)' }}>{activeTeam?.name}</strong> to confirm
        </div>
        <input
          className="input"
          placeholder={activeTeam?.name}
          value={deleteConfirmName}
          onChange={(e) => { setDeleteConfirmName(e.target.value); setDeleteError(''); }}
          autoFocus
          aria-label="Type team name to confirm deletion"
        />
        {deleteError && (
          <div className="row gap-2" style={{ marginTop: 10, color: 'var(--coral)', fontSize: 13 }}>
            <AlertCircle width={13} height={13} />{deleteError}
          </div>
        )}
      </Modal>

      {/* ── New team modal ── */}
      <Modal
        open={isOpen && mode === 'new'}
        onClose={close}
        eyebrow="── new team"
        title="Create a team"
        width={500}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-sm" onClick={close}>cancel</button>
            <button
              className="btn btn-sm btn-accent"
              disabled={!newTeamName.trim() || createMutation.isPending}
              onClick={() => {
                if (newTeamName.trim()) {
                  setCreateError('');
                  createMutation.mutate(newTeamName.trim());
                }
              }}
              aria-label="Create team"
            >
              {createMutation.isPending ? 'Creating…' : 'create team'}
            </button>
          </div>
        }
      >
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            if (newTeamName.trim()) {
              setCreateError('');
              createMutation.mutate(newTeamName.trim());
            }
          }}
        >
          <div className="col gap-4">
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>name</div>
              <input
                className="input"
                placeholder="e.g. infrastructure"
                value={newTeamName}
                onChange={(e) => setNewTeamName(e.target.value)}
                autoFocus
                required
                aria-label="Team name"
              />
            </div>
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>
                description <span className="t-label" style={{ fontSize: 10 }}>· optional</span>
              </div>
              <input
                className="input"
                placeholder="what does this team build?"
                value={newTeamDesc}
                onChange={(e) => setNewTeamDesc(e.target.value)}
                aria-label="Team description (optional)"
              />
            </div>
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>
                add members <span className="t-label" style={{ fontSize: 10 }}>· later</span>
              </div>
              <input className="input" placeholder="search by username or email…" disabled aria-label="Add members (available after creation)" />
            </div>
          </div>
          {createError && (
            <div className="row gap-2" style={{ marginTop: 10, color: 'var(--coral)', fontSize: 13 }}>
              <AlertCircle width={13} height={13} />{createError}
            </div>
          )}
        </form>
      </Modal>
    </div>
  );
}
