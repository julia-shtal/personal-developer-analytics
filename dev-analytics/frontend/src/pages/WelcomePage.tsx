import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Logo } from '@/components/brand/Logo';

const STEPS = [
  'Loading your repositories…',
  'Fetching your metrics…',
  'Preparing your dashboard…',
];

// Bumped from 2800 to 3500 to match the design's animation timing (T9.2)
const REDIRECT_DELAY_MS = 3500;
const STEP_INTERVAL_MS = 900;

export function WelcomePage() {
  const { user } = useAuth();
  const { logo } = useTheme();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setStep((s) => (s + 1 < STEPS.length ? s + 1 : s));
    }, STEP_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const t = setTimeout(() => navigate('/dashboard', { replace: true }), REDIRECT_DELAY_MS);
    return () => clearTimeout(t);
  }, [navigate]);

  const firstName = user?.username?.split(/[\s._-]/)[0] ?? 'there';

  return (
    <div style={{
      minHeight: '100vh',
      background: 'radial-gradient(ellipse at top, var(--violet-bg) 0%, var(--bg) 60%)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
    }}>
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        gap: 26, textAlign: 'center', maxWidth: 480,
      }}>
        <div style={{ animation: 'wel-float 2.4s ease-in-out infinite' }}>
          <Logo variant={logo} size={72} />
        </div>

        <div>
          <div className="t-eyebrow" style={{ marginBottom: 8 }}>── welcome back</div>
          <h1 style={{
            fontFamily: 'var(--font-sans)',
            fontSize: 30, lineHeight: 1.1, fontWeight: 500,
            letterSpacing: '-0.02em', color: 'var(--fg)', marginBottom: 6,
          }}>
            Hi, <span style={{ color: 'var(--violet-strong)' }}>{firstName}</span>.
          </h1>
          <p className="t-body" style={{ color: 'var(--fg-3)' }}>
            Spinning up your workspace.
          </p>
        </div>

        <div className="col gap-2" style={{ alignItems: 'flex-start' }}>
          {STEPS.map((s, i) => {
            const done = i < step;
            const active = i === step;
            return (
              <div key={i} className="row gap-3" style={{
                fontFamily: 'var(--font-mono)', fontSize: 12,
                color: done ? 'var(--emerald)' : active ? 'var(--violet-strong)' : 'var(--muted)',
                opacity: done || active ? 1 : 0.5,
                transition: 'all .2s',
              }}>
                <span style={{ width: 14, display: 'inline-flex', justifyContent: 'center' }}>
                  {done
                    ? <Check width={13} height={13} />
                    : active
                    ? <span className="chip-dot" style={{ animation: 'pulse 1s ease-in-out infinite' }} />
                    : <span className="chip-dot" style={{ opacity: 0.4 }} />
                  }
                </span>
                <span>{s}</span>
              </div>
            );
          })}
        </div>

        <div className="row gap-2" style={{ marginTop: 4 }}>
          {STEPS.map((_, i) => (
            <span key={i} style={{
              width: 6, height: 6, borderRadius: 999,
              background: i <= step ? 'var(--violet-strong)' : 'var(--bg-inset)',
              animation: i === step ? 'pulse 1.2s ease-in-out infinite' : 'none',
              transition: 'background .2s',
            }} />
          ))}
        </div>

        <button
          className="btn btn-sm btn-ghost"
          onClick={() => navigate('/dashboard', { replace: true })}
          style={{ marginTop: 8, fontSize: 11 }}
        >
          skip →
        </button>
      </div>

      <style>{`@keyframes wel-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }`}</style>
    </div>
  );
}