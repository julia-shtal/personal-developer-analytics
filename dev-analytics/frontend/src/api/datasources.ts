import api from '@/lib/api';
import type { DataSourceConfig, CreateDataSourceRequest } from '@/types';

export const datasourcesApi = {
  list: () => api.get<DataSourceConfig[]>('/datasources'),
  get: (id: number) => api.get<DataSourceConfig>(`/datasources/${id}`),
  create: (req: CreateDataSourceRequest) => api.post<DataSourceConfig>('/datasources', req),
  delete: (id: number) => api.delete(`/datasources/${id}`),
};
