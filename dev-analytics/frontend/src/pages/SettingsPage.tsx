import { useState, type FormEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { User, Shield } from 'lucide-react';

const ROLE_COLORS = {
  DEVELOPER: 'violet' as const,
  MANAGER: 'blue' as const,
  ADMIN: 'amber' as const,
};

export function SettingsPage() {
  const { user } = useAuth();
  const [saved, setSaved] = useState(false);

  // Profile form (display-only for now — backend doesn't expose PUT /users/me yet)
  function handleSave(e: FormEvent) {
    e.preventDefault();
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

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
                  <Badge color={ROLE_COLORS[user.role] ?? 'gray'}>
                    {user.role}
                  </Badge>
                )}
              </div>
            </div>
            {saved && (
              <p className="text-sm text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                Saved successfully.
              </p>
            )}
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
                <p className="text-xs text-gray-400">Stateless JWT — tokens are stored locally in this browser</p>
              </div>
              <Badge color="emerald">Active</Badge>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
