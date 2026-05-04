import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

const STEPS = [
  'Loading your repositories…',
  'Fetching your metrics…',
  'Preparing your dashboard…',
];

const REDIRECT_DELAY_MS = 2800;
const STEP_INTERVAL_MS = 800;

export function WelcomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [fadeIn, setFadeIn] = useState(false);

  // Trigger fade-in on mount
  useEffect(() => {
    const t = setTimeout(() => setFadeIn(true), 50);
    return () => clearTimeout(t);
  }, []);

  // Cycle through loading steps
  useEffect(() => {
    const interval = setInterval(() => {
      setStep((s) => (s + 1 < STEPS.length ? s + 1 : s));
    }, STEP_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  // Navigate to dashboard after delay
  useEffect(() => {
    const t = setTimeout(() => navigate('/dashboard', { replace: true }), REDIRECT_DELAY_MS);
    return () => clearTimeout(t);
  }, [navigate]);

  const firstName = user?.username?.split(/[\s._-]/)[0] ?? 'there';

  return (
    <div
      className={`w-full min-h-screen bg-gradient-to-br from-violet-50 to-white flex items-center justify-center transition-opacity duration-500 ${fadeIn ? 'opacity-100' : 'opacity-0'}`}
    >
      <div className="flex flex-col items-center gap-6 text-center px-4">
        {/* Logo */}
        <div className="w-16 h-16 rounded-2xl bg-violet-600 flex items-center justify-center shadow-xl shadow-violet-200 animate-bounce-slow">
          <Activity className="h-8 w-8 text-white" />
        </div>

        {/* Greeting */}
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">
            Welcome back, {firstName}!
          </h1>
          <p className="mt-1 text-sm text-gray-500">Dev Analytics</p>
        </div>

        {/* Step label */}
        <p className="text-sm text-violet-600 font-medium min-h-[1.25rem] transition-all duration-300">
          {STEPS[step]}
        </p>

        {/* Animated dots */}
        <div className="flex items-center gap-2">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="w-2 h-2 rounded-full bg-violet-400"
              style={{ animation: `pulse 1.2s ease-in-out ${i * 0.2}s infinite` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
