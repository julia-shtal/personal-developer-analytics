import api from '@/lib/api';
import type { DataSourceConfig, CreateDataSourceRequest } from '@/types';

export interface UpdateDataSourceRequest {
  name?: string;
  baseUrl?: string;
  path?: string;
  apiToken?: string;
}

export const datasourcesApi = {
  list: () => api.get<DataSourceConfig[]>('/datasources'),
  get: (id: number) => api.get<DataSourceConfig>(`/datasources/${id}`),
  create: (req: CreateDataSourceRequest) => api.post<DataSourceConfig>('/datasources', req),
  update: (id: number, req: UpdateDataSourceRequest) =>
    api.put<DataSourceConfig>(`/datasources/${id}`, req),
  delete: (id: number) => api.delete(`/datasources/${id}`),
  collect: (id: number) => api.post<string>(`/datasources/${id}/collect`),
};
