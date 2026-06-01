import { useState, useEffect, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';
import { APP_VERSION } from '@/config/branding';
import { adminApi } from '@/api/admin';
import type { InviteInfoDto } from '@/api/admin';

export function RegisterPage() {
  const { register } = useAuth();
  const { logo } = useTheme();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const inviteToken = searchParams.get('invite') ?? undefined;
  const [inviteInfo, setInviteInfo] = useState<InviteInfoDto | null>(null);
  const [inviteWarning, setInviteWarning] = useState<string | null>(null);

  useEffect(() => {
    if (!inviteToken) return;
    adminApi.getInviteInfo(inviteToken)
      .then((res) => {
        setInviteInfo(res.data);
        setEmail(res.data.email);
      })
      .catch(() => {
        setInviteWarning('This invite link is invalid or has expired. You can still register normally.');
      });
  }, [inviteToken]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register({ username, email, password, inviteToken });
      navigate('/login');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: '60px 64px', display: 'flex', flexDirection: 'column', flex: 1, maxWidth: 560, margin: '0 auto', width: '100%' }}>
        <div style={{ flex: '0 0 auto', marginBottom: 48 }}>
          <Logo variant={logo} size={36} withWordmark />
        </div>

        <div style={{ flex: '1 1 auto' }}>
          <div className="t-eyebrow" style={{ marginBottom: 12 }}>── create account</div>
          <h1 style={{
            fontFamily: 'var(--font-sans)',
            fontSize: 36, lineHeight: 1.1,
            fontWeight: 500, letterSpacing: '-0.02em',
            color: 'var(--fg)', marginBottom: 28,
          }}>
            Start tracking your metrics.
          </h1>

          {inviteInfo && (
            <div style={{
              padding: '10px 14px', borderRadius: 6, marginBottom: 20,
              background: 'color-mix(in oklab, var(--accent) 8%, var(--bg))',
              border: '1px solid color-mix(in oklab, var(--accent) 25%, var(--line))',
              fontSize: 13, color: 'var(--fg)',
            }}>
              You were invited to join
              {inviteInfo.teamName ? <strong> {inviteInfo.teamName}</strong> : ' this workspace'}
              {' '}as <strong>{inviteInfo.role.toLowerCase()}</strong>.
            </div>
          )}

          {inviteWarning && (
            <div style={{
              padding: '10px 14px', borderRadius: 6, marginBottom: 20,
              background: 'color-mix(in oklab, var(--amber) 8%, var(--bg))',
              border: '1px solid color-mix(in oklab, var(--amber) 25%, var(--line))',
              fontSize: 12, color: 'var(--amber)',
            }}>
              {inviteWarning}
            </div>
          )}

          <form onSubmit={handleSubmit} className="col gap-3" style={{ maxWidth: 400 }}>
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>username</div>
              <input
                className="input"
                type="text"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>email</div>
              <input
                className="input"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div>
              <div className="t-eyebrow" style={{ marginBottom: 6 }}>password</div>
              <input
                className="input"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
              />
              <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>at least 6 characters</div>
            </div>

            {error && (
              <p style={{
                fontSize: 12, margin: 0,
                color: 'var(--coral)',
                background: 'color-mix(in oklab, var(--coral) 10%, var(--bg))',
                border: '1px solid color-mix(in oklab, var(--coral) 25%, var(--line))',
                borderRadius: 6, padding: '8px 12px',
              }}>
                {error}
              </p>
            )}

            <button
              type="submit"
              className="btn btn-accent"
              disabled={loading}
              style={{ marginTop: 8, justifyContent: 'center', padding: '10px 16px', fontSize: 13 }}
            >
              {loading ? 'creating account…' : 'create account →'}
            </button>

            <div className="row gap-2" style={{ marginTop: 4, justifyContent: 'center' }}>
              <span className="t-label">already have an account?</span>
              <Link to="/login" className="t-label" style={{ color: 'var(--accent)' }}>sign in</Link>
            </div>
          </form>
        </div>

        <div className="t-label" style={{ fontSize: 10, marginTop: 32 }}>
          dev·analytics · {APP_VERSION} · self-hosted
        </div>
      </div>
    </div>
  );
}
