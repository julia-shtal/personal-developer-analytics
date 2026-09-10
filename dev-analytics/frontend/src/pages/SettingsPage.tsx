import { useState, useEffect, useRef, useMemo, type FormEvent, type ChangeEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Sun, Moon, Lock, Eye, EyeOff, Trash2, Plus } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Avatar } from '@/components/ui/Avatar';
import { Chip } from '@/components/ui/Chip';
import { Modal } from '@/components/ui/Modal';
import { AccentSwatches, ACCENT_SWATCHES } from '@/components/ui/AccentSwatches';
import { Logo } from '@/components/brand/Logo';
import { ACTIVE_LOGO } from '@/config/branding';
import type { LogoVariant } from '@/lib/theme';
import api from '@/lib/api';
import { avatarApi } from '@/api/avatar';
import { usersApi, type NotificationPrefsDto } from '@/api/users';
import type { CommitEmailDto, UpdateProfileRequest, UserProfile } from '@/types';

// ─── Timezone helpers ─────────────────────────────────────────────────────────

interface TzOption {
  iana: string;
  offset: string;
  offsetMinutes: number;
  label: string;
}

function buildTimezoneOptions(): TzOption[] {
  const now = new Date();
  const zones: string[] = (Intl as unknown as { supportedValuesOf: (k: string) => string[] })
    .supportedValuesOf('timeZone');
  return zones
    .map((iana): TzOption | null => {
      try {
        const parts = new Intl.DateTimeFormat('en', {
          timeZone: iana,
          timeZoneName: 'shortOffset',
        }).formatToParts(now);
        const rawOffset = parts.find((p) => p.type === 'timeZoneName')?.value ?? 'GMT+0';
        const offset = rawOffset.replace('GMT', 'UTC').replace('UTC+0', 'UTC');
        const match = offset.match(/UTC([+-])(\d{1,2})(?::(\d{2}))?/);
        let offsetMinutes = 0;
        if (match) {
          const sign = match[1] === '+' ? 1 : -1;
          offsetMinutes = sign * (parseInt(match[2]) * 60 + parseInt(match[3] ?? '0'));
        }
        return { iana, offset, offsetMinutes, label: `${offset} — ${iana}` };
      } catch { return null; }
    })
    .filter((o): o is TzOption => o !== null)
    .sort((a, b) => a.offsetMinutes - b.offsetMinutes || a.iana.localeCompare(b.iana));
}

const BROWSER_TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;

// ─── Logo variant metadata ────────────────────────────────────────────────────

const LOGO_VARIANTS: { variant: LogoVariant; label: string; desc: string }[] = [
  { variant: 'pulse',   label: 'Pulse bars',       desc: 'The chart-as-D mark.' },
  { variant: 'bracket', label: 'Bracket monogram',  desc: '[d] with cursor terminal.' },
  { variant: 'slash',   label: 'CLI slash',         desc: 'Terminal-prompt with blinking cursor.' },
  { variant: 'crystal', label: 'Editorial italic',  desc: 'Serif D in a circle.' },
];

// ─── Component ───────────────────────────────────────────────────────────────

export function SettingsPage() {
  const { user, logout, refreshUser } = useAuth();
  const { theme, accent, logo, setTheme } = useTheme();
  const qc = useQueryClient();
  const navigate = useNavigate();

  // ── Profile state ────────────────────────────────────────────────────────
  const [timezone, setTimezone] = useState(user?.timezone ?? 'UTC');
  const [githubLogin, setGithubLogin] = useState(user?.githubLogin ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [tzSearch, setTzSearch] = useState('');
  const [tzOpen, setTzOpen] = useState(false);
  const [jiraAccountId, setJiraAccountId] = useState(user?.jiraAccountId ?? '');
  const [newCommitEmail, setNewCommitEmail] = useState('');
  const [commitEmailError, setCommitEmailError] = useState('');
  const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [saveError, setSaveError] = useState('');
  const [avatarError, setAvatarError] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);
  const tzRef = useRef<HTMLDivElement>(null);

  const tzOptions = useMemo(() => buildTimezoneOptions(), []);
  const filteredTz = useMemo(() => {
    const q = tzSearch.toLowerCase();
    if (!q) return tzOptions;
    return tzOptions.filter(
      (o) => o.iana.toLowerCase().includes(q) || o.offset.toLowerCase().includes(q),
    );
  }, [tzOptions, tzSearch]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setTimezone(user?.timezone ?? 'UTC');
     
    setGithubLogin(user?.githubLogin ?? '');
     
    setUsername(user?.username ?? '');
     
    setEmail(user?.email ?? '');
     
    setJiraAccountId(user?.jiraAccountId ?? '');
  }, [user]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (tzRef.current && !tzRef.current.contains(e.target as Node)) setTzOpen(false);
    }
    if (tzOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [tzOpen]);

  // ── Accent hex input — uncontrolled with key reset so no setState-in-effect needed ──
  const matchedSwatch = ACCENT_SWATCHES.find(
    (s) => s.hex.toLowerCase() === accent.toLowerCase(),
  );

  function commitHex(v: string) {
    if (/^#[0-9a-fA-F]{6}$/.test(v)) setTheme({ accent: v.toLowerCase() });
  }

  // ── Password state ────────────────────────────────────────────────────────
  const [pwOpen, setPwOpen] = useState(false);
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwStatus, setPwStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [pwError, setPwError] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  // ── Delete account state ─────────────────────────────────────────────────
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteEmailInput, setDeleteEmailInput] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const deleteAccountMutation = useMutation({
    mutationFn: () => usersApi.deleteAccount(),
    onSuccess: async () => {
      await logout();
      navigate('/login');
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setDeleteError(msg ?? 'Failed to delete account.');
    },
  });

  function openDeleteModal() {
    setDeleteEmailInput('');
    setDeleteError('');
    setDeleteModalOpen(true);
  }

  function handleDeleteConfirm() {
    setDeleteError('');
    deleteAccountMutation.mutate();
  }

  // ── Avatar queries + mutations ───────────────────────────────────────────
  const { data: presets = [] } = useQuery({
    queryKey: ['avatar-presets'],
    queryFn: () => avatarApi.getPresets().then((r) => r.data),
    staleTime: Infinity,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => avatarApi.upload(file),
    onSuccess: () => { setAvatarError(''); refreshUser(); },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setAvatarError(msg ?? 'Upload failed. Check file type and size (max 2 MB).');
    },
  });

  const presetMutation = useMutation({
    mutationFn: (presetId: string) => avatarApi.setPreset(presetId),
    onSuccess: () => { setAvatarError(''); refreshUser(); },
  });

  const deleteAvatarMutation = useMutation({
    mutationFn: () => avatarApi.delete(),
    onSuccess: () => { setAvatarError(''); refreshUser(); },
  });

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    uploadMutation.mutate(file);
    e.target.value = '';
  }

  // ── Profile save ─────────────────────────────────────────────────────────
  const updateMutation = useMutation({
    mutationFn: (body: UpdateProfileRequest) => api.put<UserProfile>('/users/me', body),
    onSuccess: () => {
      setSaveStatus('success');
      setSaveError('');
      setTimeout(() => setSaveStatus('idle'), 3000);
    },
    onError: (err: unknown) => {
      const res = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
      // 422 is "no such login", 409 is "already linked elsewhere". Anything else keeps the
      // server's message, so a 502 does not read as "login not found".
      const msg =
        res?.status === 422 ? 'GitHub login not found.'
        : res?.status === 409 ? 'Already linked to another account.'
        : res?.data?.message ?? 'Failed to save profile.';
      setSaveError(msg);
      setSaveStatus('error');
    },
  });

  // ── Commit emails ────────────────────────────────────────────────────────
  const { data: commitEmails } = useQuery({
    queryKey: ['commit-emails'],
    queryFn: () => usersApi.commitEmails.list().then((r) => r.data),
    staleTime: 60_000,
  });

  const addCommitEmailMutation = useMutation({
    mutationFn: (value: string) => usersApi.commitEmails.add(value),
    onSuccess: () => {
      setNewCommitEmail('');
      setCommitEmailError('');
      qc.invalidateQueries();
    },
    onError: (err: unknown) => {
      const res = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
      setCommitEmailError(
        res?.status === 409
          ? 'Already linked to another account.'
          : res?.data?.message ?? 'Could not add that address.',
      );
    },
  });

  const removeCommitEmailMutation = useMutation({
    mutationFn: (id: number) => usersApi.commitEmails.remove(id),
    onSuccess: () => {
      setCommitEmailError('');
      qc.invalidateQueries();
    },
  });

  const changePasswordMutation = useMutation({
    mutationFn: (body: { oldPassword: string; newPassword: string }) =>
      api.put('/users/me/password', body),
    onSuccess: () => {
      setPwStatus('success');
      setPwError('');
      setOldPassword(''); setNewPassword(''); setConfirmPassword('');
      setTimeout(() => { setPwStatus('idle'); setPwOpen(false); }, 2500);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setPwError(msg ?? 'Failed to change password.');
      setPwStatus('error');
    },
  });

  function handlePasswordChange(e: FormEvent) {
    e.preventDefault();
    setPwStatus('idle');
    if (newPassword !== confirmPassword) { setPwError('New passwords do not match.'); setPwStatus('error'); return; }
    if (newPassword.length < 8) { setPwError('New password must be at least 8 characters.'); setPwStatus('error'); return; }
    changePasswordMutation.mutate({ oldPassword, newPassword });
  }

  function handleSave(e: FormEvent) {
    e.preventDefault();
    setSaveStatus('idle');
    updateMutation.mutate({
      username: username || undefined,
      email: email || undefined,
      timezone: timezone || undefined,
      githubLogin: githubLogin || undefined,
      jiraAccountId: jiraAccountId || undefined,
    });
  }

  // ── Notification prefs — use query data directly with optimistic cache update ──
  const DEFAULT_NOTIFS: NotificationPrefsDto = { aiBrief: false, syncFailures: false, afterHours: false, newTeamMember: false, defaultContactMethod: 'IN_APP' };

  const { data: notifData } = useQuery({
    queryKey: ['notification-prefs'],
    queryFn: () => usersApi.notifications.get().then((r) => r.data),
    staleTime: 60_000,
  });

  const notifs = notifData ?? DEFAULT_NOTIFS;

  const notifMutation = useMutation({
    mutationFn: (dto: NotificationPrefsDto) => usersApi.notifications.update(dto),
    onSuccess: (res) => {
      qc.setQueryData(['notification-prefs'], res.data);
    },
  });

  function toggleNotif(key: 'aiBrief' | 'syncFailures' | 'afterHours' | 'newTeamMember') {
    const next = { ...notifs, [key]: !notifs[key] };
    qc.setQueryData(['notification-prefs'], next);
    notifMutation.mutate(next);
  }

  function setContactMethod(method: 'IN_APP' | 'EMAIL') {
    const next = { ...notifs, defaultContactMethod: method };
    qc.setQueryData(['notification-prefs'], next);
    notifMutation.mutate(next);
  }

  const selectedTz = tzOptions.find((o) => o.iana === timezone);
  const avatarBusy = uploadMutation.isPending || presetMutation.isPending || deleteAvatarMutation.isPending;
  const hasAvatar = user?.hasCustomAvatar || !!user?.avatarPreset;

  const ROLE_CHIP: Record<string, 'violet' | 'coral' | 'cyan'> = {
    DEVELOPER: 'violet',
    MANAGER:   'cyan',
    ADMIN:     'coral',
  };

  return (
    <div className="page narrow">
      <div className="t-eyebrow" style={{ marginBottom: 10 }}>── Settings</div>
      <h1 className="t-h1" style={{ marginBottom: 32 }}>Your preferences.</h1>

      <div className="col gap-4">

        {/* ── Appearance ──────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22 }}>
          <div className="t-eyebrow" style={{ marginBottom: 14 }}>── appearance</div>

          {/* Theme toggle */}
          <div className="row" style={{ alignItems: 'flex-start', gap: 24, marginBottom: 22, flexWrap: 'wrap' }}>
            <div style={{ minWidth: 140 }}>
              <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--fg)' }}>Theme</div>
              <div className="t-label" style={{ marginTop: 2 }}>warm paper or carbon</div>
            </div>
            <div className="row gap-0" style={{
              border: '1px solid var(--line)', borderRadius: 6,
              padding: 2, background: 'var(--bg-card)',
            }}>
              {([['light', 'light', <Sun key="i" width={12} height={12} />], ['dark', 'dark', <Moon key="i" width={12} height={12} />]] as const).map(([id, label, icon]) => (
                <button
                  key={id}
                  onClick={() => setTheme({ theme: id })}
                  aria-label={label}
                  style={{
                    fontFamily: 'var(--font-mono)', fontSize: 11, padding: '5px 11px',
                    background: theme === id ? 'var(--bg-2)' : 'transparent',
                    color: theme === id ? 'var(--fg)' : 'var(--fg-3)',
                    border: 'none', cursor: 'pointer', borderRadius: 4,
                    fontWeight: theme === id ? 600 : 400,
                    display: 'inline-flex', alignItems: 'center', gap: 6,
                  }}
                >{icon}{label}</button>
              ))}
            </div>
          </div>

          {/* Accent color */}
          <div className="divider-2" style={{ marginBottom: 18 }} />
          <div className="row" style={{ alignItems: 'flex-start', gap: 24, flexWrap: 'wrap', marginBottom: 22 }}>
            <div style={{ minWidth: 140 }}>
              <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--fg)' }}>Accent color</div>
              <div className="t-label" style={{ marginTop: 2 }}>highlights, focus rings, selection</div>
            </div>
            <div className="col gap-3" style={{ flex: 1, minWidth: 260 }}>
              <AccentSwatches value={accent} onChange={(v) => setTheme({ accent: v })} size={32} />
              <div className="row gap-2" style={{ alignItems: 'center' }}>
                <span className="t-label" style={{ width: 60 }}>hex</span>
                <input
                  key={accent}
                  className="input"
                  defaultValue={accent}
                  onChange={(e) => commitHex(e.target.value)}
                  spellCheck={false}
                  style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, maxWidth: 140, padding: '7px 10px' }}
                />
                <span style={{ width: 32, height: 32, borderRadius: 6, background: accent, border: '1px solid var(--line)' }} />
                <span className="t-label" style={{ marginLeft: 4 }}>{matchedSwatch ? matchedSwatch.name : 'custom'}</span>
              </div>
              <div className="row gap-2" style={{
                padding: '12px 14px', borderRadius: 8,
                background: 'var(--bg-2)', border: '1px solid var(--line-2)',
                flexWrap: 'wrap', alignItems: 'center',
              }}>
                <span className="t-h2" style={{ fontSize: 16 }}><em>preview</em> · headlines</span>
                <span style={{ flex: 1 }} />
                <button className="btn btn-sm btn-accent">primary action</button>
                <button className="btn btn-sm"><span style={{ color: 'var(--accent)' }}>linked text</span></button>
              </div>
            </div>
          </div>

          {/* Logo picker — T8.2 */}
          <div className="divider-2" style={{ marginBottom: 18 }} />
          <div className="t-eyebrow" style={{ marginBottom: 12 }}>── brand</div>
          <div className="row gap-3" style={{ flexWrap: 'wrap', marginBottom: 10 }}>
            {LOGO_VARIANTS.map(({ variant, label, desc }) => {
              const active = logo === variant;
              return (
                <button
                  key={variant}
                  onClick={() => setTheme({ logo: variant })}
                  aria-label={`Use ${label} logo`}
                  style={{
                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
                    padding: '14px 18px', borderRadius: 8,
                    border: active ? '2px solid var(--accent)' : '2px solid var(--line)',
                    background: active ? 'var(--accent-bg)' : 'var(--bg-card)',
                    cursor: 'pointer', minWidth: 110,
                    transition: 'all .12s',
                  }}
                >
                  <Logo variant={variant} size={32} />
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: active ? 'var(--accent)' : 'var(--fg)', fontFamily: 'var(--font-mono)' }}>{label}</div>
                    <div className="t-label" style={{ fontSize: 10.5, marginTop: 2 }}>{desc}</div>
                  </div>
                </button>
              );
            })}
          </div>
          <button className="btn btn-sm" onClick={() => setTheme({ logo: ACTIVE_LOGO })}>
            reset to default
          </button>
        </div>

        {/* ── Avatar ──────────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22 }}>
          <div className="t-eyebrow" style={{ marginBottom: 14 }}>── avatar</div>
          <div className="row gap-4" style={{ marginBottom: 18, flexWrap: 'wrap' }}>
            <div>{user && <Avatar user={user} size="lg" />}</div>
            <div className="col gap-2" style={{ flex: 1, minWidth: 220 }}>
              <div className="row gap-2" style={{ flexWrap: 'wrap' }}>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={handleFileChange}
                />
                <button
                  className="btn"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploadMutation.isPending}
                >
                  <Plus width={13} height={13} />
                  upload image
                </button>
                {hasAvatar && (
                  <button
                    className="btn btn-ghost"
                    onClick={() => deleteAvatarMutation.mutate()}
                    disabled={deleteAvatarMutation.isPending}
                    style={{ color: 'var(--coral)' }}
                  >
                    <Trash2 width={13} height={13} />
                    remove
                  </button>
                )}
              </div>
              <div className="t-label" style={{ fontSize: 11 }}>JPEG, PNG or WebP · max 2 MB · resized to 256×256</div>
              {avatarError && <div className="t-label" style={{ color: 'var(--coral)' }}>{avatarError}</div>}
            </div>
          </div>
          <div className="t-eyebrow" style={{ marginBottom: 10 }}>or pick a preset</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: 10 }}>
            {presets.map((id) => {
              const active = user?.avatarPreset === id && !user?.hasCustomAvatar;
              return (
                <button
                  key={id}
                  onClick={() => presetMutation.mutate(id)}
                  disabled={avatarBusy}
                  title={id}
                  style={{
                    width: '100%', aspectRatio: '1',
                    padding: 0, borderRadius: '50%', overflow: 'hidden',
                    border: active ? '2px solid var(--accent)' : '2px solid transparent',
                    outline: active ? '2px solid color-mix(in oklab, var(--accent) 30%, transparent)' : 'none',
                    outlineOffset: 1,
                    cursor: 'pointer', background: 'transparent', transition: 'all .12s',
                  }}
                >
                  <img src={`/avatars/${id}.svg`} alt={id} style={{ width: '100%', height: '100%', display: 'block', objectFit: 'cover' }} />
                </button>
              );
            })}
          </div>
        </div>

        {/* ── Profile ─────────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22 }}>
          <div className="t-eyebrow" style={{ marginBottom: 14 }}>── profile</div>
          <form onSubmit={handleSave}>
            <div className="col gap-3">
              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>username</div>
                <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} style={{ maxWidth: 380 }} />
              </div>
              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>email</div>
                <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} style={{ maxWidth: 380 }} />
              </div>
              <div>
                <div className="row gap-2" style={{ marginBottom: 6, alignItems: 'center' }}>
                  <div className="t-label">github login</div>
                  {user?.githubLogin
                    ? (user.githubLoginVerified
                        ? <Chip color="emerald" dot>verified</Chip>
                        : <Chip color="coral" dot>not found</Chip>)
                    : null}
                </div>
                <input className="input" value={githubLogin} onChange={(e) => setGithubLogin(e.target.value)} placeholder="your-github-username" style={{ maxWidth: 380 }} />
                <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>
                  github repositories match your commits, pull requests and reviews through your github account automatically
                </div>
              </div>

              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>commit emails</div>
                <div className="col gap-2" style={{ maxWidth: 380 }}>
                  {(commitEmails ?? []).map((ce: CommitEmailDto) => (
                    <div key={ce.id} className="row gap-2" style={{ alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: 13, wordBreak: 'break-all' }}>{ce.email}</span>
                      <button
                        type="button"
                        aria-label={`remove ${ce.email}`}
                        className="btn-icon"
                        onClick={() => removeCommitEmailMutation.mutate(ce.id)}
                        disabled={removeCommitEmailMutation.isPending}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                  {(commitEmails ?? []).length === 0 && (
                    <span className="t-label" style={{ fontSize: 10.5 }}>no addresses declared yet</span>
                  )}
                  <div className="row gap-2">
                    <input
                      className="input"
                      type="email"
                      value={newCommitEmail}
                      onChange={(e) => setNewCommitEmail(e.target.value)}
                      placeholder="you@example.com"
                      style={{ flex: 1 }}
                    />
                    <button
                      type="button"
                      className="btn"
                      onClick={() => newCommitEmail.trim() && addCommitEmailMutation.mutate(newCommitEmail.trim())}
                      disabled={addCommitEmailMutation.isPending || !newCommitEmail.trim()}
                    >
                      <Plus size={14} /> add
                    </button>
                  </div>
                  {commitEmailError && (
                    <span style={{ color: 'var(--coral)', fontSize: 11.5 }}>{commitEmailError}</span>
                  )}
                </div>
                <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>
                  for local repositories, add every address you commit with — including your github noreply address
                </div>
              </div>

              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>jira account id</div>
                <input className="input" value={jiraAccountId} onChange={(e) => setJiraAccountId(e.target.value)} placeholder="5b10a2844c20165700ede21g" style={{ maxWidth: 380 }} />
                <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>
                  filled in automatically when you connect jira with your own token
                </div>
              </div>
              <div ref={tzRef}>
                <div className="t-label" style={{ marginBottom: 6 }}>timezone</div>
                <div className="row gap-2" style={{ position: 'relative', maxWidth: 400 }}>
                  <button
                    type="button"
                    onClick={() => setTzOpen((v) => !v)}
                    className="input"
                    style={{ flex: 1, textAlign: 'left', cursor: 'pointer', display: 'flex', justifyContent: 'space-between' }}
                  >
                    <span>{selectedTz?.label ?? timezone}</span>
                  </button>
                  {timezone === BROWSER_TZ && <Chip color="emerald" dot>auto</Chip>}
                  {tzOpen && (
                    <div style={{
                      position: 'absolute', top: '100%', left: 0, right: 0,
                      background: 'var(--bg-card)', border: '1px solid var(--line)',
                      borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,.12)',
                      zIndex: 20, overflow: 'hidden', marginTop: 4,
                    }}>
                      <div style={{ padding: 8, borderBottom: '1px solid var(--line-2)' }}>
                        <input
                          className="input"
                          placeholder="Search by city or offset…"
                          value={tzSearch}
                          onChange={(e) => setTzSearch(e.target.value)}
                          autoFocus
                        />
                      </div>
                      <div style={{ maxHeight: 220, overflowY: 'auto' }}>
                        {filteredTz.map((o) => (
                          <button
                            key={o.iana}
                            type="button"
                            onClick={() => { setTimezone(o.iana); setTzSearch(''); setTzOpen(false); }}
                            style={{
                              width: '100%', textAlign: 'left', padding: '8px 12px',
                              fontSize: 12.5, background: o.iana === timezone ? 'var(--bg-2)' : 'transparent',
                              color: o.iana === timezone ? 'var(--accent)' : 'var(--fg)',
                              border: 'none', cursor: 'pointer',
                            }}
                          >{o.label}</button>
                        ))}
                        {filteredTz.length === 0 && (
                          <div className="t-label" style={{ textAlign: 'center', padding: 16 }}>No matches</div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
                <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>used for after-hours commit metrics</div>
              </div>
              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>role</div>
                <Chip color={ROLE_CHIP[user?.role ?? 'DEVELOPER']}>{user?.role?.toLowerCase() ?? 'developer'}</Chip>
              </div>
            </div>

            {saveStatus === 'success' && (
              <div className="t-label" style={{ color: 'var(--emerald)', marginTop: 10 }}>Profile saved successfully.</div>
            )}
            {saveStatus === 'error' && (
              <div className="t-label" style={{ color: 'var(--coral)', marginTop: 10 }}>{saveError}</div>
            )}

            <div className="row gap-2" style={{ marginTop: 18 }}>
              <button type="submit" className="btn btn-accent" disabled={updateMutation.isPending}>save changes</button>
            </div>
          </form>
        </div>

        {/* ── Security ────────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22 }}>
          <div className="t-eyebrow" style={{ marginBottom: 14 }}>── security</div>
          <div className="row" style={{ justifyContent: 'space-between', padding: '4px 0 10px' }}>
            <div>
              <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--fg)' }}>Session</div>
              <div className="t-label" style={{ marginTop: 2 }}>Stateless JWT · stored locally</div>
            </div>
            <Chip color="emerald" dot>Active</Chip>
          </div>
          <button
            onClick={() => { setPwOpen((v) => !v); setPwStatus('idle'); }}
            className="btn"
            style={{
              width: '100%', justifyContent: 'flex-start', padding: '12px 14px',
              background: pwOpen ? 'var(--bg-2)' : 'var(--bg-card)',
            }}
          >
            <Lock width={13} height={13} />
            <span style={{ flex: 1, textAlign: 'left' }}>Change password</span>
            <span style={{ fontSize: 10, color: 'var(--fg-3)', transform: pwOpen ? 'rotate(180deg)' : 'none', transition: 'transform .15s', display: 'inline-block' }}>▼</span>
          </button>
          {pwOpen && (
            <form
              onSubmit={handlePasswordChange}
              style={{
                borderLeft: '2px solid var(--accent)',
                marginLeft: 8, marginTop: 12, paddingLeft: 16,
              }}
            >
              <div className="col gap-3">
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>current password</div>
                  <input className="input" type="password" placeholder="Enter current password" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} style={{ maxWidth: 380 }} required />
                </div>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>new password</div>
                  <div className="row gap-2" style={{ maxWidth: 380 }}>
                    <div style={{ position: 'relative', flex: 1 }}>
                      <input className="input" type={showNew ? 'text' : 'password'} placeholder="At least 8 characters" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} style={{ paddingRight: 36 }} required />
                      <button type="button" onClick={() => setShowNew((v) => !v)} style={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--fg-3)', padding: 4 }}>
                        {showNew ? <EyeOff width={13} height={13} /> : <Eye width={13} height={13} />}
                      </button>
                    </div>
                  </div>
                </div>
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>confirm new password</div>
                  <div className="row gap-2" style={{ maxWidth: 380 }}>
                    <div style={{ position: 'relative', flex: 1 }}>
                      <input className="input" type={showConfirm ? 'text' : 'password'} placeholder="Repeat new password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} style={{ paddingRight: 36 }} required />
                      <button type="button" onClick={() => setShowConfirm((v) => !v)} style={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--fg-3)', padding: 4 }}>
                        {showConfirm ? <EyeOff width={13} height={13} /> : <Eye width={13} height={13} />}
                      </button>
                    </div>
                  </div>
                </div>
                {pwStatus === 'success' && <div className="t-label" style={{ color: 'var(--emerald)' }}>Password changed successfully.</div>}
                {pwStatus === 'error' && <div className="t-label" style={{ color: 'var(--coral)' }}>{pwError}</div>}
                <div className="row gap-2" style={{ marginTop: 4 }}>
                  <button type="submit" className="btn btn-sm btn-accent" disabled={changePasswordMutation.isPending}>update password</button>
                  <button type="button" className="btn btn-sm" onClick={() => { setOldPassword(''); setNewPassword(''); setConfirmPassword(''); setShowNew(false); setShowConfirm(false); setPwStatus('idle'); setPwError(''); setPwOpen(false); }}>cancel</button>
                </div>
              </div>
            </form>
          )}
        </div>

        {/* ── Notifications ────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22 }}>
          <div className="t-eyebrow" style={{ marginBottom: 14 }}>── notifications</div>
          {([
            ['aiBrief',        'Weekly AI brief',       'Every Monday at 09:00'],
            ['syncFailures',   'Sync failures',         'When a data source fails'],
            ['afterHours',     'After-hours commits',   'When > 15% in a 7-day window'],
            ['newTeamMember',  'New team member',       'When a manager adds you'],
          ] as const).map(([key, label, sub], i) => (
            <div key={key} className="row" style={{ padding: '12px 0', borderTop: i === 0 ? 'none' : '1px solid var(--line-2)', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--fg)' }}>{label}</div>
                <div className="t-label" style={{ marginTop: 2 }}>{sub}</div>
              </div>
              <button
                onClick={() => toggleNotif(key)}
                disabled={notifMutation.isPending}
                aria-label={`Toggle ${label}`}
                style={{
                  width: 36, height: 20, borderRadius: 999, padding: 0,
                  background: notifs[key] ? 'var(--accent)' : 'var(--bg-inset)',
                  border: '1px solid ' + (notifs[key] ? 'var(--accent)' : 'var(--line)'),
                  cursor: 'pointer', position: 'relative',
                }}
              >
                <span style={{
                  position: 'absolute', top: 1, left: notifs[key] ? 17 : 1,
                  width: 16, height: 16, borderRadius: 999,
                  background: 'white', transition: 'left .15s',
                }} />
              </button>
            </div>
          ))}
          <div className="row" style={{ padding: '12px 0', borderTop: '1px solid var(--line-2)', gap: 14 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--fg)' }}>Default contact method</div>
              <div className="t-label" style={{ marginTop: 2 }}>Which option appears first when messaging a team member</div>
            </div>
            <div className="row gap-1">
              {(['IN_APP', 'EMAIL'] as const).map((method) => (
                <button
                  key={method}
                  className="btn btn-sm"
                  onClick={() => setContactMethod(method)}
                  disabled={notifMutation.isPending}
                  aria-pressed={notifs.defaultContactMethod === method}
                  style={{
                    background: notifs.defaultContactMethod === method ? 'var(--accent)' : 'var(--bg-inset)',
                    borderColor: notifs.defaultContactMethod === method ? 'var(--accent)' : 'var(--line)',
                    color: notifs.defaultContactMethod === method ? 'var(--accent-fg, #fff)' : 'var(--fg)',
                  }}
                >
                  {method === 'IN_APP' ? 'In-app message' : 'Email'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── Danger zone ──────────────────────────────────────────────────── */}
        <div className="card" style={{ padding: 22, borderColor: 'color-mix(in oklab, var(--coral) 30%, var(--line))' }}>
          <div className="t-eyebrow" style={{ marginBottom: 6, color: 'var(--coral)' }}>── danger zone</div>
          <p className="t-body" style={{ marginBottom: 12 }}>
            Permanently delete your account and all collected metrics. This action cannot be undone.
          </p>
          <button
            className="btn"
            style={{ color: 'var(--coral)', borderColor: 'color-mix(in oklab, var(--coral) 40%, var(--line))' }}
            onClick={openDeleteModal}
          >
            <Trash2 width={13} height={13} />
            delete account
          </button>
        </div>

      </div>
      <div style={{ height: 32 }} />

      {/* ── Delete account confirmation modal ────────────────────────────── */}
      <Modal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        title="Delete account"
        eyebrow="── danger zone"
        width={480}
        footer={
          <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-sm" onClick={() => setDeleteModalOpen(false)}>
              cancel
            </button>
            <button
              className="btn btn-sm"
              style={{ color: 'var(--coral)', borderColor: 'color-mix(in oklab, var(--coral) 40%, var(--line))' }}
              onClick={handleDeleteConfirm}
              disabled={deleteEmailInput !== (user?.email ?? '') || deleteAccountMutation.isPending}
            >
              <Trash2 width={12} height={12} />
              {deleteAccountMutation.isPending ? 'Deleting…' : 'Delete permanently'}
            </button>
          </div>
        }
      >
        <div className="col gap-3">
          <p className="t-body">
            This will permanently delete your account, all collected metrics, datasource configurations, and team memberships.
            <strong> This cannot be undone.</strong>
          </p>
          <div>
            <div className="t-label" style={{ marginBottom: 6 }}>Type your email to confirm:</div>
            <input
              className="input"
              type="email"
              placeholder={user?.email ?? ''}
              value={deleteEmailInput}
              onChange={(e) => { setDeleteEmailInput(e.target.value); setDeleteError(''); }}
              autoFocus
            />
          </div>
          {deleteError && (
            <div className="t-label" style={{ color: 'var(--coral)' }}>{deleteError}</div>
          )}
        </div>
      </Modal>
    </div>
  );
}
