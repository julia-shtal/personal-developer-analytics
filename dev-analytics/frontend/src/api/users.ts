import api from '@/lib/api';
import type { CommitEmailDto } from '@/types';

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
  /**
   * Addresses the user commits with. Commits are attributed by these or by the GitHub
   * account resolved for the commit, so local repositories depend entirely on this list.
   */
  commitEmails: {
    list: () => api.get<CommitEmailDto[]>('/users/me/commit-emails'),
    add: (email: string) => api.post<CommitEmailDto>('/users/me/commit-emails', { email }),
    remove: (id: number) => api.delete<void>(`/users/me/commit-emails/${id}`),
  },
  deleteAccount: () => api.delete<void>('/users/me'),
};
