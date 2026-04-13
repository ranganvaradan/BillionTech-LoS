'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';

const PUBLIC_PATHS = ['/login', '/portal'];

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [authorized, setAuthorized] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('los_token');
    const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

    if (!token && !isPublic) {
      router.replace('/login');
      return;
    }

    if (token && pathname === '/login') {
      router.replace('/dashboard');
      return;
    }

    setAuthorized(true);
  }, [pathname, router]);

  // Always render public paths immediately
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return <>{children}</>;
  }

  if (!authorized) {
    return null; // Brief blank while checking auth
  }

  return <>{children}</>;
}
