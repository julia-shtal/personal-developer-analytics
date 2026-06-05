// ─── Auth ────────────────────────────────────────────────────────────────────

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  inviteToken?: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

// ─── User ────────────────────────────────────────────────────────────────────

export type Role = 'DEVELOPER' | 'MANAGER' | 'ADMIN';

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  role: Role;
  timezone?: string;
  githubLogin?: string;
  hasCustomAvatar?: boolean;
  avatarPreset?: string;
}

export interface UpdateProfileRequest {
  username?: string;
  email?: string;
  timezone?: string;
  githubLogin?: string;
}

// ─── Data Sources ─────────────────────────────────────────────────────────────

export type DataSourceType = 'GIT_LOCAL' | 'GITHUB' | 'JIRA';

export interface DataSourceConfig {
  id: number;
  type: DataSourceType;
  name: string;
  baseUrl?: string;
  path?: string;
  enabled: boolean;
  lastSuccessSync?: string;
  createdAt?: string;
  teamId?: number;
  canDelete: boolean;
  repoCount: number;
}

export interface CreateDataSourceRequest {
  type: DataSourceType;
  name: string;
  baseUrl?: string;
  path?: string;
  apiToken?: string;
  teamId?: number;
  repoFullName?: string;
  projectKey?: string; // JIRA only — scopes collection to a single project
}

export interface JiraProjectDto {
  key: string;
  name: string;
  id: string;
}

export interface TrackedJiraProjectDto {
  id: number;
  dataSourceId: number;
  dataSourceBaseUrl: string | null;
  projectKey: string;
  projectName: string | null;
  lastScanAt: string | null;
  subscribed: boolean;
}

export interface RepoDto {
  id: number;
  name: string;
  repoFullName?: string;
  localPath?: string;
  dataSourceId: number;
  subscribed: boolean;
  repoUrl?: string;
  collectIssues: boolean;
  issuesLastSyncedAt?: string;
  teamId?: number;
}

export interface DiscoveredRepoDto {
  fullName: string;
  /** Serialised as "private" by the backend */
  private: boolean;
  defaultBranch: string;
  alreadyAttached: boolean;
}

export interface DiscoveredProjectDto {
  projectKey: string;
  projectName: string;
  alreadyAttached: boolean;
}

export interface IssueCountDto {
  open: number;
  closed: number;
}

// ─── Metrics ──────────────────────────────────────────────────────────────────

export type MetricType =
  | 'DAILY_COMMITS_COUNT'
  | 'DAILY_PR_CREATED'
  | 'DAILY_PR_MERGED'
  | 'DAILY_ISSUES_CLOSED'
  | 'DAILY_ISSUES_CREATED'
  | 'DAILY_CHURN_RATIO'
  | 'PR_LEAD_TIME_HOURS_MEDIAN'
  | 'PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN'
  | 'REVIEW_RESPONSE_TIME_HOURS_MEDIAN'
  | 'ISSUE_LEAD_TIME_HOURS_MEDIAN'
  | 'FOCUS_RATIO_DAYS_TASKS'
  | 'AFTER_HOURS_COMMIT_RATIO'
  | 'DEEP_WORK_STREAK_DAYS'
  | 'KNOWLEDGE_SILO_SCORE'
  | 'REFACTOR_RATIO'
  | 'PR_SIZE_COMPLEXITY_SCORE'
  | 'MERGE_WITHOUT_REVIEW_RATIO'
  | 'MERGE_TO_MAIN_FREQUENCY_PER_WEEK';

export interface MetricPointDto {
  date: string;
  value: number;
  metricType: string;
  repositoryId?: number;
  repositoryName?: string;
}

export interface MetricAggregateDto {
  metricType: string;
  value: number;
  periodFrom?: string;
  periodTo?: string;
}

export interface TeamMetricPointDto {
  date: string;
  value: number;
  metricType: string;
  userId?: number;
  username?: string;
}

export interface MemberSummaryDto {
  userId: number;
  username: string;
  metrics: Partial<Record<MetricType, number>>;
  hasCustomAvatar?: boolean;
  avatarPreset?: string;
  lastActiveAt?: string;
  email?: string;
}

/** Per-metric anomaly flags. True means the metric deviates > 2σ from its window mean. */
export type MetricAnomalyResponse = Partial<Record<MetricType, boolean>>;

// ─── Teams ───────────────────────────────────────────────────────────────────

export interface Team {
  id: number;
  name: string;
  managerId?: number;
  members?: UserProfile[];
  archivedAt?: string;
  visibility?: string;
  aiBriefSchedule?: string;
}

/** Lean team descriptor returned to developer-role members: id, name, and member count. */
export interface TeamMembership {
  id: number;
  name: string;
  memberCount: number;
}

// ─── Date range ──────────────────────────────────────────────────────────────

export interface DateRange {
  from: string; // ISO date
  to: string;
}
