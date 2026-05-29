import api from '@/lib/api';

export interface AdminStatsDto {
  activeUsers24h: number;
  databaseSizeBytes: number;
  aiCallsToday: number;
}

export const adminApi = {
  stats: () => api.get<AdminStatsDto>('/admin/stats'),
};
