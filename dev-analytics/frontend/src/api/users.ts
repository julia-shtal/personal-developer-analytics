import api from '@/lib/api';

export interface NotificationPrefsDto {
  aiBrief: boolean;
  syncFailures: boolean;
  afterHours: boolean;
  newTeamMember: boolean;
  defaultContactMethod: 'IN_APP' | 'EMAIL';
}

export const usersApi = {
  notifications: {
    get: () => api.get<NotificationPrefsDto>('/users/me/notifications'),
    update: (dto: NotificationPrefsDto) =>
      api.put<NotificationPrefsDto>('/users/me/notifications', dto),
  },
  deleteAccount: () => api.delete<void>('/users/me'),
};
