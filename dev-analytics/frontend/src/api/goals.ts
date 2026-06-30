import api from '@/lib/api';

export interface GoalDto {
  id: number;
  metricType: string;
  targetValue: number;
  targetDate: string;  // ISO date string: "2026-07-31"
  createdAt: string;
}

export interface GoalRequestDto {
  metricType: string;
  targetValue: number;
  targetDate: string;
}

export const goalsApi = {
  list: () => api.get<GoalDto[]>('/goals').then((r) => r.data),
  create: (req: GoalRequestDto) => api.post<GoalDto>('/goals', req).then((r) => r.data),
  remove: (id: number) => api.delete(`/goals/${id}`),
};
