'use client';

import AuthGuard from '@/components/layout/AuthGuard';

export default function Providers({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
