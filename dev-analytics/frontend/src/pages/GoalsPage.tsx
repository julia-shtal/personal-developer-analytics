import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Target, Trash2, Plus, X } from 'lucide-react';
import { goalsApi } from '@/api/goals';
import type { GoalRequestDto } from '@/api/goals';
import { PageSpinner } from '@/components/ui/Spinner';

const METRIC_LABELS: Record<string, string> = {
  DAILY_COMMITS_COUNT: 'Daily Commits',
  DAILY_COMMITS_AVG_SIZE: 'Avg Commit Size',
  DAILY_PR_CREATED: 'PRs Created',
  DAILY_PR_MERGED: 'Merged PRs',
  DAILY_ISSUES_CREATED: 'Issues Created',
  DAILY_ISSUES_CLOSED: 'Issues Closed',
  DAILY_CHURN_RATIO: 'Churn Ratio',
  PR_LEAD_TIME_HOURS_MEDIAN: 'PR Lead Time',
  PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: 'First Commit to Merge',
  REVIEW_RESPONSE_TIME_HOURS_MEDIAN: 'Review Response Time',
  ISSUE_LEAD_TIME_HOURS_MEDIAN: 'Issue Lead Time',
  FOCUS_RATIO_DAYS_TASKS: 'Focus Ratio',
  KNOWLEDGE_SILO_SCORE: 'Knowledge Silo Score',
  REFACTOR_RATIO: 'Refactor Ratio',
  MERGE_WITHOUT_REVIEW_RATIO: 'Merge Without Review',
  AFTER_HOURS_COMMIT_RATIO: 'After-Hours Commits',
  DEEP_WORK_STREAK_DAYS: 'Deep Work Streak',
  PR_SIZE_COMPLEXITY_SCORE: 'PR Size Complexity',
  COMMITS_PER_WEEK_AVG: 'Commits per Week (avg)',
  REVIEW_PARTICIPATION_COUNT: 'Review Participation',
};

const METRIC_KEYS = Object.keys(METRIC_LABELS);

const DEFAULT_FORM: GoalRequestDto = {
  metricType: METRIC_KEYS[0],
  targetValue: 0,
  targetDate: '',
};

export function GoalsPage() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<GoalRequestDto>(DEFAULT_FORM);

  const { data: goals, isLoading, isError } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.list(),
  });

  const createMutation = useMutation({
    mutationFn: (req: GoalRequestDto) => goalsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['goals'] });
      setShowModal(false);
      setForm(DEFAULT_FORM);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => goalsApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['goals'] });
    },
  });

  function openModal() {
    setForm(DEFAULT_FORM);
    setShowModal(true);
  }

  function closeModal() {
    setShowModal(false);
    setForm(DEFAULT_FORM);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.metricType || !form.targetDate || form.targetValue < 0) return;
    createMutation.mutate(form);
  }

  if (isLoading) return <PageSpinner />;

  return (
    <div className="page fade-in">
      {/* ── Header ───────────────────────────────────────── */}
      <div
        className="row"
        style={{ marginBottom: 28, alignItems: 'center', justifyContent: 'space-between' }}
      >
        <h1 className="t-h1" style={{ margin: 0 }}>My Goals</h1>
        <button className="btn btn-sm btn-accent" onClick={openModal}>
          <Plus width={14} height={14} style={{ marginRight: 6 }} />
          Add Goal
        </button>
      </div>

      {/* ── Error ────────────────────────────────────────── */}
      {isError && (
        <p style={{ color: 'var(--coral-strong)', fontSize: 13 }}>
          Failed to load goals. Please try again.
        </p>
      )}

      {/* ── Empty state ──────────────────────────────────── */}
      {!isError && goals?.length === 0 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '64px 0',
            gap: 12,
          }}
        >
          <Target width={40} height={40} style={{ color: 'var(--fg-muted)' }} />
          <p className="t-muted" style={{ fontSize: 14 }}>No goals set yet.</p>
        </div>
      )}

      {/* ── Goals list ───────────────────────────────────── */}
      {!isError && goals && goals.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {goals.map((goal) => (
            <div
              key={goal.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 18px',
                background: 'var(--bg-2)',
                borderRadius: 8,
                border: '1px solid var(--line)',
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--fg)' }}>
                  {METRIC_LABELS[goal.metricType] ?? goal.metricType}
                </span>
                <span className="t-muted" style={{ fontSize: 12 }}>
                  Target: <strong style={{ color: 'var(--fg)' }}>{goal.targetValue}</strong>
                  {' · '}
                  By: <strong style={{ color: 'var(--fg)' }}>{goal.targetDate}</strong>
                </span>
              </div>
              <button
                className="btn btn-sm"
                aria-label={`Delete goal for ${METRIC_LABELS[goal.metricType] ?? goal.metricType}`}
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(goal.id)}
                style={{ color: 'var(--coral-strong)', borderColor: 'var(--coral)' }}
              >
                <Trash2 width={14} height={14} />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* ── Add Goal Modal ───────────────────────────────── */}
      {showModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 50,
          }}
          onClick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
        >
          <div
            style={{
              background: 'var(--bg)',
              border: '1px solid var(--line)',
              borderRadius: 12,
              padding: '28px 32px',
              width: '100%',
              maxWidth: 440,
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
            }}
          >
            {/* Modal header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: 'var(--fg)' }}>
                Add Goal
              </h2>
              <button
                className="btn btn-sm"
                aria-label="Close modal"
                onClick={closeModal}
                style={{ padding: '4px 8px' }}
              >
                <X width={16} height={16} />
              </button>
            </div>

            {/* Modal form */}
            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg-muted)' }}>
                  Metric
                </label>
                <select
                  className="input"
                  value={form.metricType}
                  onChange={(e) => setForm((f) => ({ ...f, metricType: e.target.value }))}
                  required
                >
                  {METRIC_KEYS.map((key) => (
                    <option key={key} value={key}>
                      {METRIC_LABELS[key]}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg-muted)' }}>
                  Target Value
                </label>
                <input
                  className="input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.targetValue}
                  onChange={(e) => setForm((f) => ({ ...f, targetValue: Number(e.target.value) }))}
                  required
                />
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg-muted)' }}>
                  Target Date
                </label>
                <input
                  className="input"
                  type="date"
                  value={form.targetDate}
                  onChange={(e) => setForm((f) => ({ ...f, targetDate: e.target.value }))}
                  required
                />
              </div>

              {createMutation.isError && (
                <p style={{ fontSize: 12, color: 'var(--coral-strong)', margin: 0 }}>
                  Failed to save goal. Please try again.
                </p>
              )}

              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={closeModal}
                  disabled={createMutation.isPending}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-sm btn-accent"
                  disabled={createMutation.isPending}
                >
                  {createMutation.isPending ? 'Saving…' : 'Save Goal'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
