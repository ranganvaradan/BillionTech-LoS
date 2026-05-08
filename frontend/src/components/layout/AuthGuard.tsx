'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';

const PUBLIC_PATHS = ['/login', '/portal'];

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [authorized, setAuthorized] = useState(false);

  const checkAuth = useCallback(() => {
    const token = localStorage.getItem('los_token');
    const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

    if (!token && !isPublic) {
      router.replace('/login');
      return;
    }

    if (token && pathname === '/login') {
      router.replace('/applications');
      return;
    }

    setAuthorized(true);
  }, [pathname, router]);

  useEffect(() => {
    checkAuth();

    // Re-check auth when window regains focus (detects token cleared by 401 interceptor)
    window.addEventListener('focus', checkAuth);
    // Listen for storage changes from other tabs
    window.addEventListener('storage', checkAuth);
    return () => {
      window.removeEventListener('focus', checkAuth);
      window.removeEventListener('storage', checkAuth);
    };
  }, [checkAuth]);

  // Always render public paths immediately
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return <>{children}</>;
  }

  if (!authorized) {
    return null; // Brief blank while checking auth
  }

  return <>{children}</>;
}
