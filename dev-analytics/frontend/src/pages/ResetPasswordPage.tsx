import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';
import { APP_VERSION } from '@/config/branding';
import api from '@/lib/api';

export function ResetPasswordPage() {
  const { logo } = useTheme();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/reset-password', { token, newPassword: password });
      setDone(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Failed to reset password. The link may have expired.');
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
          <div className="t-eyebrow" style={{ marginBottom: 12 }}>── reset password</div>
          <h1 style={{
            fontFamily: 'var(--font-sans)',
            fontSize: 36, lineHeight: 1.1,
            fontWeight: 500, letterSpacing: '-0.02em',
            color: 'var(--fg)', marginBottom: 28,
          }}>
            Choose a new password
          </h1>

          {!token ? (
            <div className="card" style={{ padding: 24, maxWidth: 400 }}>
              <div className="t-eyebrow" style={{ color: 'var(--coral)', marginBottom: 8 }}>── invalid link</div>
              <p className="t-body" style={{ color: 'var(--fg-2)' }}>
                This reset link is missing its token. Please request a new one.
              </p>
              <Link to="/forgot-password" className="btn" style={{ marginTop: 16, display: 'inline-flex' }}>
                request new link →
              </Link>
            </div>
          ) : done ? (
            <div className="card" style={{ padding: 24, maxWidth: 400 }}>
              <div className="t-eyebrow" style={{ color: 'var(--emerald)', marginBottom: 8 }}>── password updated</div>
              <p className="t-body" style={{ color: 'var(--fg-2)', marginBottom: 16 }}>
                Your password has been reset. You can now sign in with your new password.
              </p>
              <button className="btn btn-accent" onClick={() => navigate('/login')}
                style={{ justifyContent: 'center' }}>
                sign in →
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="col gap-3" style={{ maxWidth: 400 }}>
              <div>
                <div className="t-eyebrow" style={{ marginBottom: 6 }}>new password</div>
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  minLength={8}
                  required
                />
              </div>
              <div>
                <div className="t-eyebrow" style={{ marginBottom: 6 }}>confirm password</div>
                <input
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  minLength={8}
                  required
                />
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
                {loading ? 'saving…' : 'set new password →'}
              </button>
            </form>
          )}

          <div className="row gap-2" style={{ marginTop: 16 }}>
            <Link to="/login" className="t-label" style={{ color: 'var(--accent)' }}>← back to sign in</Link>
          </div>
        </div>

        <div className="t-label" style={{ fontSize: 10, marginTop: 32 }}>
          dev·analytics · {APP_VERSION} · self-hosted
        </div>
      </div>
    </div>
  );
}