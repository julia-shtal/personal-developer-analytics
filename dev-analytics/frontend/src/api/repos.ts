import api from '@/lib/api';
import type { RepoDto } from '@/types';

export const reposApi = {
  list: (dataSourceId?: number, teamId?: number) =>
    api.get<RepoDto[]>('/repos', {
      params: {
        ...(dataSourceId != null && { dataSourceId }),
        ...(teamId != null && { teamId }),
      },
    }),
  subscribe: (repoId: number) => api.post(`/repos/${repoId}/subscribe`),
  unsubscribe: (repoId: number) => api.delete(`/repos/${repoId}/subscribe`),
  setCollectIssues: (repoId: number, enabled: boolean) =>
    api.patch<RepoDto>(`/repos/${repoId}/collect-issues`, { enabled }),
};
