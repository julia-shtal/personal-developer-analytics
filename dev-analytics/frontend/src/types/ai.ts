export interface MetricsSummaryDto {
  from: string;
  to: string;
  scope: 'PERSONAL' | 'REPOSITORY' | 'TEAM';
  repoName?: string;
  overview: string;
  insights: string[];
  recommendations: string[];
  rawModelOutput: string;
  modelName: string;
}
