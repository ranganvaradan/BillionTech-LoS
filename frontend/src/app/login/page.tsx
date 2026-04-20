'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { authApi } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [show2FA, setShow2FA] = useState(false);
  const [totpCode, setTotpCode] = useState('');
  const [tempToken, setTempToken] = useState('');

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await authApi.login({ username, password });
      if (result.requiresTwoFactor) {
        setTempToken(result.accessToken);
        setShow2FA(true);
      } else {
        localStorage.setItem('los_token', result.accessToken);
        localStorage.setItem('los_refresh_token', result.refreshToken);
        localStorage.setItem('los_user', JSON.stringify({
          username: result.user.username,
          roles: result.user.roles,
          firstName: result.user.firstName,
          lastName: result.user.lastName,
        }));
        router.push('/applications');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Login failed. Please check credentials.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handle2FA = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await authApi.verify2fa(tempToken, totpCode);
      localStorage.setItem('los_token', result.accessToken);
      localStorage.setItem('los_refresh_token', result.refreshToken);
      localStorage.setItem('los_user', JSON.stringify({
        username: result.user.username,
        roles: result.user.roles,
        firstName: result.user.firstName,
        lastName: result.user.lastName,
      }));
      router.push('/applications');
    } catch {
      setError('Invalid 2FA code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-100 to-blue-50">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary text-white font-bold text-xl mb-4">
            LOS
          </div>
          <h1 className="text-2xl font-bold text-slate-900">BillionTech LOS</h1>
          <p className="text-sm text-slate-500 mt-1">Loan Origination System v2.0</p>
        </div>

        {/* Login Card */}
        <div className="bg-white rounded-2xl shadow-lg border border-border p-8">
          {!show2FA ? (
            <form onSubmit={handleLogin} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Username</label>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-colors"
                  placeholder="Enter username"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-lg border border-border text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-colors"
                  placeholder="Enter password"
                  required
                />
              </div>
              {error && (
                <p className="text-sm text-danger bg-red-50 px-3 py-2 rounded-lg">{error}</p>
              )}
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-primary text-white py-2.5 rounded-lg text-sm font-medium hover:bg-primary-hover transition-colors disabled:opacity-50"
              >
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>
          ) : (
            <form onSubmit={handle2FA} className="space-y-5">
              <div className="text-center mb-2">
                <p className="text-sm text-slate-600">Enter the 6-digit code from your authenticator app</p>
              </div>
              <div>
                <input
                  type="text"
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                  className="w-full px-4 py-3 rounded-lg border border-border text-center text-lg font-mono tracking-widest focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                  placeholder="000000"
                  maxLength={6}
                  required
                />
              </div>
              {error && (
                <p className="text-sm text-danger bg-red-50 px-3 py-2 rounded-lg">{error}</p>
              )}
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-primary text-white py-2.5 rounded-lg text-sm font-medium hover:bg-primary-hover transition-colors disabled:opacity-50"
              >
                {loading ? 'Verifying...' : 'Verify'}
              </button>
              <button
                type="button"
                onClick={() => { setShow2FA(false); setTotpCode(''); setError(''); }}
                className="w-full text-sm text-slate-500 hover:text-slate-700"
              >
                Back to login
              </button>
            </form>
          )}
        </div>

        <p className="text-center text-xs text-slate-400 mt-6">
          Default: admin / Admin@LOS2026
        </p>
      </div>
    </div>
  );
}
