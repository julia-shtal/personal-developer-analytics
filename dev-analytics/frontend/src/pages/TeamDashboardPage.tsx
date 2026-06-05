import { useState, useEffect } from 'react';
import type { ComponentType, CSSProperties } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Users, ChevronDown, MessageSquare, Sparkles, AlertCircle, Shield } from 'lucide-react';
import { teamsApi } from '@/api/teams';
import { teamMetricsApi } from '@/api/metrics';
import { reposApi } from '@/api/repos';
import { aiApi } from '@/api/ai';
import { KpiTile } from '@/components/ui/KpiTile';
import { Chip } from '@/components/ui/Chip';
import { Modal } from '@/components/ui/Modal';
import { Avatar } from '@/components/ui/Avatar';
import { Tooltip } from '@/components/ui/Tooltip';
import { MetricBarChart } from '@/components/charts/MetricBarChart';
import { MultiLineChart } from '@/components/charts/MultiLineChart';
import { PageSpinner, Spinner } from '@/components/ui/Spinner';
import { AiTeamInsightCard } from '@/components/ai/AiTeamInsightCard';
import { ProseWithNumbers } from '@/components/ui/ProseWithNumbers';
import { useDateRange } from '@/context/DateRangeContext';
import { useAuth } from '@/context/AuthContext';
import { formatDate, timeAgo } from '@/lib/dates';
import { Commits, PRMerged, IssuesClosed, LeadTime, Churn, Focus } from '@/components/icons';
import type { Team, TeamMembership, MemberSummaryDto, RepoDto } from '@/types';
import type { MetricsSummaryDto } from '@/types/ai';

function fmt(v: number | undefined, decimals = 1) {
  if (v == null || v === 0) return '—';
  return v.toFixed(decimals);
}

function fmtHours(v: number | undefined) {
  if (!v || v <= 0) return '—';
  if (v < 24) return `${v.toFixed(1)}h`;
  return `${(v / 24).toFixed(1)}d`;
}

function fmtNumber(n: number) {
  return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(Math.round(n));
}

const KIND_COLOR: Record<string, string> = {
  positive: 'var(--emerald)',
  risk: 'var(--coral)',
  note: 'var(--amber)',
};
const KIND_CHIP: Record<string, 'emerald' | 'coral' | 'amber'> = {
  positive: 'emerald',
  risk: 'coral',
  note: 'amber',
};
const KIND_SYM: Record<string, string> = { positive: '+', risk: '!', note: '~' };

function MemberAiSummaryModal({ member, summary, onClose }: {
  member: MemberSummaryDto;
  summary: MetricsSummaryDto;
  onClose: () => void;
}) {
  return (
    <Modal
      open
      onClose={onClose}
      eyebrow={`── AI summary · ${member.username}`}
      title={summary.headline || `AI Summary — ${member.username}`}
      width={760}
    >
      <div style={{ marginTop: 8 }}>
        <div className="t-eyebrow" style={{ marginBottom: 6 }}>overview</div>
        <ProseWithNumbers text={summary.overview} className="t-body" style={{ maxWidth: 700, lineHeight: 1.6 }} />
      </div>

      {summary.insights.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div className="t-eyebrow" style={{ marginBottom: 8 }}>key insights</div>
          <div className="col gap-2">
            {summary.insights.map((insight, i) => (
              <div key={i} className="row gap-3" style={{ alignItems: 'flex-start', padding: '4px 0' }}>
                <span style={{
                  fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 600,
                  color: KIND_COLOR[insight.kind] ?? KIND_COLOR.note,
                  width: 18, lineHeight: 1.45, flexShrink: 0,
                }}>{KIND_SYM[insight.kind] ?? '~'}</span>
                <ProseWithNumbers text={insight.text} className="t-body" style={{ margin: 0, lineHeight: 1.55, flex: 1 }} />
                {insight.metric && <Chip color={KIND_CHIP[insight.kind] ?? 'amber'}>{insight.metric}</Chip>}
              </div>
            ))}
          </div>
        </div>
      )}

      {summary.recommendations.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div className="t-eyebrow" style={{ marginBottom: 8 }}>recommendations</div>
          <div className="col gap-2">
            {summary.recommendations.map((rec, i) => (
              <div key={i} className="row gap-3" style={{ alignItems: 'flex-start', padding: '4px 0' }}>
                <span className="chip-dot" style={{ color: 'var(--accent)', marginTop: 7, flexShrink: 0 }} />
                <ProseWithNumbers text={rec} className="t-body" style={{ margin: 0, lineHeight: 1.55 }} />
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="row gap-2" style={{ marginTop: 18, paddingTop: 12, borderTop: '1px solid var(--line-2)', color: 'var(--fg-3)' }}>
        <Shield width={12} height={12} />
        <span className="t-label" style={{ fontSize: 10.5 }}>Generated locally from metrics only</span>
        <span className="tick">·</span>
        <span className="t-label" style={{ fontSize: 10.5 }}>{summary.modelName} via Ollama</span>
      </div>
    </Modal>
  );
}

interface MemberDetailModalProps {
  member: MemberSummaryDto;
  teamId: number;
  open: boolean;
  onClose: () => void;
}

function MemberDetailModal({ member, teamId, open, onClose }: MemberDetailModalProps) {
  const navigate = useNavigate();
  const { range } = useDateRange();
  const { from, to } = range;

  const [, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 10_000);
    return () => clearInterval(id);
  }, []);

  const commits = useQuery({
    queryKey: ['member-commits', teamId, member.userId, from, to],
    queryFn: () =>
      teamMetricsApi.memberDailyCommits(teamId, member.userId, from, to).then((r) => r.data),
    enabled: open,
  });

  const [aiSummary, setAiSummary] = useState<MetricsSummaryDto | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);
  const [showAiSummary, setShowAiSummary] = useState(false);

  async function fetchMemberAi() {
    setAiLoading(true);
    setAiError(null);
    try {
      const result = await aiApi.generateMemberSummary(teamId, member.userId, from, to);
      setAiSummary(result);
      setShowAiSummary(true);
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setAiError(msg ?? 'Failed to generate AI summary');
    } finally {
      setAiLoading(false);
    }
  }

  const m = member.metrics;
  const churn = m.DAILY_CHURN_RATIO ?? 0;

  const kpiItems: Array<[string, string, string, ComponentType<{ width?: number; height?: number; style?: CSSProperties }>, string]> = [
    ['commits',       fmt(m.DAILY_COMMITS_COUNT, 0),                       'accent',  Commits,      'Avg daily commits in the selected period'],
    ['prs merged',    fmt(m.DAILY_PR_MERGED, 0),                           'accent',  PRMerged,     'Avg daily pull requests merged to the default branch'],
    ['issues closed', fmt(m.DAILY_ISSUES_CLOSED, 0),                       'emerald', IssuesClosed, 'Avg daily Jira/GitHub issues resolved or closed'],
    ['pr lead time',  fmtHours(m.PR_LEAD_TIME_HOURS_MEDIAN),               'cyan',    LeadTime,     'Median time from PR open to first merge'],
    ['churn',         churn > 0 ? `${(churn * 100).toFixed(0)}%` : '—',   churn > 0.25 ? 'coral' : 'amber', Churn, 'Ratio of deleted + churned lines to total changed lines. High values indicate rework.'],
    ['focus ratio',   m.FOCUS_RATIO_DAYS_TASKS != null ? `${Math.round(m.FOCUS_RATIO_DAYS_TASKS)}d` : '—', 'emerald', Focus, 'Days where task-related activity (issues) was the primary work type'],
  ];

  return (
    <>
    <Modal
      open={open}
      onClose={onClose}
      eyebrow={`── member · ${member.username}`}
      title={`${member.username} — ${formatDate(from)} – ${formatDate(to)}`}
      width={620}
      footer={
        <div className="row gap-2" style={{ justifyContent: 'space-between' }}>
          <span className="t-label">click any row to drill into other members</span>
          <div className="col gap-2" style={{ alignItems: 'flex-end' }}>
            <div className="row gap-2">
              <button
                className="btn btn-sm"
                onClick={() => navigate(`/messages?to=${member.userId}`)}
                aria-label={`Message ${member.username}`}
              >
                <MessageSquare width={12} height={12} />message
              </button>
              <button
                className="btn btn-sm btn-accent"
                onClick={fetchMemberAi}
                disabled={aiLoading}
                aria-label={`Ask AI about ${member.username}`}
              >
                {aiLoading ? <Spinner size="sm" /> : <Sparkles width={12} height={12} />}
                {aiLoading ? 'Generating…' : `ask AI about ${member.username}`}
              </button>
            </div>
            {aiError && (
              <span style={{ fontSize: 11, color: 'var(--coral-strong)' }}>{aiError}</span>
            )}
          </div>
        </div>
      }
    >
      {/* Header */}
      <div className="row gap-3" style={{ marginBottom: 18 }}>
        <Avatar
          user={{ id: member.userId, username: member.username, hasCustomAvatar: member.hasCustomAvatar, avatarPreset: member.avatarPreset }}
          size="lg"
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: 16, fontWeight: 600, color: 'var(--fg)' }}>
            {member.username}
          </div>
          <div className="row gap-2" style={{ marginTop: 4, flexWrap: 'wrap' }}>
            <Chip accent>contributor</Chip>
            {member.lastActiveAt
              ? <span className="t-label" style={{ fontSize: 11 }}><span className="dot dot-live" style={{ marginRight: 6 }} />active {timeAgo(new Date(member.lastActiveAt))}</span>
              : <span className="t-label" style={{ fontSize: 11 }}>no activity recorded</span>
            }
          </div>
        </div>
      </div>

      {/* 3×2 KPI grid */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 0,
        border: '1px solid var(--line)', borderRadius: 8, marginBottom: 16, overflow: 'hidden',
      }}>
        {kpiItems.map(([label, val, color, Icon, tip], i) => (
          <div key={label} style={{
            padding: '14px 16px',
            borderLeft: i % 3 ? '1px solid var(--line-2)' : 'none',
            borderTop: i >= 3 ? '1px solid var(--line-2)' : 'none',
          }}>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 6 }}>
              <span className="t-label" style={{ fontSize: 10 }}>{label}</span>
              <Tooltip content={tip}>
                <Icon width={13} height={13} style={{ color: `var(--${color})`, cursor: 'default' }} />
              </Tooltip>
            </div>
            <div className="t-number-big" style={{ fontSize: 24 }}>{val}</div>
          </div>
        ))}
      </div>

      {/* Daily commits bar chart */}
      <div className="card-quiet" style={{ padding: 14, borderRadius: 8 }}>
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
          <span className="t-eyebrow">commits — daily</span>
          <span className="t-label">
            {commits.isLoading ? 'loading…' : `${(commits.data ?? []).length} days`}
          </span>
        </div>
        {commits.isLoading ? (
          <div style={{ height: 140, display: 'flex', alignItems: 'center' }}>
            <span className="t-muted" style={{ fontSize: 12 }}>Loading…</span>
          </div>
        ) : (commits.data ?? []).length === 0 ? (
          <div style={{ height: 140, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="t-muted" style={{ fontSize: 12 }}>No data for this period.</span>
          </div>
        ) : (
          <MetricBarChart
            data={(commits.data ?? []).slice(-60)}
            color="var(--accent)"
            label="commits"
            height={140}
          />
        )}
      </div>
    </Modal>

    {/* Member AI summary modal */}
    {showAiSummary && aiSummary && (
      <MemberAiSummaryModal
        member={member}
        summary={aiSummary}
        onClose={() => setShowAiSummary(false)}
      />
    )}
    </>
  );
}

export function TeamDashboardPage() {
  const qc = useQueryClient();
  const { range } = useDateRange();
  const { isManager, isAdmin } = useAuth();
  const isFullAccess = isManager || isAdmin;
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null);
  const [teamRepoId, setTeamRepoId] = useState<number | null>(null);
  const [teamPickerOpen, setTeamPickerOpen] = useState(false);
  const [selectedMember, setSelectedMember] = useState<MemberSummaryDto | null>(null);
  const { from, to } = range;

  // Managers/admins see their managed teams; developers see teams they are members of.
  const { data: managedTeams, isLoading: managedLoading } = useQuery<Team[]>({
    queryKey: ['teams'],
    queryFn: () => teamsApi.list().then((r) => r.data),
    enabled: isFullAccess,
  });

  const { data: memberTeams, isLoading: memberLoading } = useQuery<TeamMembership[]>({
    queryKey: ['team-memberships'],
    queryFn: () => teamsApi.myMemberships().then((r) => r.data),
    enabled: !isFullAccess,
  });

  const teams: Array<{ id: number; name: string }> = isFullAccess
    ? (managedTeams ?? [])
    : (memberTeams ?? []);
  const teamsLoading = isFullAccess ? managedLoading : memberLoading;

  const activeTeamId = selectedTeamId ?? teams?.[0]?.id ?? null;
  const activeTeam = teams?.find((t) => t.id === activeTeamId);

  // Reset repo filter whenever the active team changes.
  useEffect(() => { setTeamRepoId(null); }, [activeTeamId]);

  const { data: teamRepos } = useQuery<RepoDto[]>({
    queryKey: ['team-repos', activeTeamId],
    queryFn: () =>
      activeTeamId
        ? reposApi.list(undefined, activeTeamId).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !!activeTeamId,
    staleTime: 5 * 60_000,
  });

  const { data: commitSeries, isLoading: commitsLoading } = useQuery({
    queryKey: ['team-commits', activeTeamId, from, to, teamRepoId],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.dailyCommits(activeTeamId, from, to, teamRepoId).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !!activeTeamId,
  });

  // Additional aggregate series for developer view — only fetched when not full access.
  const { data: prMergedSeries } = useQuery({
    queryKey: ['team-pr-merged', activeTeamId, from, to, teamRepoId],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.dailyPrMerged(activeTeamId, from, to, teamRepoId).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !isFullAccess && !!activeTeamId,
  });

  const { data: issuesClosedSeries } = useQuery({
    queryKey: ['team-issues-closed', activeTeamId, from, to, teamRepoId],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.dailyIssuesClosed(activeTeamId, from, to, teamRepoId).then((r) => r.data)
        : Promise.resolve([]),
    enabled: !isFullAccess && !!activeTeamId,
  });

  // Per-member summary: only fetched for manager/admin — developers don't have access.
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['team-summary', activeTeamId, from, to],
    queryFn: () =>
      activeTeamId
        ? teamMetricsApi.summary(activeTeamId, from, to).then((r) => r.data)
        : Promise.resolve([]),
    enabled: isFullAccess && !!activeTeamId,
  });

  const [calcError, setCalcError] = useState<string | null>(null);

  const calculateMutation = useMutation({
    mutationFn: ({ teamId, fromDate, toDate }: { teamId: number; fromDate: string; toDate: string }) =>
      teamMetricsApi.calculate(teamId, fromDate, toDate),
    onSuccess: () => {
      setCalcError(null);
      qc.invalidateQueries();
    },
    onError: (err) => {
      const msg = (err as { response?: { data?: { message?: string }; status?: number } })?.response?.data?.message;
      const status = (err as { response?: { status?: number } })?.response?.status;
      setCalcError(msg ?? `Calculation failed (HTTP ${status ?? 'unknown'})`);
      window.dispatchEvent(new CustomEvent('da:recalculate-done'));
    },
    onSettled: () => window.dispatchEvent(new CustomEvent('da:recalculate-done')),
  });

  const { mutate: runCalculate } = calculateMutation;

  useEffect(() => {
    const h = () => {
      if (!activeTeamId) return;
      window.dispatchEvent(new CustomEvent('da:recalculate-start'));
      runCalculate({ teamId: activeTeamId, fromDate: from, toDate: to });
    };
    window.addEventListener('da:recalculate', h);
    return () => window.removeEventListener('da:recalculate', h);
  }, [runCalculate, activeTeamId, from, to]);

  if (teamsLoading) return <PageSpinner />;

  if (!teams?.length) {
    return (
      <div className="page fade-in" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 320 }}>
        <Users width={40} height={40} style={{ opacity: 0.2, marginBottom: 12 }} />
        <p className="t-muted">
          {isFullAccess
            ? 'No teams found. You need to be a manager of at least one team.'
            : 'No teams found. You are not a member of any team yet.'}
        </p>
      </div>
    );
  }

  const aggregateSum = (series: typeof commitSeries) =>
    series?.filter((p) => p.userId == null).reduce((s, p) => s + p.value, 0) ?? 0;

  // Aggregate KPIs from per-member summary (manager/admin) or aggregate series (developer)
  const totals = isFullAccess
    ? (summary?.reduce(
        (acc, m) => ({
          commits:      acc.commits      + (m.metrics.DAILY_COMMITS_COUNT ?? 0),
          prsMerged:    acc.prsMerged    + (m.metrics.DAILY_PR_MERGED     ?? 0),
          issuesClosed: acc.issuesClosed + (m.metrics.DAILY_ISSUES_CLOSED ?? 0),
        }),
        { commits: 0, prsMerged: 0, issuesClosed: 0 }
      ) ?? { commits: 0, prsMerged: 0, issuesClosed: 0 })
    : {
        commits:      aggregateSum(commitSeries),
        prsMerged:    aggregateSum(prMergedSeries),
        issuesClosed: aggregateSum(issuesClosedSeries),
      };

  const topCommitter = isFullAccess && summary?.length
    ? [...summary].sort((a, b) => (b.metrics.DAILY_COMMITS_COUNT ?? 0) - (a.metrics.DAILY_COMMITS_COUNT ?? 0))[0]
    : null;

  const memberCount = isFullAccess
    ? (summary?.length ?? (activeTeam as Team | undefined)?.members?.length ?? 0)
    : (activeTeam as TeamMembership | undefined)?.memberCount ?? 0;

  return (
    <div className="page fade-in">
      {/* Hero */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 28, gap: 20, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 400px', minWidth: 0 }}>
          <div className="t-eyebrow" style={{ marginBottom: 10 }}>
            ── Team · {activeTeam?.name ?? '…'} · {memberCount} member{memberCount !== 1 ? 's' : ''}
          </div>
          <h1 className="t-h1">
            {(isFullAccess ? summaryLoading : commitsLoading)
              ? 'Loading team data…'
              : totals.commits > 0
                ? isFullAccess && topCommitter?.metrics.DAILY_COMMITS_COUNT
                  ? <><em>{fmtNumber(totals.commits)}</em> commits across the team, led by <em>{topCommitter.username}</em> at <em>{Math.round(topCommitter.metrics.DAILY_COMMITS_COUNT)}</em>.</>
                  : <><em>{fmtNumber(totals.commits)}</em> commits across the team.</>
                : 'No data for this period — try recalculating.'}
          </h1>
        </div>

        {/* Recalculate error */}
        {calcError && (
          <div className="row gap-2" style={{
            fontSize: 12, color: 'var(--coral-strong)',
            background: 'var(--coral-bg)',
            border: '1px solid color-mix(in oklab, var(--coral) 25%, var(--line))',
            borderRadius: 6, padding: '10px 14px', flexShrink: 0,
          }}>
            <AlertCircle width={13} height={13} style={{ flexShrink: 0 }} />
            {calcError}
          </div>
        )}

        {/* Team repo selector */}
        {teamRepos && teamRepos.length > 0 && (
          <div style={{ position: 'relative', display: 'inline-block', flexShrink: 0 }}>
            <select
              value={teamRepoId ?? ''}
              onChange={(e) => setTeamRepoId(e.target.value === '' ? null : Number(e.target.value))}
              aria-label="Filter team metrics by repository"
              style={{
                appearance: 'none',
                background: 'var(--bg-2)',
                border: '1px solid var(--line)',
                borderRadius: 6,
                color: 'var(--fg)',
                fontFamily: 'var(--font-mono)',
                fontSize: 11,
                padding: '5px 28px 5px 10px',
                cursor: 'pointer',
                outline: 'none',
                minWidth: 140,
              }}
            >
              <option value="">all repos</option>
              {teamRepos.map((repo) => (
                <option key={repo.id} value={repo.id}>{repo.name}</option>
              ))}
            </select>
            <ChevronDown
              width={11}
              height={11}
              style={{
                position: 'absolute',
                right: 8,
                top: '50%',
                transform: 'translateY(-50%)',
                pointerEvents: 'none',
                color: 'var(--fg-3)',
              }}
            />
          </div>
        )}

        {/* Team selector */}
        {teams.length > 1 && (
          <div style={{ position: 'relative', flexShrink: 0 }}>
            <button className="btn" onClick={() => setTeamPickerOpen((v) => !v)} aria-haspopup="listbox">
              <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>team</span>
              <span style={{ fontWeight: 500 }}>{activeTeam?.name}</span>
              <ChevronDown width={11} height={11} />
            </button>
            {teamPickerOpen && (
              <div
                style={{
                  position: 'absolute', top: '100%', right: 0, marginTop: 4, zIndex: 20,
                  background: 'var(--bg-card)', border: '1px solid var(--line)',
                  borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,.12)', minWidth: 160,
                }}
                role="listbox"
              >
                {teams.map((t) => (
                  <button
                    key={t.id}
                    className="btn"
                    role="option"
                    aria-selected={t.id === activeTeamId}
                    style={{
                      width: '100%', justifyContent: 'flex-start', padding: '9px 14px',
                      fontWeight: t.id === activeTeamId ? 600 : 400,
                      color: t.id === activeTeamId ? 'var(--accent)' : 'var(--fg)',
                      borderRadius: 6,
                    }}
                    onClick={() => { setSelectedTeamId(t.id); setTeamPickerOpen(false); }}
                  >
                    {t.name}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* KPI row */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="grid-kpi">
            <KpiTile label="team commits"       value={fmtNumber(totals.commits)}                 sub="all members"         accent="accent"  icon={<Commits      width={16} height={16} />} tooltip="Sum of daily commits across all team members in the selected period" />
          <KpiTile label="team prs merged"   value={String(Math.round(totals.prsMerged))}    sub="to default branch"   accent="accent"  icon={<PRMerged     width={16} height={16} />} tooltip="Sum of daily pull requests merged to the default branch by the team" />
          <KpiTile label="team issues closed" value={String(Math.round(totals.issuesClosed))} sub="resolved · closed"  accent="emerald" icon={<IssuesClosed width={16} height={16} />} tooltip="Sum of daily Jira/GitHub issues resolved or closed by the team" />
          <KpiTile label="members"           value={memberCount || '—'}                       sub="in this team"        accent="cyan"    icon={<Users        width={16} height={16} />} tooltip="Number of members in the team" />
        </div>
      </div>

      {/* AI team insight — managers/admins only; requires per-member summary data */}
      {isFullAccess && activeTeamId && (
        <AiTeamInsightCard
          range={range}
          teamId={activeTeamId}
          teamName={activeTeam?.name ?? ''}
          memberSummary={summary ?? []}
        />
      )}

      {/* Commits chart */}
      <div className="card" style={{ padding: 22, marginBottom: 24 }}>
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 14 }}>
          <div>
            <div className="t-eyebrow">
              {isFullAccess ? 'commits — per member' : 'commits — team aggregate'}
            </div>
            <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>
              {isFullAccess ? 'Daily commits by member' : 'Daily commits'}
            </div>
          </div>
          {isFullAccess && <Chip>{memberCount} member{memberCount !== 1 ? 's' : ''}</Chip>}
        </div>
        {commitsLoading ? (
          <div style={{ height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="t-muted">Loading…</span>
          </div>
        ) : commitSeries?.length ? (
          isFullAccess ? (
            <MultiLineChart data={commitSeries.filter((d) => d.username !== 'team')} height={220} />
          ) : (
            <MetricBarChart
              data={commitSeries
                .filter((d) => d.userId == null)
                .map((d) => ({ date: d.date, value: d.value, metricType: d.metricType }))}
              color="var(--accent)"
              label="commits"
              height={220}
            />
          )
        ) : (
          <div style={{ height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="t-muted">No data — try recalculating for this period.</span>
          </div>
        )}
      </div>

      {/* Member table — managers/admins only */}
      {isFullAccess && (
        <div className="card" style={{ overflow: 'hidden' }}>
          <div className="row" style={{ padding: '14px 20px', justifyContent: 'space-between' }}>
            <div>
              <div className="t-eyebrow">members</div>
              <div className="t-h2" style={{ fontSize: 22, marginTop: 4 }}>Per-member breakdown</div>
            </div>
          </div>

          {summaryLoading ? (
            <div style={{ padding: '48px 20px', textAlign: 'center' }}>
              <span className="t-muted">Loading…</span>
            </div>
          ) : summary?.length ? (
            <table className="table">
              <thead>
                <tr>
                  <th>member</th>
                  <th style={{ textAlign: 'right' }}>commits</th>
                  <th style={{ textAlign: 'right' }}>prs merged</th>
                  <th style={{ textAlign: 'right' }}>prs opened</th>
                  <th style={{ textAlign: 'right' }}>issues closed</th>
                  <th style={{ textAlign: 'right' }}>pr lead</th>
                  <th style={{ textAlign: 'right' }}>churn</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {summary.map((m) => {
                  const churn = m.metrics.DAILY_CHURN_RATIO ?? 0;
                  const isActive = selectedMember?.userId === m.userId;
                  return (
                    <tr
                      key={m.userId}
                      onClick={() => setSelectedMember(m)}
                      style={{ cursor: 'pointer', background: isActive ? 'var(--bg-2)' : 'transparent' }}
                    >
                      <td>
                        <div className="row gap-3">
                          <Avatar
                            user={{ id: m.userId, username: m.username, hasCustomAvatar: m.hasCustomAvatar, avatarPreset: m.avatarPreset }}
                            size="sm"
                          />
                          <div>
                            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: 'var(--fg)', fontWeight: 500 }}>
                              {m.username}
                            </div>
                            <div className="t-label" style={{ fontSize: 10, marginTop: 1 }}>contributor</div>
                          </div>
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }} className="num">{fmt(m.metrics.DAILY_COMMITS_COUNT, 0)}</td>
                      <td style={{ textAlign: 'right' }} className="num">{fmt(m.metrics.DAILY_PR_MERGED, 0)}</td>
                      <td style={{ textAlign: 'right' }} className="num">{fmt(m.metrics.DAILY_PR_CREATED, 0)}</td>
                      <td style={{ textAlign: 'right' }} className="num">{fmt(m.metrics.DAILY_ISSUES_CLOSED, 0)}</td>
                      <td style={{ textAlign: 'right' }} className="num">{fmtHours(m.metrics.PR_LEAD_TIME_HOURS_MEDIAN)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {churn > 0 ? (
                          <Chip color={churn > 0.25 ? 'coral' : churn > 0.15 ? 'amber' : 'emerald'}>
                            {(churn * 100).toFixed(0)}%
                          </Chip>
                        ) : '—'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <ChevronDown
                          width={13} height={13}
                          style={{ color: 'var(--fg-3)', transform: 'rotate(-90deg)' }}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <div style={{ padding: '48px 20px', textAlign: 'center' }}>
              <span className="t-muted">No summary data — recalculate to populate.</span>
            </div>
          )}
        </div>
      )}

      <div style={{ height: 32 }} />

      {/* Member detail modal — managers/admins only */}
      {isFullAccess && selectedMember && activeTeamId && (
        <MemberDetailModal
          member={selectedMember}
          teamId={activeTeamId}
          open={!!selectedMember}
          onClose={() => setSelectedMember(null)}
        />
      )}
    </div>
  );
}