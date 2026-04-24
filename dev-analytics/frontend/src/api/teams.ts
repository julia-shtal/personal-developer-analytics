import api from '@/lib/api';
import type { Team } from '@/types';

export const teamsApi = {
  list: () => api.get<Team[]>('/teams'),
  get: (id: number) => api.get<Team>(`/teams/${id}`),
};
