import { useState } from 'react'
import type { PlpSyncStatus } from '@/types/plp'

const STATUS_STYLES: Record<PlpSyncStatus, string> = {
  NOT_SYNCED: 'bg-slate-100 text-slate-700',
  SYNC_SUCCESS: 'bg-emerald-100 text-emerald-800',
  SYNC_FAILED: 'bg-red-100 text-red-800',
}

const STATUS_LABELS: Record<PlpSyncStatus, string> = {
  NOT_SYNCED: 'Not synced',
  SYNC_SUCCESS: 'Synced',
  SYNC_FAILED: 'Sync failed',
}

export function PlpSyncStatusBadge({
  status,
  label,
  onRetry,
  retrying = false,
}: {
  status: PlpSyncStatus | null | undefined
  label?: string
  onRetry?: () => void | Promise<void>
  retrying?: boolean
}) {
  const [busy, setBusy] = useState(false)
  const resolved = status ?? 'NOT_SYNCED'

  async function handleRetry() {
    if (!onRetry) return
    setBusy(true)
    try {
      await onRetry()
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <span
        className={[
          'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
          STATUS_STYLES[resolved],
        ].join(' ')}
      >
        {label ? `${label}: ` : ''}
        {STATUS_LABELS[resolved]}
      </span>
      {resolved === 'SYNC_FAILED' && onRetry ? (
        <button
          type="button"
          className="text-xs font-medium text-slate-700 underline hover:text-slate-900 disabled:opacity-50"
          disabled={busy || retrying}
          onClick={() => void handleRetry()}
        >
          {busy || retrying ? 'Retrying…' : 'Retry'}
        </button>
      ) : null}
    </span>
  )
}
