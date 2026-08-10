import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { listPendingProgramApprovals, type ProgramApprovalResponse } from '@/api/workflow'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { ProgramsWorkspaceNav } from '@/components/workspace/ProgramsWorkspaceNav'
import { ProgramApprovalPanel } from '@/components/plp/ProgramApprovalPanel'

export function ProgramApprovalsPage() {
  const [rows, setRows] = useState<ProgramApprovalResponse[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setRows(await listPendingProgramApprovals())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load program approvals')
      setRows(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  return (
    <div className="space-y-4">
      <PageHeader
        title="Program Approvals"
        description="L1/L2 review queue for PLP programs created from anchor onboarding. Approve activates the program on PLP."
      />
      <ProgramsWorkspaceNav />
      {loading && <LoadingState label="Loading approvals…" />}
      {error && <ErrorState message={error} />}
      {rows && !loading && rows.length === 0 ? (
        <p className="text-sm text-slate-600">No programs awaiting your approval.</p>
      ) : null}
      {rows && rows.length > 0 ? (
        <ul className="space-y-4">
          {rows.map((r) => (
            <li key={r.programId} className="rounded border border-slate-200 bg-white p-4">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h2 className="font-semibold text-slate-900">{r.programName}</h2>
                  <p className="text-sm text-slate-600">
                    {r.programCode} · {r.approvalStatus}
                    {r.assignedL1UserName ? ` · L1: ${r.assignedL1UserName}` : null}
                    {r.assignedL2UserName ? ` · L2: ${r.assignedL2UserName}` : null}
                  </p>
                </div>
                {r.anchorApplicationId ? (
                  <Link
                    to={`/applications/${r.anchorApplicationId}`}
                    className="text-sm text-indigo-700 underline"
                  >
                    Open anchor application
                  </Link>
                ) : null}
              </div>
              <ProgramApprovalPanel programId={r.programId} onUpdated={() => void load()} />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
