import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ShieldAlert, Trash2, ChevronDown } from 'lucide-react';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { PageSpinner } from '@/components/ui/Spinner';
import { useAuth } from '@/context/AuthContext';
import { Navigate } from 'react-router-dom';
import api from '@/lib/api';
import type { UserProfile, Role } from '@/types';
import { useState } from 'react';

const ROLE_COLORS: Record<Role, 'violet' | 'blue' | 'amber'> = {
  DEVELOPER: 'violet',
  MANAGER: 'blue',
  ADMIN: 'amber',
};

const ROLES: Role[] = ['DEVELOPER', 'MANAGER', 'ADMIN'];

function RoleDropdown({
  userId,
  currentRole,
  onUpdate,
}: {
  userId: number;
  currentRole: Role;
  onUpdate: (userId: number, role: Role) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 px-2 py-1 rounded-lg hover:bg-gray-100 transition-colors"
      >
        <Badge color={ROLE_COLORS[currentRole]} dot>{currentRole}</Badge>
        <ChevronDown className="h-3 w-3 text-gray-400" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 mt-1 w-40 bg-white rounded-xl border border-gray-200 shadow-lg z-20 py-1">
            {ROLES.map((role) => (
              <button
                key={role}
                onClick={() => {
                  onUpdate(userId, role);
                  setOpen(false);
                }}
                className={`w-full text-left px-3 py-2 text-sm hover:bg-gray-50 transition-colors ${
                  role === currentRole ? 'text-violet-700 font-medium' : 'text-gray-700'
                }`}
              >
                {role}
              </button>
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

  if (!isAdmin) return <Navigate to="/dashboard" replace />;

  const { data: users, isLoading } = useQuery<UserProfile[]>({
    queryKey: ['admin-users'],
    queryFn: () => api.get<UserProfile[]>('/admin/users').then((r) => r.data),
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: Role }) =>
      api.put(`/admin/users/${userId}/role`, { role }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  const deleteUserMutation = useMutation({
    mutationFn: (userId: number) => api.delete(`/admin/users/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  if (isLoading) return <PageSpinner />;

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <div>
        <div className="flex items-center gap-2">
          <ShieldAlert className="h-5 w-5 text-amber-500" />
          <h1 className="text-xl font-semibold text-gray-900">Admin — User Management</h1>
        </div>
        <p className="text-sm text-gray-500 mt-0.5">Manage accounts and roles</p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="text-sm font-semibold text-gray-900">
            All users <span className="text-gray-400 font-normal ml-1">({users?.length ?? 0})</span>
          </h2>
        </CardHeader>
        <CardBody className="px-0 py-0">
          {!users?.length ? (
            <div className="py-12 text-center text-gray-400 text-sm">No users found.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="text-left px-5 py-3 font-medium text-gray-500 text-xs">User</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500 text-xs">Email</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500 text-xs">Role</th>
                    <th className="px-5 py-3" />
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-violet-100 flex items-center justify-center text-violet-700 text-xs font-semibold">
                            {u.username[0].toUpperCase()}
                          </div>
                          <span className="font-medium text-gray-900">{u.username}</span>
                          {u.id === currentUser?.id && (
                            <span className="text-xs text-gray-400">(you)</span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-500">{u.email}</td>
                      <td className="px-4 py-3">
                        <RoleDropdown
                          userId={u.id}
                          currentRole={u.role}
                          onUpdate={(userId, role) => updateRoleMutation.mutate({ userId, role })}
                        />
                      </td>
                      <td className="px-5 py-3 text-right">
                        {u.id !== currentUser?.id && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              if (confirm(`Delete user "${u.username}"? This cannot be undone.`)) {
                                deleteUserMutation.mutate(u.id);
                              }
                            }}
                            loading={deleteUserMutation.isPending}
                            className="text-gray-400 hover:text-red-600"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
