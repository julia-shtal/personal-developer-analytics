import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';
import { APP_VERSION } from '@/config/branding';
import api from '@/lib/api';

export function ForgotPasswordPage() {
  const { logo } = useTheme();
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/forgot-password', { email });
      setSent(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Failed to send reset email. Please try again.');
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
            Forgot your password?
          </h1>

          {sent ? (
            <div className="card" style={{ padding: 24, maxWidth: 400 }}>
              <div className="t-eyebrow" style={{ color: 'var(--emerald)', marginBottom: 8 }}>── link sent</div>
              <p style={{ fontSize: 13.5, color: 'var(--fg)', fontWeight: 500, marginBottom: 6 }}>Check your email.</p>
              <p className="t-body" style={{ color: 'var(--fg-2)' }}>
                If an account with <strong>{email}</strong> exists, a reset link has been sent. It's valid for 24 hours.
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="col gap-3" style={{ maxWidth: 400 }}>
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
                <div className="t-label" style={{ marginTop: 4, fontSize: 10.5 }}>
                  we'll send a reset link valid for 24 hours
                </div>
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
                {loading ? 'sending…' : 'send reset link →'}
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