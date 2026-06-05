import api from '@/lib/api';
import type { Team, TeamMembership } from '@/types';

export const teamsApi = {
  list: () => api.get<Team[]>('/teams'),
  myMemberships: () => api.get<TeamMembership[]>('/teams/me/memberships'),
  get: (id: number) => api.get<Team>(`/teams/${id}`),
  create: (name: string) => api.post<Team>('/teams', { name }),
  addMember: (teamId: number, userId: number) =>
    api.post<Team>(`/teams/${teamId}/members`, { userId }),
  removeMember: (teamId: number, userId: number) =>
    api.delete<Team>(`/teams/${teamId}/members/${userId}`),
  rename: (teamId: number, name: string) => api.put<Team>(`/teams/${teamId}`, { name }),
  delete: (teamId: number) => api.delete<void>(`/teams/${teamId}`),
  archive: (teamId: number) => api.patch<Team>(`/teams/${teamId}/archive`),
  updateConfig: (teamId: number, cfg: { visibility: string; aiBriefSchedule: string }) =>
    api.put<Team>(`/teams/${teamId}/config`, cfg),
  duplicate: (teamId: number) => api.post<Team>(`/teams/${teamId}/duplicate`),
  exportCsv: (teamId: number, from: string, to: string) =>
    api.get(`/teams/${teamId}/export`, { params: { from, to }, responseType: 'blob' }),
};
