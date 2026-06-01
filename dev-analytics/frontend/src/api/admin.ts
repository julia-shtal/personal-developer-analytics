import api from '@/lib/api';

export interface AdminStatsDto {
  activeUsers24h: number;
  databaseSizeBytes: number;
  aiCallsToday: number;
}

export interface InviteTokenDto {
  token: string;
  email: string;
  role: string;
  expiresAt: string;
  inviteUrl: string;
}

export interface InviteInfoDto {
  email: string;
  role: string;
  teamName: string | null;
}

export const adminApi = {
  stats: () => api.get<AdminStatsDto>('/admin/stats'),
  createInvite: (email: string, role: string, teamId?: number) =>
    api.post<InviteTokenDto>('/admin/invites', { email, role, teamId }),
  getInviteInfo: (token: string) =>
    api.get<InviteInfoDto>(`/auth/invite/${token}`),
};
