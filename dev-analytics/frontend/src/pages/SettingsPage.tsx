import { useState, useEffect, useMemo, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { User, Shield, AlertCircle, CheckCircle } from 'lucide-react';
import api from '@/lib/api';
import type { UserProfile } from '@/types';

const ROLE_COLORS = {
  DEVELOPER: 'violet' as const,
  MANAGER: 'blue' as const,
  ADMIN: 'amber' as const,
};

// ─── Timezone helpers ────────────────────────────────────────────────────────

interface TzOption {
  iana: string;       // e.g. "Europe/Kyiv"
  offset: string;     // e.g. "UTC+3"
  offsetMinutes: number;
  label: string;      // e.g. "UTC+3 — Europe/Kyiv"
}

function buildTimezoneOptions(): TzOption[] {
  const now = new Date();

  // Intl.supportedValuesOf is available in all modern browsers
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
        // rawOffset is like "GMT+3", "GMT-5:30", "GMT+0" — normalise to "UTC+3"
        const offset = rawOffset.replace('GMT', 'UTC').replace('UTC+0', 'UTC');

        // Parse to minutes so we can sort numerically
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
  const { user } = useAuth();
  const [timezone, setTimezone] = useState(user?.timezone ?? 'UTC');
  const [githubLogin, setGithubLogin] = useState(user?.githubLogin ?? '');
  const [tzSearch, setTzSearch] = useState('');
  const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [saveError, setSaveError] = useState('');

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
  }, [user]);

  const updateMutation = useMutation({
    mutationFn: (body: { timezone?: string; githubLogin?: string }) =>
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

  function handleSave(e: FormEvent) {
    e.preventDefault();
    setSaveStatus('idle');
    updateMutation.mutate({ timezone: timezone || undefined, githubLogin: githubLogin || undefined });
  }

  // Find current option label for the selected timezone
  const selectedOption = tzOptions.find((o) => o.iana === timezone);

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500 mt-0.5">Your profile and account preferences</p>
      </div>

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
            <Input label="Username" value={user?.username ?? ''} readOnly className="bg-gray-50" />
            <Input label="Email" value={user?.email ?? ''} readOnly className="bg-gray-50" />
            <div>
              <p className="text-sm font-medium text-gray-700 mb-1.5">Role</p>
              <div className="flex flex-wrap gap-2">
                {user?.role && (
                  <Badge color={ROLE_COLORS[user.role] ?? 'gray'}>{user.role}</Badge>
                )}
              </div>
            </div>

            {/* Timezone picker with search */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Timezone
                <span className="ml-1 text-xs text-gray-400 font-normal">
                  — used for after-hours commit metrics
                </span>
              </label>

              {selectedOption && (
                <p className="text-xs text-violet-700 bg-violet-50 rounded-lg px-3 py-1.5 mb-2 font-medium">
                  Current: {selectedOption.label}
                </p>
              )}

              <Input
                placeholder="Search by city or offset, e.g. Berlin or UTC+3"
                value={tzSearch}
                onChange={(e) => setTzSearch(e.target.value)}
              />

              <select
                value={timezone}
                onChange={(e) => {
                  setTimezone(e.target.value);
                  setTzSearch('');
                }}
                size={6}
                className="mt-1.5 w-full rounded-lg border border-gray-300 bg-white px-3 py-1 text-sm text-gray-900 focus:border-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-500/20"
              >
                {filteredTz.map((o) => (
                  <option key={o.iana} value={o.iana}>
                    {o.label}
                  </option>
                ))}
              </select>
              <p className="mt-1 text-xs text-gray-400">
                {filteredTz.length} timezone{filteredTz.length !== 1 ? 's' : ''} shown
              </p>
            </div>

            <Input
              label="GitHub login"
              value={githubLogin}
              onChange={(e) => setGithubLogin(e.target.value)}
              placeholder="your-github-username"
            />
            <p className="text-xs text-gray-400 -mt-2">
              Used to link commits and PRs to your account when collecting from shared repositories.
            </p>

            {saveStatus === 'success' && (
              <div className="flex items-center gap-2 text-sm text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                <CheckCircle className="h-4 w-4 flex-shrink-0" />
                Profile saved successfully.
              </div>
            )}
            {saveStatus === 'error' && (
              <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                {saveError}
              </div>
            )}

            <Button type="submit" loading={updateMutation.isPending}>
              Save changes
            </Button>
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
          <div className="space-y-3">
            <div className="flex items-center justify-between py-2">
              <div>
                <p className="text-sm font-medium text-gray-700">Session</p>
                <p className="text-xs text-gray-400">
                  Stateless JWT — tokens are stored locally in this browser
                </p>
              </div>
              <Badge color="emerald">Active</Badge>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
