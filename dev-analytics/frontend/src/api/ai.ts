import api from '@/lib/api';
import type { MetricsSummaryDto } from '@/types/ai';

export const aiApi = {
  generateSummary: (from: string, to: string, repoId?: number): Promise<MetricsSummaryDto> =>
    api.get<MetricsSummaryDto>('/ai/summary', { params: { from, to, repoId } }).then((r) => r.data),

  generateTeamSummary: (teamId: number, from: string, to: string): Promise<MetricsSummaryDto> =>
    api.get<MetricsSummaryDto>(`/ai/summary/teams/${teamId}`, { params: { from, to } }).then((r) => r.data),
};
