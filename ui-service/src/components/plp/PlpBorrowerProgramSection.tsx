import { useCallback, useEffect, useState } from 'react'
import {
  getLinkedSubProgramSummary,
  linkApplicationToProgram,
  listSelectablePlpPrograms,
  retryPlpApplication,
} from '@/api/plp'
import { ApiError } from '@/api/http'
import type { ApplicationResponse } from '@/types/application'
import type { PlpLinkedSubProgramSummary, PlpProgramSummary } from '@/types/plp'
import { PlpSyncStatusBadge } from '@/components/plp/PlpSyncStatusBadge'

export function PlpBorrowerProgramSection({
  app,
  onRefetch,
}: {
  app: ApplicationResponse
  onRefetch?: () => void | Promise<unknown>
}) {
  const [summary, setSummary] = useState<PlpLinkedSubProgramSummary | null>(null)
  const [programs, setPrograms] = useState<PlpProgramSummary[]>([])
  const [selectedSubProgramId, setSelectedSubProgramId] = useState(app.subProgramId ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(false)

  const loadSummary = useCallback(async () => {
    if (!app.subProgramId) {
      setSummary(null)
      return
    }
    try {
      const s = await getLinkedSubProgramSummary(app.subProgramId)
      setSummary(s)
      setSelectedSubProgramId(app.subProgramId)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load linked program.')
    }
  }, [app.subProgramId])

  const loadPrograms = useCallback(async () => {
    try {
      const list = await listSelectablePlpPrograms()
      setPrograms(list)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load programs.')
    }
  }, [])

  useEffect(() => {
    void loadSummary()
    void loadPrograms()
  }, [loadSummary, loadPrograms])

  async function onChangeProgram() {
    if (!selectedSubProgramId || selectedSubProgramId === app.subProgramId) {
      return
    }
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await linkApplicationToProgram(app.id, selectedSubProgramId)
      setInfo('Anchor and program updated for this application.')
      await onRefetch?.()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not update program link.')
    } finally {
      setBusy(false)
    }
  }

  async function onRetryPlp() {
    setRetrying(true)
    setError(null)
    setInfo(null)
    try {
      await retryPlpApplication(app.id)
      setInfo('PLP sync retry completed. Refreshing status…')
      await onRefetch?.()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'PLP retry failed.')
    } finally {
      setRetrying(false)
    }
  }

  const postSanctionStatuses = new Set([
    'SANCTIONED',
    'KFS_GENERATED',
    'SANCTION_ISSUED',
    'ESIGN_PENDING',
    'ESIGN_COMPLETED',
    'READY_FOR_DISBURSEMENT',
    'DISBURSEMENT_PENDING',
    'DISBURSED',
  ])
  const hasSanction =
    app.sanctionedAmount != null ||
    postSanctionStatuses.has(app.status) ||
    app.plpBorrowerId != null
  const borrowerPlpSynced =
    app.plpBorrowerSyncStatus === 'SYNC_SUCCESS' &&
    app.plpLinkSyncStatus === 'SYNC_SUCCESS' &&
    app.plpMappingSyncStatus === 'SYNC_SUCCESS'

  return (
    <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <h3 className="bt-card-title">Invoice discounting — anchor &amp; program</h3>
      <p className="mt-1 text-sm text-slate-600">
        Uses the anchor and sub-program selected during application intake. Change only if the credit
        structure should differ from intake.
      </p>

      {!app.subProgramId ? (
        <p className="mt-3 text-sm text-amber-800">
          No program linked yet. Complete intake and select anchor/program before sanction.
        </p>
      ) : null}

      {summary ? (
        <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Anchor</dt>
            <dd className="font-medium text-slate-900">
              {summary.anchorName} ({summary.anchorCode})
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Program</dt>
            <dd className="font-medium text-slate-900">{summary.programName}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Sub-program</dt>
            <dd className="font-medium text-slate-900">{summary.subProgramName}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Program limit</dt>
            <dd className="font-medium text-slate-900">
              {summary.programLimit != null ? summary.programLimit.toLocaleString() : '—'}
            </dd>
          </div>
        </dl>
      ) : null}

      {programs.length > 0 ? (
        <label className="mt-4 block text-sm font-medium text-slate-700">
          Change sub-program (optional)
          <select
            className="mt-1 bt-input w-full text-sm"
            value={selectedSubProgramId}
            onChange={(e) => setSelectedSubProgramId(e.target.value)}
          >
            <option value="">— Select —</option>
            {programs.map((p) => (
              <option key={p.subProgramId ?? p.programId} value={p.subProgramId ?? ''}>
                {p.anchorName ? `${p.anchorName} — ` : ''}
                {p.programName}
                {p.subProgramId ? '' : ' (no sub-program)'}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      {selectedSubProgramId && selectedSubProgramId !== app.subProgramId ? (
        <button
          type="button"
          className="mt-2 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-50"
          disabled={busy}
          onClick={() => void onChangeProgram()}
        >
          {busy ? 'Updating…' : 'Apply program change'}
        </button>
      ) : null}

      <div className="mt-4 space-y-2 rounded-md border border-slate-200 bg-white p-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">PLP sync (after sanction)</p>
        {!hasSanction ? (
          <p className="text-sm text-slate-600">
            Borrower and borrower link are created in PLP when you approve sanction and generate KFS.
          </p>
        ) : (
          <div className="flex flex-wrap gap-3">
            <PlpSyncStatusBadge
              status={app.plpBorrowerSyncStatus}
              label="Borrower"
              onRetry={onRetryPlp}
              retrying={retrying}
            />
            <PlpSyncStatusBadge
              status={app.plpLinkSyncStatus}
              label="Borrower link"
              onRetry={onRetryPlp}
              retrying={retrying}
            />
            <PlpSyncStatusBadge
              status={app.plpMappingSyncStatus}
              label="Mapping"
              onRetry={onRetryPlp}
              retrying={retrying}
            />
          </div>
        )}
        {app.plpProgramSyncError ? (
          <p className="text-sm text-red-700">{app.plpProgramSyncError}</p>
        ) : null}
        {summary?.programSyncStatus === 'SYNC_SUCCESS' &&
        summary?.subProgramSyncStatus === 'SYNC_SUCCESS' ? (
          <p className="text-sm text-emerald-800">Program and sub-program are synced to PLP.</p>
        ) : null}
        {hasSanction && borrowerPlpSynced ? (
          <p className="text-sm text-emerald-800">
            Borrower, sub-program link, and program mapping are synced to PLP.
          </p>
        ) : null}
      </div>

      {error ? <p className="mt-3 text-sm text-red-600">{error}</p> : null}
      {info ? <p className="mt-3 text-sm text-emerald-800">{info}</p> : null}
    </div>
  )
}
