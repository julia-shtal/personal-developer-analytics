import api from '@/lib/api';
import type { DataSourceConfig, CreateDataSourceRequest, RepoDto, DiscoveredRepoDto, TrackedJiraProjectDto, DiscoveredProjectDto } from '@/types';

export const jiraProjectsApi = {
  listLinkedRepos: (jiraProjectId: number) =>
    api.get<RepoDto[]>(`/jira-projects/${jiraProjectId}/repositories`),
  linkRepo: (jiraProjectId: number, repoId: number) =>
    api.post<void>(`/jira-projects/${jiraProjectId}/repositories/${repoId}`, null),
  unlinkRepo: (jiraProjectId: number, repoId: number) =>
    api.delete(`/jira-projects/${jiraProjectId}/repositories/${repoId}`),
};

export interface UpdateDataSourceRequest {
  name?: string;
  baseUrl?: string;
  path?: string;
  apiToken?: string;
}

export interface CompletedPhase {
  name: string;
  itemsSaved: number;
  durationSeconds: number;
}

export interface SyncStatus {
  running: boolean;
  phaseNumber: number;
  totalPhases: number;
  phase: string;
  phaseProcessed: number;
  /** –1 means unknown */
  phaseTotal: number;
  totalProcessed: number;
  elapsedSeconds: number;
  /** null if ETA cannot be calculated */
  phaseEtaSeconds: number | null;
  overallEtaSeconds: number | null;
  completedPhases: CompletedPhase[];
  result: string | null;
  error: string | null;
}

export interface SyncJobSummaryDto {
  id: number;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED';
  phase: string | null;
  startedAt: string;     // ISO-8601 Instant
  completedAt: string | null;
  totalProcessed: number | null;
  error: string | null;
}

export const datasourcesApi = {
  list: () => api.get<DataSourceConfig[]>('/datasources'),
  get: (id: number) => api.get<DataSourceConfig>(`/datasources/${id}`),
  create: (req: CreateDataSourceRequest) => api.post<DataSourceConfig>('/datasources', req),
  update: (id: number, req: UpdateDataSourceRequest) =>
    api.put<DataSourceConfig>(`/datasources/${id}`, req),
  delete: (id: number) => api.delete(`/datasources/${id}`),
  collect: (id: number) => api.post<void>(`/datasources/${id}/collect`),
  collectStatus: (id: number) => api.get<SyncStatus>(`/datasources/${id}/collect/status`),
  activeCollectStatuses: () => api.get<Record<string, SyncStatus>>('/datasources/collect/status/active'),
  syncHistory: (id: number, limit = 5) =>
    api.get<SyncJobSummaryDto[]>(`/datasources/${id}/sync-history?limit=${limit}`),

  repos: {
    list: (dsId: number) =>
      api.get<RepoDto[]>(`/datasources/${dsId}/repos`),
    attach: (dsId: number, repoFullName: string, collectIssues = false) =>
      api.post<RepoDto>(`/datasources/${dsId}/repos`, { repoFullName, collectIssues }),
    detach: (dsId: number, repoId: number) =>
      api.delete(`/datasources/${dsId}/repos/${repoId}`),
    discover: (dsId: number) =>
      api.get<DiscoveredRepoDto[]>(`/datasources/${dsId}/repos/discover-repos`),
  },

  projects: {
    list: (dsId: number) =>
      api.get<TrackedJiraProjectDto[]>(`/datasources/${dsId}/projects`),
    attach: (dsId: number, projectKey: string, projectName?: string) =>
      api.post<TrackedJiraProjectDto>(`/datasources/${dsId}/projects`, { projectKey, projectName }),
    detach: (dsId: number, projectId: number) =>
      api.delete(`/datasources/${dsId}/projects/${projectId}`),
    discover: (dsId: number) =>
      api.get<DiscoveredProjectDto[]>(`/datasources/${dsId}/projects/discover-projects`),
  },
};
