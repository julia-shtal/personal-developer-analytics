import api from '@/lib/api';
import type { Team } from '@/types';

export const teamsApi = {
  list: () => api.get<Team[]>('/teams'),
  get: (id: number) => api.get<Team>(`/teams/${id}`),
  create: (name: string) => api.post<Team>('/teams', { name }),
  addMember: (teamId: number, userId: number) =>
    api.post<Team>(`/teams/${teamId}/members`, { userId }),
  removeMember: (teamId: number, userId: number) =>
    api.delete<Team>(`/teams/${teamId}/members/${userId}`),
  rename: (teamId: number, name: string) => api.put<Team>(`/teams/${teamId}`, { name }),
  delete: (teamId: number) => api.delete<void>(`/teams/${teamId}`),
};
