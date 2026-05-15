import { useState, useEffect, useRef, useMemo, type FormEvent, type ChangeEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import { User, Shield, AlertCircle, CheckCircle, ChevronDown, Lock, Eye, EyeOff, Upload, Trash2 } from 'lucide-react';
import api from '@/lib/api';
import { avatarApi } from '@/api/avatar';
import type { UserProfile } from '@/types';
import clsx from 'clsx';

const ROLE_COLORS = {
  DEVELOPER: 'violet' as const,
  MANAGER: 'blue' as const,
  ADMIN: 'amber' as const,
};

// ─── Timezone helpers ────────────────────────────────────────────────────────

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
      } catch {
        return null;
      }
    })
    .filter((o): o is TzOption => o !== null)
    .sort((a, b) => a.offsetMinutes - b.offsetMinutes || a.iana.localeCompare(b.iana));
}

// ─── Component ───────────────────────────────────────────────────────────────

export function SettingsPage() {
  const { user, refreshUser } = useAuth();

  const [timezone, setTimezone] = useState(user?.timezone ?? 'UTC');
  const [githubLogin, setGithubLogin] = useState(user?.githubLogin ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [tzSearch, setTzSearch] = useState('');
  const [tzOpen, setTzOpen] = useState(false);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [saveError, setSaveError] = useState('');
  const [avatarError, setAvatarError] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);
  const tzRef = useRef<HTMLDivElement>(null);

  const tzOptions = useMemo(buildTimezoneOptions, []);
  const filteredTz = useMemo(() => {
    const q = tzSearch.toLowerCase();
    if (!q) return tzOptions;
    return tzOptions.filter(
      (o) => o.iana.toLowerCase().includes(q) || o.offset.toLowerCase().includes(q)
    );
  }, [tzOptions, tzSearch]);

  useEffect(() => {
    setTimezone(user?.timezone ?? 'UTC');
    setGithubLogin(user?.githubLogin ?? '');
    setUsername(user?.username ?? '');
    setEmail(user?.email ?? '');
  }, [user]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (tzRef.current && !tzRef.current.contains(e.target as Node)) setTzOpen(false);
    }
    if (tzOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [tzOpen]);

  // ── Password change state ────────────────────────────────────────────────
  const [pwOpen, setPwOpen] = useState(false);
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwStatus, setPwStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [pwError, setPwError] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

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
    mutationFn: (body: { username?: string; email?: string; timezone?: string; githubLogin?: string }) =>
      api.put<UserProfile>('/users/me', body),
    onSuccess: () => {
      setSaveStatus('success');
      setSaveError('');
      setTimeout(() => setSaveStatus('idle'), 3000);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setSaveError(msg ?? 'Failed to save profile.');
      setSaveStatus('error');
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
    });
  }

  const selectedOption = tzOptions.find((o) => o.iana === timezone);
  const avatarBusy = uploadMutation.isPending || presetMutation.isPending || deleteAvatarMutation.isPending;
  const hasAvatar = user?.hasCustomAvatar || !!user?.avatarPreset;

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500 mt-0.5">Your profile and account preferences</p>
      </div>

      {/* Avatar card */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <User className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">Avatar</h2>
          </div>
        </CardHeader>
        <CardBody>
          <div className="space-y-4">
            {/* Current avatar + actions */}
            <div className="flex items-center gap-4">
              {user && <Avatar user={user} size="lg" />}
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                    onChange={handleFileChange}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    loading={uploadMutation.isPending}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload className="h-3.5 w-3.5" />
                    Upload image
                  </Button>
                  {hasAvatar && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      loading={deleteAvatarMutation.isPending}
                      onClick={() => deleteAvatarMutation.mutate()}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      Remove
                    </Button>
                  )}
                </div>
                <p className="text-xs text-gray-400">JPEG, PNG or WebP · max 2 MB · resized to 256×256</p>
              </div>
            </div>

            {avatarError && (
              <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                {avatarError}
              </div>
            )}

            {/* Preset picker */}
            <div>
              <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">Or choose a preset</p>
              <div className="flex flex-wrap gap-2">
                {presets.map((id) => {
                  const active = user?.avatarPreset === id;
                  return (
                    <button
                      key={id}
                      type="button"
                      disabled={avatarBusy}
                      onClick={() => presetMutation.mutate(id)}
                      className={clsx(
                        'w-10 h-10 rounded-full overflow-hidden border-2 transition-all focus:outline-none focus:ring-2 focus:ring-violet-500/40',
                        active ? 'border-violet-500 ring-2 ring-violet-300' : 'border-transparent hover:border-gray-300',
                        avatarBusy && 'opacity-50 cursor-not-allowed',
                      )}
                      title={id}
                    >
                      <img
                        src={`/avatars/${id}.svg`}
                        alt={id}
                        className="w-full h-full object-cover"
                      />
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Profile card */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <User className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">Profile</h2>
          </div>
        </CardHeader>
        <CardBody>
          <form onSubmit={handleSave} className="space-y-4">
            <Input label="Username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Your username" />
            <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="your@email.com" />
            <div>
              <p className="text-sm font-medium text-gray-700 mb-1.5">Role</p>
              <div className="flex flex-wrap gap-2">
                {user?.role && <Badge color={ROLE_COLORS[user.role] ?? 'gray'} dot>{user.role}</Badge>}
              </div>
            </div>

            {/* Timezone picker */}
            <div ref={tzRef} className="relative">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Timezone
                <span className="ml-1 text-xs text-gray-400 font-normal">— used for after-hours commit metrics</span>
              </label>
              <button
                type="button"
                onClick={() => setTzOpen((v) => !v)}
                className="w-full flex items-center justify-between px-3 py-2 rounded-lg border border-gray-300 bg-white text-sm text-gray-900 hover:border-violet-400 focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-500 transition-colors"
              >
                <span>{selectedOption?.label ?? timezone}</span>
                <ChevronDown className={`h-4 w-4 text-gray-400 transition-transform ${tzOpen ? 'rotate-180' : ''}`} />
              </button>
              {tzOpen && (
                <div className="absolute z-20 mt-1 w-full bg-white rounded-xl border border-gray-200 shadow-lg overflow-hidden">
                  <div className="p-2 border-b border-gray-100">
                    <Input placeholder="Search by city or offset…" value={tzSearch} onChange={(e) => setTzSearch(e.target.value)} autoFocus />
                  </div>
                  <div className="max-h-56 overflow-y-auto">
                    {filteredTz.map((o) => (
                      <button
                        key={o.iana}
                        type="button"
                        onClick={() => { setTimezone(o.iana); setTzSearch(''); setTzOpen(false); }}
                        className={`w-full text-left px-3 py-2 text-sm transition-colors ${
                          o.iana === timezone ? 'bg-violet-50 text-violet-700 font-medium' : 'text-gray-800 hover:bg-gray-50'
                        }`}
                      >
                        {o.label}
                      </button>
                    ))}
                    {filteredTz.length === 0 && <p className="text-sm text-gray-400 text-center py-4">No matches</p>}
                  </div>
                </div>
              )}
            </div>

            <Input label="GitHub login" value={githubLogin} onChange={(e) => setGithubLogin(e.target.value)} placeholder="your-github-username" />
            <p className="text-xs text-gray-400 -mt-2">Used to link commits and PRs to your account when collecting from shared repositories.</p>

            {saveStatus === 'success' && (
              <div className="flex items-center gap-2 text-sm text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                <CheckCircle className="h-4 w-4 flex-shrink-0" />Profile saved successfully.
              </div>
            )}
            {saveStatus === 'error' && (
              <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />{saveError}
              </div>
            )}

            <Button type="submit" loading={updateMutation.isPending}>Save changes</Button>
          </form>
        </CardBody>
      </Card>

      {/* Security card */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Shield className="h-4 w-4 text-violet-600" />
            <h2 className="text-sm font-semibold text-gray-900">Security</h2>
          </div>
        </CardHeader>
        <CardBody>
          <div className="space-y-4">
            <div className="flex items-center justify-between py-2">
              <div>
                <p className="text-sm font-medium text-gray-700">Session</p>
                <p className="text-xs text-gray-400">Stateless JWT — tokens are stored locally in this browser</p>
              </div>
              <Badge color="emerald">Active</Badge>
            </div>

            <div className="border border-gray-100 rounded-lg overflow-hidden">
              <button
                type="button"
                onClick={() => { setPwOpen((v) => !v); setPwStatus('idle'); }}
                className="w-full flex items-center justify-between px-4 py-3 text-left bg-gray-50 hover:bg-gray-100 transition-colors"
              >
                <div className="flex items-center gap-2">
                  <Lock className="h-4 w-4 text-gray-500" />
                  <span className="text-sm font-medium text-gray-700">Change password</span>
                </div>
                <ChevronDown className={`h-4 w-4 text-gray-400 transition-transform duration-200 ${pwOpen ? 'rotate-180' : ''}`} />
              </button>

              {pwOpen && (
                <form onSubmit={handlePasswordChange} className="px-4 py-4 space-y-3 border-t border-gray-100">
                  <Input label="Current password" type="password" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} placeholder="Enter current password" required />
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">New password</label>
                    <div className="relative">
                      <Input type={showNew ? 'text' : 'password'} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="At least 8 characters" required className="pr-10" />
                      <button type="button" onClick={() => setShowNew((v) => !v)} className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600" tabIndex={-1}>
                        {showNew ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Confirm new password</label>
                    <div className="relative">
                      <Input type={showConfirm ? 'text' : 'password'} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Repeat new password" required className="pr-10" />
                      <button type="button" onClick={() => setShowConfirm((v) => !v)} className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600" tabIndex={-1}>
                        {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {pwStatus === 'success' && (
                    <div className="flex items-center gap-2 text-sm text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                      <CheckCircle className="h-4 w-4 flex-shrink-0" />Password changed successfully.
                    </div>
                  )}
                  {pwStatus === 'error' && (
                    <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                      <AlertCircle className="h-4 w-4 flex-shrink-0" />{pwError}
                    </div>
                  )}

                  <div className="flex items-center gap-2">
                    <Button type="submit" loading={changePasswordMutation.isPending} size="sm">Update password</Button>
                    <Button type="button" variant="ghost" size="sm" onClick={() => { setOldPassword(''); setNewPassword(''); setConfirmPassword(''); setShowNew(false); setShowConfirm(false); setPwStatus('idle'); setPwError(''); setPwOpen(false); }}>Cancel</Button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}