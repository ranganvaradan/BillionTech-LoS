import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getPlpProgram,
  retryPlpApplication,
  retryPlpBorrower,
  retryPlpProgram,
  retryPlpSubProgram,
} from '@/api/plp'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { ProgramsWorkspaceNav } from '@/components/workspace/ProgramsWorkspaceNav'
import { PlpSyncStatusBadge } from '@/components/plp/PlpSyncStatusBadge'
import { formatMoney } from '@/lib/format'
import type { PlpProgramDetail } from '@/types/plp'

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 text-sm">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium text-slate-900">{value}</dd>
    </div>
  )
}

export function PlpProgramDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [detail, setDetail] = useState<PlpProgramDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError(null)
    try {
      setDetail(await getPlpProgram(id))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load program.')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  if (!id) return null

  const anchor = detail?.subPrograms[0]

  return (
    <div>
      <ProgramsWorkspaceNav />
      <p className="mb-4 text-sm text-slate-600">
        <Link to="/plp/programs" className="font-medium text-slate-800 underline">
          ← PLP Programs
        </Link>
      </p>
      <PageHeader title={detail?.programName ?? 'Program'} description={detail?.programCode} />
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      {detail ? (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            <section className="rounded-lg border border-slate-200 bg-white p-4">
              <h2 className="bt-card-title">Program</h2>
              <dl className="mt-3 grid gap-2">
                <DetailRow label="Type" value={detail.programType} />
                <DetailRow
                  label="Credit limit"
                  value={detail.creditLimit != null ? formatMoney(detail.creditLimit) : '—'}
                />
                <DetailRow
                  label="Interest"
                  value={detail.interestRate != null ? `${detail.interestRate}%` : '—'}
                />
                <DetailRow label="Tenure (days)" value={String(detail.tenureDays ?? '—')} />
                <DetailRow
                  label="Validity"
                  value={`${detail.validityStartDate ?? '—'} → ${detail.validityEndDate ?? '—'}`}
                />
              </dl>
              <div className="mt-3">
                <PlpSyncStatusBadge
                  status={detail.programSyncStatus}
                  label="Program PLP"
                  onRetry={
                    detail.programSyncStatus === 'SYNC_FAILED'
                      ? async () => {
                          await retryPlpProgram(detail.programId)
                          await load()
                        }
                      : undefined
                  }
                />
              </div>
            </section>
            {anchor ? (
              <section className="rounded-lg border border-slate-200 bg-white p-4">
                <h2 className="bt-card-title">Anchor</h2>
                <p className="mt-2 text-sm text-slate-800">{anchor.anchorName ?? anchor.anchorId}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <PlpSyncStatusBadge status={anchor.anchorSyncStatus ?? 'NOT_SYNCED'} label="Anchor" />
                  <PlpSyncStatusBadge
                    status={anchor.subProgramSyncStatus}
                    label="Sub-program"
                    onRetry={
                      anchor.subProgramSyncStatus === 'SYNC_FAILED'
                        ? async () => {
                            await retryPlpSubProgram(anchor.subProgramId)
                            await load()
                          }
                        : undefined
                    }
                  />
                </div>
              </section>
            ) : null}
          </div>

          <section className="mt-6 rounded-lg border border-slate-200 bg-white">
            <h2 className="border-b border-slate-200 px-4 py-3 bt-card-title">
              Linked borrowers
            </h2>
            <div className="overflow-x-auto">
              <table className="bt-table min-w-full">
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th className="px-4 py-2 font-medium">Borrower</th>
                    <th className="px-4 py-2 font-medium">Application</th>
                    <th className="px-4 py-2 font-medium">Status</th>
                    <th className="px-4 py-2 font-medium">PLP sync</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.borrowers.map((b) => (
                    <tr key={b.applicationId} className="border-t border-slate-100">
                      <td className="px-4 py-2">{b.borrowerName}</td>
                      <td className="px-4 py-2">
                        <Link
                          to={`/applications/${b.applicationId}`}
                          className="font-medium text-slate-800 underline"
                        >
                          {b.applicationNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-2">{b.status}</td>
                      <td className="px-4 py-2">
                        <div className="flex flex-col gap-1">
                          <PlpSyncStatusBadge
                            status={b.borrowerSyncStatus}
                            label="Borrower"
                            onRetry={
                              b.borrowerSyncStatus === 'SYNC_FAILED'
                                ? async () => {
                                    await retryPlpBorrower(b.applicationId)
                                    await load()
                                  }
                                : undefined
                            }
                          />
                          <PlpSyncStatusBadge status={b.linkSyncStatus} label="Link" />
                          <PlpSyncStatusBadge
                            status={b.mappingSyncStatus}
                            label="Mapping"
                            onRetry={
                              b.overallSyncStatus === 'SYNC_FAILED'
                                ? async () => {
                                    await retryPlpApplication(b.applicationId)
                                    await load()
                                  }
                                : undefined
                            }
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {detail.borrowers.length === 0 ? (
                <p className="px-4 py-6 text-center text-sm text-slate-500">No borrowers linked.</p>
              ) : null}
            </div>
          </section>
        </>
      ) : null}
    </div>
  )
}
