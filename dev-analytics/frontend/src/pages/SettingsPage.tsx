import { useState, useEffect, useRef, useMemo, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useAuth } from '@/context/AuthContext';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { User, Shield, AlertCircle, CheckCircle, ChevronDown } from 'lucide-react';
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
  const [username, setUsername] = useState(user?.username ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [tzSearch, setTzSearch] = useState('');
  const [tzOpen, setTzOpen] = useState(false);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [saveError, setSaveError] = useState('');

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
      if (tzRef.current && !tzRef.current.contains(e.target as Node)) {
        setTzOpen(false);
      }
    }
    if (tzOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [tzOpen]);

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
            <Input
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Your username"
            />
            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="your@email.com"
            />
            <div>
              <p className="text-sm font-medium text-gray-700 mb-1.5">Role</p>
              <div className="flex flex-wrap gap-2">
                {user?.role && (
                  <Badge color={ROLE_COLORS[user.role] ?? 'gray'}>{user.role}</Badge>
                )}
              </div>
            </div>

            {/* Timezone picker — collapsed dropdown */}
            <div ref={tzRef} className="relative">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Timezone
                <span className="ml-1 text-xs text-gray-400 font-normal">
                  — used for after-hours commit metrics
                </span>
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
                    <Input
                      placeholder="Search by city or offset…"
                      value={tzSearch}
                      onChange={(e) => setTzSearch(e.target.value)}
                      autoFocus
                    />
                  </div>
                  <div className="max-h-56 overflow-y-auto">
                    {filteredTz.map((o) => (
                      <button
                        key={o.iana}
                        type="button"
                        onClick={() => { setTimezone(o.iana); setTzSearch(''); setTzOpen(false); }}
                        className={`w-full text-left px-3 py-2 text-sm transition-colors ${
                          o.iana === timezone
                            ? 'bg-violet-50 text-violet-700 font-medium'
                            : 'text-gray-800 hover:bg-gray-50'
                        }`}
                      >
                        {o.label}
                      </button>
                    ))}
                    {filteredTz.length === 0 && (
                      <p className="text-sm text-gray-400 text-center py-4">No matches</p>
                    )}
                  </div>
                </div>
              )}
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
