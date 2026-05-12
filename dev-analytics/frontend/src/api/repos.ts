import api from '@/lib/api';
import type { RepoDto } from '@/types';

export const reposApi = {
  list: (dataSourceId?: number) =>
    api.get<RepoDto[]>('/repos', { params: dataSourceId ? { dataSourceId } : undefined }),
  subscribe: (repoId: number) => api.post(`/repos/${repoId}/subscribe`),
  unsubscribe: (repoId: number) => api.delete(`/repos/${repoId}/subscribe`),
  setCollectIssues: (repoId: number, enabled: boolean) =>
    api.patch<RepoDto>(`/repos/${repoId}/collect-issues`, { enabled }),
};
