import api from '@/lib/api';
import type { IssueCountDto, JiraProjectDto, TrackedJiraProjectDto } from '@/types';

export const issuesApi = {
  listTrackedJiraProjects: (dataSourceId: number) =>
    api.get<TrackedJiraProjectDto[]>('/jira-projects', { params: { dataSourceId } }),
  listAvailableJiraProjects: (dataSourceId: number) =>
    api.get<JiraProjectDto[]>('/jira-projects/available', { params: { dataSourceId } }),
  /** @deprecated use listTrackedJiraProjects */
  listJiraProjects: (dataSourceId: number) =>
    api.get<JiraProjectDto[]>('/jira-projects/available', { params: { dataSourceId } }),
  getCount: (repoId: number) =>
    api.get<IssueCountDto>('/issues/count', { params: { repoId } }),
};
