import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';
import { Chip } from '@/components/ui/Chip';
import { AI, Jira, Folder, MergeFreq, Github } from '@/components/icons';
import { APP_VERSION } from '@/config/branding';

type MockSource = { name: string; sync: string; icon: ReactNode; color: string };
type MockMember = { initials: string; color: string; name: string; v: number };

const PREVIEW_SOURCES: MockSource[] = [
  { name: 'Work GitHub',    sync: '4m ago',  icon: <Github width={14} height={14} />, color: 'violet' },
  { name: 'Acme Jira',      sync: '14m ago', icon: <Jira width={14} height={14} />,    color: 'cyan'   },
  { name: 'monorepo.local', sync: '3d ago',  icon: <Folder width={14} height={14} />,  color: 'amber'  },
];

const PREVIEW_MEMBERS: MockMember[] = [
  { initials: 'AK', color: 'violet', name: 'aisha.k',  v: 89 },
  { initials: 'MT', color: 'cyan',   name: 'marcus.t', v: 72 },
  { initials: 'LF', color: 'amber',  name: 'leo.fern', v: 64 },
];

export function LoginPage() {
  const { login } = useAuth();
  const { logo } = useTheme();
  const navigate = useNavigate();
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login({ usernameOrEmail, password });
      navigate('/welcome');
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      console.error('[Login] error:', err);
      if (status === 401 || status === 403) {
        setError('Invalid username/email or password.');
      } else if (status != null) {
        setError(`Login failed (HTTP ${status}). Check browser console for details.`);
      } else {
        setError('Could not reach the server. Is Spring Boot running?');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-split" style={{
      minHeight: '100vh',
      display: 'grid',
      gridTemplateColumns: '1fr 1.1fr',
      background: 'var(--bg)',
    }}>
      {/* Left: form */}
      <div style={{ padding: '60px 64px', display: 'flex', flexDirection: 'column' }}>
        <div style={{ flex: '0 0 auto' }}>
          <Logo variant={logo} size={36} withWordmark />
        </div>

        <div style={{ flex: '1 1 auto', display: 'flex', alignItems: 'center' }}>
          <div style={{ maxWidth: 400, width: '100%' }}>
            <div className="t-eyebrow" style={{ marginBottom: 12 }}>── sign in</div>
            <h1 style={{
              fontFamily: 'var(--font-sans)',
              fontSize: 36, lineHeight: 1.1,
              fontWeight: 500, letterSpacing: '-0.02em',
              color: 'var(--fg)', marginBottom: 24,
            }}>
              Welcome back.
            </h1>

            <form onSubmit={handleSubmit} className="col gap-3">
              <div>
                <div className="t-eyebrow" style={{ marginBottom: 6 }}>email or username</div>
                <input
                  className="input"
                  type="text"
                  autoComplete="username"
                  value={usernameOrEmail}
                  onChange={(e) => setUsernameOrEmail(e.target.value)}
                  required
                />
              </div>
              <div>
                <div className="row" style={{ justifyContent: 'space-between', marginBottom: 6 }}>
                  <span className="t-eyebrow">password</span>
                  <Link to="/forgot-password" className="t-label" style={{ color: 'var(--accent)' }}>forgot?</Link>
                </div>
                <input
                  className="input"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
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
                {loading ? 'signing in…' : 'sign in →'}
              </button>

              <div className="row gap-2" style={{ marginTop: 4, justifyContent: 'center' }}>
                <span className="t-label">no account?</span>
                <Link to="/register" className="t-label" style={{ color: 'var(--accent)' }}>create one</Link>
              </div>
            </form>
          </div>
        </div>

        <div className="t-label" style={{ fontSize: 10 }}>
          dev·analytics · {APP_VERSION} · self-hosted
        </div>
      </div>

      {/* Right: editorial showcase — decorative only, no live data */}
      <div className="login-showcase" style={{
        background: 'var(--bg-inset)',
        padding: '52px 56px',
        position: 'relative',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        gap: 28,
      }}>
        {/* Horizontal-line texture */}
        <div style={{
          position: 'absolute', inset: 0, opacity: 0.06, pointerEvents: 'none',
          backgroundImage: 'repeating-linear-gradient(0deg, transparent 0, transparent 39px, var(--fg) 39px, var(--fg) 40px)',
        }} />

        <div style={{ position: 'relative', zIndex: 1 }}>
          <div className="t-eyebrow">── what's inside</div>
          <h2 style={{
            fontFamily: 'var(--font-sans)',
            fontSize: 30, lineHeight: 1.18,
            fontWeight: 500, letterSpacing: '-0.02em',
            marginTop: 12, color: 'var(--fg)', maxWidth: 460,
          }}>
            Personal & team metrics. DORA. SPACE. AI brief — all on one warm canvas.
          </h2>
        </div>

        <div style={{
          position: 'relative', zIndex: 1,
          display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14,
        }}>
          {/* Sources card — full width */}
          <div className="card" style={{ padding: 14, gridColumn: '1 / -1' }}>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
              <div className="t-eyebrow">sources</div>
              <Chip color="emerald" dot>live</Chip>
            </div>
            <div className="col gap-2">
              {PREVIEW_SOURCES.map((s, i) => (
                <div key={i} className="row gap-2" style={{
                  padding: '6px 10px', borderRadius: 6,
                  background: 'var(--bg-2)', border: '1px solid var(--line-2)',
                }}>
                  <span style={{ color: `var(--${s.color})` }}>{s.icon}</span>
                  <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--fg)', flex: 1 }}>{s.name}</span>
                  <span className="t-label" style={{ fontSize: 10 }}>synced {s.sync}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Team card */}
          <div className="card" style={{ padding: 14 }}>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
              <div className="t-eyebrow">team</div>
              <span className="t-label" style={{ fontSize: 10 }}>5 members</span>
            </div>
            <div className="col gap-2">
              {PREVIEW_MEMBERS.map((m, i) => (
                <div key={i} className="row gap-2">
                  <span className="avatar avatar-sm" style={{
                    background: `var(--${m.color}-bg)`,
                    color: `var(--${m.color})`,
                    borderColor: 'transparent',
                  }}>{m.initials}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--fg)', flex: 1 }}>{m.name}</span>
                  <span className="font-mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{m.v}</span>
                </div>
              ))}
            </div>
          </div>

          {/* AI summary card */}
          <div className="card" style={{ padding: 14 }}>
            <div className="row gap-2" style={{ color: 'var(--violet-strong)', marginBottom: 8 }}>
              <AI width={14} height={14} />
              <span className="t-label" style={{ color: 'var(--violet-strong)', fontSize: 10 }}>AI SUMMARY</span>
              <span style={{ flex: 1 }} />
              <Chip color="emerald">Fresh</Chip>
            </div>
            <p style={{ fontSize: 12, color: 'var(--fg-2)', margin: 0, lineHeight: 1.5 }}>
              <strong style={{ color: 'var(--fg)' }}>327 commits</strong>, <strong style={{ color: 'var(--fg)' }}>23 PRs</strong> merged.
              Watch knowledge-silo <strong style={{ color: 'var(--coral)', fontFamily: 'var(--font-mono)' }}>67%</strong>.
            </p>
          </div>
        </div>

        {/* Footer badges */}
        <div className="row gap-3" style={{ position: 'relative', zIndex: 1, color: 'var(--fg-3)', flexWrap: 'wrap' }}>
          <span className="row gap-2"><Shield width={12} height={12} /><span className="t-label" style={{ fontSize: 10 }}>Local-first AI</span></span>
          <span className="tick">·</span>
          <span className="row gap-2"><Github width={12} height={12} /><span className="t-label" style={{ fontSize: 10 }}>GitHub · Jira · Local Git</span></span>
          <span className="tick">·</span>
          <span className="row gap-2"><MergeFreq width={12} height={12} /><span className="t-label" style={{ fontSize: 10 }}>DORA + SPACE</span></span>
        </div>
      </div>

      <style>{`
        @media (max-width: 880px) {
          .login-split { grid-template-columns: 1fr !important; }
          .login-showcase { display: none !important; }
        }
      `}</style>
    </div>
  );
}
