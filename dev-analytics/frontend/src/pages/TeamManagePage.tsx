import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Users,
  Plus,
  Trash2,
  UserPlus,
  X,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Pencil,
  Check,
  X as XIcon,
} from 'lucide-react';
import { teamsApi } from '@/api/teams';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { PageSpinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import { Navigate } from 'react-router-dom';
import api from '@/lib/api';
import type { Team, UserProfile } from '@/types';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function errMsg(err: unknown, fallback: string) {
  return (
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
  );
}

// ─── Add-member modal ────────────────────────────────────────────────────────

interface AddMemberModalProps {
  teamId: number;
  currentMemberIds: number[];
  allUsers: UserProfile[];
  onClose: () => void;
}

function AddMemberModal({ teamId, currentMemberIds, allUsers, onClose }: AddMemberModalProps) {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');

  const candidates = allUsers.filter(
    (u) =>
      !currentMemberIds.includes(u.id) &&
      (u.username.toLowerCase().includes(search.toLowerCase()) ||
        u.email.toLowerCase().includes(search.toLowerCase()))
  );

  const addMutation = useMutation({
    mutationFn: (userId: number) => teamsApi.addMember(teamId, userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      onClose();
    },
    onError: (err) => setError(errMsg(err, 'Failed to add member.')),
  });

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center p-4 bg-black/30 backdrop-blur-sm">
      <div className="w-full max-w-md bg-white rounded-2xl border border-gray-200 shadow-2xl">
        <div className="flex items-center justify-between px-5 pt-5 pb-3 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <UserPlus className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">Add member</h2>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-gray-100 transition-colors">
            <X className="h-4 w-4 text-gray-500" />
          </button>
        </div>

        <div className="px-5 py-4 space-y-3">
          <Input
            placeholder="Search by username or email…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <div className="max-h-60 overflow-y-auto space-y-1 rounded-lg border border-gray-100">
            {candidates.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-6">
                {allUsers.length === 0
                  ? 'No users available.'
                  : 'All users are already members, or no matches.'}
              </p>
            ) : (
              candidates.map((u) => (
                <div
                  key={u.id}
                  className="flex items-center justify-between px-3 py-2.5 hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-7 h-7 rounded-full bg-violet-100 flex items-center justify-center text-violet-700 text-xs font-semibold flex-shrink-0">
                      {u.username[0].toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-gray-900">{u.username}</p>
                      <p className="text-xs text-gray-400 truncate">{u.email}</p>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => addMutation.mutate(u.id)}
                    loading={addMutation.isPending}
                  >
                    Add
                  </Button>
                </div>
              ))
            )}
          </div>

          {error && (
            <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
              <AlertCircle className="h-4 w-4 flex-shrink-0" />
              {error}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Team card ────────────────────────────────────────────────────────────────

interface TeamCardProps {
  team: Team;
  allUsers: UserProfile[];
}

function TeamCard({ team, allUsers }: TeamCardProps) {
  const qc = useQueryClient();
  const [expanded, setExpanded] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [removeError, setRemoveError] = useState('');
  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState(team.name);

  const removeMutation = useMutation({
    mutationFn: (userId: number) => teamsApi.removeMember(team.id, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['teams'] }),
    onError: (err) => setRemoveError(errMsg(err, 'Failed to remove member.')),
  });

  const renameMutation = useMutation({
    mutationFn: (name: string) => teamsApi.rename(team.id, name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      setEditingName(false);
    },
    onError: (err) => setRemoveError(errMsg(err, 'Failed to rename team.')),
  });

  const currentMemberIds = (team.members ?? []).map((m) => m.id);

  return (
    <>
      <Card>
        {/* Team header */}
        <button
          className="w-full text-left px-5 py-4 flex items-center justify-between group"
          onClick={() => setExpanded((v) => !v)}
        >
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-violet-100 flex items-center justify-center flex-shrink-0">
              <Users className="h-4 w-4 text-violet-600" />
            </div>
            <div>
              {editingName ? (
                <form
                  className="flex items-center gap-1"
                  onSubmit={(e) => { e.preventDefault(); renameMutation.mutate(nameValue.trim()); }}
                >
                  <input
                    autoFocus
                    value={nameValue}
                    onChange={(e) => setNameValue(e.target.value)}
                    onClick={(e) => e.stopPropagation()}
                    className="text-sm font-semibold text-gray-900 border-b border-violet-400 bg-transparent outline-none px-0.5 w-36"
                  />
                  <button type="submit" onClick={(e) => e.stopPropagation()}
                    className="p-0.5 rounded hover:bg-violet-100 text-violet-600">
                    <Check className="h-3.5 w-3.5" />
                  </button>
                  <button type="button" onClick={(e) => { e.stopPropagation(); setEditingName(false); setNameValue(team.name); }}
                    className="p-0.5 rounded hover:bg-gray-100 text-gray-400">
                    <XIcon className="h-3.5 w-3.5" />
                  </button>
                </form>
              ) : (
                <div className="flex items-center gap-1 group/name">
                  <p className="text-sm font-semibold text-gray-900">{team.name}</p>
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); setEditingName(true); setNameValue(team.name); }}
                    className="opacity-0 group-hover/name:opacity-100 p-0.5 rounded hover:bg-gray-100 text-gray-400 transition-opacity"
                  >
                    <Pencil className="h-3 w-3" />
                  </button>
                </div>
              )}
              <p className="text-xs text-gray-400">
                {team.members?.length ?? 0} member{(team.members?.length ?? 0) !== 1 ? 's' : ''}
              </p>
            </div>
          </div>
          {expanded
            ? <ChevronUp className="h-4 w-4 text-gray-400 group-hover:text-gray-600" />
            : <ChevronDown className="h-4 w-4 text-gray-400 group-hover:text-gray-600" />
          }
        </button>

        {expanded && (
          <div className="border-t border-gray-100">
            {/* Member list */}
            {(team.members?.length ?? 0) === 0 ? (
              <div className="px-5 py-6 text-center text-gray-400 text-sm">
                No members yet — add someone below.
              </div>
            ) : (
              <ul className="divide-y divide-gray-50">
                {team.members!.map((member) => (
                  <li key={member.id} className="flex items-center justify-between px-5 py-3">
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 rounded-full bg-violet-100 flex items-center justify-center text-violet-700 text-xs font-semibold flex-shrink-0">
                        {member.username[0].toUpperCase()}
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{member.username}</p>
                        <p className="text-xs text-gray-400">{member.email}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge color={member.role === 'ADMIN' ? 'amber' : member.role === 'MANAGER' ? 'blue' : 'violet'}>
                        {member.role}
                      </Badge>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          if (confirm(`Remove ${member.username} from ${team.name}?`)) {
                            removeMutation.mutate(member.id);
                          }
                        }}
                        loading={removeMutation.isPending}
                        className="text-gray-400 hover:text-red-600"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}

            {removeError && (
              <div className="mx-5 mb-3 flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                {removeError}
              </div>
            )}

            {/* Add member button */}
            <div className="px-5 py-3 border-t border-gray-50">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowAddModal(true)}
              >
                <UserPlus className="h-3.5 w-3.5" />
                Add member
              </Button>
            </div>
          </div>
        )}
      </Card>

      {showAddModal && (
        <AddMemberModal
          teamId={team.id}
          currentMemberIds={currentMemberIds}
          allUsers={allUsers}
          onClose={() => setShowAddModal(false)}
        />
      )}
    </>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function TeamManagePage() {
  const { isManager, isAdmin } = useAuth();
  const qc = useQueryClient();

  if (!isManager && !isAdmin) return <Navigate to="/dashboard" replace />;

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newTeamName, setNewTeamName] = useState('');
  const [createError, setCreateError] = useState('');

  const { data: teams, isLoading: teamsLoading } = useQuery<Team[]>({
    queryKey: ['teams'],
    queryFn: () => teamsApi.list().then((r) => r.data),
  });

  // All users — for the member-add picker (endpoint accessible to MANAGER+ADMIN)
  const { data: allUsers = [] } = useQuery<UserProfile[]>({
    queryKey: ['all-users'],
    queryFn: () => api.get<UserProfile[]>('/users').then((r) => r.data),
  });

  const createMutation = useMutation({
    mutationFn: (name: string) => teamsApi.create(name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['teams'] });
      setNewTeamName('');
      setShowCreateForm(false);
      setCreateError('');
    },
    onError: (err) => setCreateError(errMsg(err, 'Failed to create team.')),
  });

  function handleCreate(e: FormEvent) {
    e.preventDefault();
    if (!newTeamName.trim()) return;
    setCreateError('');
    createMutation.mutate(newTeamName.trim());
  }

  if (teamsLoading) return <PageSpinner />;

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Team Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">Create teams and manage members</p>
        </div>
        <Button size="sm" onClick={() => setShowCreateForm((v) => !v)}>
          <Plus className="h-4 w-4" />
          New team
        </Button>
      </div>

      {/* Create team form */}
      {showCreateForm && (
        <Card>
          <CardHeader>
            <h2 className="text-sm font-semibold text-gray-900">Create team</h2>
          </CardHeader>
          <CardBody>
            <form onSubmit={handleCreate} className="flex gap-2 items-end">
              <div className="flex-1">
                <Input
                  label="Team name"
                  value={newTeamName}
                  onChange={(e) => setNewTeamName(e.target.value)}
                  placeholder="e.g. Platform Team"
                  required
                />
              </div>
              <Button type="submit" loading={createMutation.isPending}>
                Create
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setShowCreateForm(false);
                  setNewTeamName('');
                  setCreateError('');
                }}
              >
                Cancel
              </Button>
            </form>
            {createError && (
              <div className="mt-3 flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                {createError}
              </div>
            )}
          </CardBody>
        </Card>
      )}

      {/* Team list */}
      {teams?.length === 0 && !showCreateForm && (
        <div className="text-center py-20 text-gray-400">
          <Users className="h-10 w-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No teams yet. Create one to get started.</p>
        </div>
      )}

      <div className="space-y-3">
        {teams?.map((team) => (
          <TeamCard key={team.id} team={team} allUsers={allUsers} />
        ))}
      </div>
    </div>
  );
}
