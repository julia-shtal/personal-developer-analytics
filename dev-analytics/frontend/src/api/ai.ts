import api from '@/lib/api';
import type { ConversationDto, MessageDto, MetricsSummaryDto } from '@/types/ai';

export const aiApi = {
  generateSummary: (from: string, to: string, repoId?: number): Promise<MetricsSummaryDto> =>
    api.get<MetricsSummaryDto>('/ai/summary', { params: { from, to, repoId } }).then((r) => r.data),

  generateTeamSummary: (teamId: number, from: string, to: string): Promise<MetricsSummaryDto> =>
    api.get<MetricsSummaryDto>(`/ai/summary/teams/${teamId}`, { params: { from, to } }).then((r) => r.data),

  generateMemberSummary: (teamId: number, memberId: number, from: string, to: string): Promise<MetricsSummaryDto> =>
    api.get<MetricsSummaryDto>(`/ai/summary/teams/${teamId}/member/${memberId}`, { params: { from, to } }).then((r) => r.data),

  latestSummary: (): Promise<MetricsSummaryDto | null> =>
    api.get<MetricsSummaryDto>('/ai/summary/latest')
      .then((r) => r.data ?? null)
      .catch(() => null),

  latestTeamSummary: (teamId: number): Promise<MetricsSummaryDto | null> =>
    api.get<MetricsSummaryDto>(`/ai/summary/teams/${teamId}/latest`)
      .then((r) => r.data ?? null)
      .catch(() => null),

  startConversation: (summaryScope: string, summaryJson: string): Promise<ConversationDto> =>
    api.post<ConversationDto>('/ai/conversations', { summaryScope, summaryJson }).then((r) => r.data),

  sendMessage: (conversationId: number, content: string): Promise<MessageDto> =>
    api.post<MessageDto>(`/ai/conversations/${conversationId}/messages`, { content }).then((r) => r.data),

  getMessages: (conversationId: number): Promise<MessageDto[]> =>
    api.get<MessageDto[]>(`/ai/conversations/${conversationId}/messages`).then((r) => r.data),

  summaryHistory: (limit = 10): Promise<MetricsSummaryDto[]> =>
    api.get<MetricsSummaryDto[]>('/ai/summary/history', { params: { limit } }).then((r) => r.data),

  teamSummaryHistory: (teamId: number, limit = 10): Promise<MetricsSummaryDto[]> =>
    api.get<MetricsSummaryDto[]>(`/ai/summary/teams/${teamId}/history`, { params: { limit } }).then((r) => r.data),
};
