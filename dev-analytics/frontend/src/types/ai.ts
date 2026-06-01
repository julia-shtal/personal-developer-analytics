export interface InsightDto {
  kind: 'positive' | 'risk' | 'note';
  text: string;
  metric: string;
}

export interface MetricsSummaryDto {
  from: string;
  to: string;
  scope: 'PERSONAL' | 'REPOSITORY' | 'TEAM';
  contextRepoName?: string;
  headline: string;
  overview: string;
  insights: InsightDto[];
  recommendations: string[];
  rawModelOutput: string;
  modelName: string;
}

export interface ConversationDto {
  id: number;
  createdAt: string;
}

export interface MessageDto {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
}