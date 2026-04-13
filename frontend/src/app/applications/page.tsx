'use client';

import { Suspense } from 'react';
import { LoadingSpinner } from '@/components/ui/StatusBadge';
import ApplicationsContent from './ApplicationsContent';

export default function ApplicationsPage() {
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <ApplicationsContent />
    </Suspense>
  );
}
