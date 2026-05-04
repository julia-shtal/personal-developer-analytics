import api from '@/lib/api';
import type { DataSourceConfig, CreateDataSourceRequest } from '@/types';

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
};
