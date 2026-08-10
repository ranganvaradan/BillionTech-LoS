import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listPlpPrograms, retryPlpProgram, retryPlpSubProgram } from '@/api/plp'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { ProgramsWorkspaceNav } from '@/components/workspace/ProgramsWorkspaceNav'
import { PlpSyncStatusBadge } from '@/components/plp/PlpSyncStatusBadge'
import { formatMoney } from '@/lib/format'
import type { PlpProgramSummary } from '@/types/plp'

export function PlpProgramsPage() {
  const [programs, setPrograms] = useState<PlpProgramSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setPrograms(await listPlpPrograms())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load programs.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div>
      <PageHeader
        title="Programs"
        description="Invoice discounting programs and program lending setup."
      />
      <ProgramsWorkspaceNav />
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      {!loading && !error ? (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full">
            <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3 font-medium">Program</th>
                <th className="px-4 py-3 font-medium">Anchor</th>
                <th className="px-4 py-3 font-medium">Credit limit</th>
                <th className="px-4 py-3 font-medium">Rate</th>
                <th className="px-4 py-3 font-medium">Validity</th>
                <th className="px-4 py-3 font-medium">Sync</th>
                <th className="px-4 py-3 font-medium">Borrowers</th>
                <th className="px-4 py-3 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {programs.map((p) => (
                <tr key={`${p.programId}-${p.subProgramId ?? 'x'}`} className="border-b border-slate-100">
                  <td className="px-4 py-3 font-medium text-slate-900">{p.programName}</td>
                  <td className="px-4 py-3 text-slate-700">{p.anchorName ?? '—'}</td>
                  <td className="px-4 py-3">{p.creditLimit != null ? formatMoney(p.creditLimit) : '—'}</td>
                  <td className="px-4 py-3">{p.interestRate != null ? `${p.interestRate}%` : '—'}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {p.validityStartDate ?? '—'} → {p.validityEndDate ?? '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-col gap-1">
                      <PlpSyncStatusBadge
                        status={p.programSyncStatus}
                        label="Program"
                        onRetry={
                          p.programSyncStatus === 'SYNC_FAILED'
                            ? async () => {
                                await retryPlpProgram(p.programId)
                                await load()
                              }
                            : undefined
                        }
                      />
                      {p.subProgramId ? (
                        <PlpSyncStatusBadge
                          status={p.subProgramSyncStatus ?? 'NOT_SYNCED'}
                          label="Sub-program"
                          onRetry={
                            p.subProgramSyncStatus === 'SYNC_FAILED'
                              ? async () => {
                                  await retryPlpSubProgram(p.subProgramId!)
                                  await load()
                                }
                              : undefined
                          }
                        />
                      ) : null}
                    </div>
                  </td>
                  <td className="px-4 py-3">{p.borrowerCount}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/plp/programs/${p.programId}`}
                      className="text-sm font-medium text-slate-800 underline"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {programs.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-slate-500">No programs yet.</p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
