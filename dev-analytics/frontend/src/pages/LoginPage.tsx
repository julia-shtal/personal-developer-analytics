import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function LoginPage() {
  const { login } = useAuth();
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
    <div className="w-full min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Brand */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-2xl bg-violet-600 flex items-center justify-center mb-4 shadow-lg shadow-violet-200">
            <Activity className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-2xl font-semibold text-gray-900">Dev Analytics</h1>
          <p className="mt-1 text-sm text-gray-500">Sign in to your account</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="Username or email"
              type="text"
              autoComplete="username"
              value={usernameOrEmail}
              onChange={(e) => setUsernameOrEmail(e.target.value)}
              required
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />

            {error && (
              <p className="text-sm text-red-500 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                {error}
              </p>
            )}

            <div className="flex items-center justify-between">
              <Button type="submit" className="w-full" loading={loading}>
                Sign in
              </Button>
            </div>
          </form>

          <div className="mt-4 text-center">
            <Link to="/forgot-password" className="text-sm text-gray-400 hover:text-violet-600 transition-colors">
              Forgot your password?
            </Link>
          </div>
        </div>

        <p className="mt-4 text-center text-sm text-gray-500">
          No account?{' '}
          <Link to="/register" className="font-medium text-violet-600 hover:text-violet-700">
            Register
          </Link>
        </p>
      </div>
    </div>
  );
}
