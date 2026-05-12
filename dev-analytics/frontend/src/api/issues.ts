import api from '@/lib/api';
import type { IssueCountDto, JiraProjectDto } from '@/types';

export const issuesApi = {
  listJiraProjects: (dataSourceId: number) =>
    api.get<JiraProjectDto[]>('/issues/jira-projects', { params: { dataSourceId } }),
  getCount: (repoId: number) =>
    api.get<IssueCountDto>('/issues/count', { params: { repoId } }),
};
