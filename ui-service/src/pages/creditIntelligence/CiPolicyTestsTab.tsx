import { useEffect, useState } from 'react'
import { listPolicyTests, reviewPolicyTest } from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiOutcomeBadge,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import {
  asList,
  asRecord,
  formatBusinessInputs,
} from '@/lib/creditIntelligence/businessLexicon'

export function CiPolicyTestsTab({
  documentId,
  busy,
  setBusy,
  onError,
  prospectDemoMode,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  prospectDemoMode: boolean
}) {
  const [payload, setPayload] = useState<Record<string, unknown> | null>(null)
  const [editId, setEditId] = useState<string | null>(null)
  const [editExpected, setEditExpected] = useState('PASS')
  const [showTech, setShowTech] = useState(false)
  const [loading, setLoading] = useState(true)

  const reload = async () => {
    onError(null)
    try {
      setPayload(await listPolicyTests(documentId))
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load tests')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const tests = asList(payload?.tests)
  const approved = Number(payload?.approvedCount ?? 0)

  const act = async (testId: string, uiAction: string, expectedOutcome?: string) => {
    setBusy(true)
    onError(null)
    try {
      const data = await reviewPolicyTest(documentId, testId, {
        uiAction,
        expectedOutcome,
        reviewer: 'credit_manager',
      })
      setPayload(data)
      setEditId(null)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Test review failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading && !payload) {
    return (
      <p className="text-sm text-slate-600">
        Preparing boundary and missing-information tests…
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <CiExecutiveSummary title="Summary">
        <p>
          {approved} of {Number(payload?.count ?? tests.length)} tests approved. Confirm expected outcomes before
          building a draft policy package.
        </p>
      </CiExecutiveSummary>

      <CiSection
        title="Generated tests"
        description="Boundary and missing-information scenarios for each proposed business rule."
      >
        {tests.length === 0 ? (
          <CiEmptyState
            title="No tests generated yet"
            detail="Approve proposed business rules first. Tests appear once the policy understanding is ready."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-2 py-2">Rule</th>
                  <th className="px-2 py-2">Scenario</th>
                  <th className="px-2 py-2">Inputs</th>
                  <th className="px-2 py-2">Expected</th>
                  <th className="px-2 py-2">Actual</th>
                  <th className="px-2 py-2">Status</th>
                  <th className="px-2 py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {tests.map((raw) => {
                  const t = asRecord(raw)
                  const id = String(t.id ?? '')
                  return (
                    <tr key={id} className="border-b border-slate-100 align-top">
                      <td className="px-2 py-2 font-medium">{String(t.rule ?? '—')}</td>
                      <td className="px-2 py-2 text-xs">{String(t.scenario ?? t.name ?? '—')}</td>
                      <td className="px-2 py-2 text-xs text-slate-600">{formatBusinessInputs(t.inputs)}</td>
                      <td className="px-2 py-2">
                        {editId === id ? (
                          <select
                            className="rounded border px-1 text-xs"
                            value={editExpected}
                            onChange={(e) => setEditExpected(e.target.value)}
                          >
                            <option value="PASS">Approved</option>
                            <option value="FAIL">Declined</option>
                            <option value="DATA_INSUFFICIENT">Missing Information</option>
                            <option value="REFER">Manual Credit Review</option>
                          </select>
                        ) : (
                          <CiOutcomeBadge value={t.expectedOutcome} />
                        )}
                      </td>
                      <td className="px-2 py-2">
                        <CiOutcomeBadge value={t.actualOutcome} />
                      </td>
                      <td className="px-2 py-2">
                        <CiOutcomeBadge value={t.status} />
                      </td>
                      <td className="px-2 py-2">
                        <div className="flex flex-col gap-1">
                          {editId === id ? (
                            <>
                              <button
                                type="button"
                                className="bt-btn bt-btn-primary bt-btn-sm"
                                disabled={busy}
                                onClick={() => void act(id, 'EDIT_EXPECTED', editExpected)}
                              >
                                Save expected
                              </button>
                              <button type="button" className="text-xs text-slate-500" onClick={() => setEditId(null)}>
                                Cancel
                              </button>
                            </>
                          ) : (
                            <>
                              <button
                                type="button"
                                className="bt-btn bt-btn-primary bt-btn-sm"
                                disabled={busy || Boolean(t.approved)}
                                onClick={() => void act(id, 'APPROVE')}
                              >
                                Approve test
                              </button>
                              <button
                                type="button"
                                className="bt-btn bt-btn-secondary bt-btn-sm"
                                disabled={busy}
                                onClick={() => {
                                  setEditId(id)
                                  setEditExpected(String(t.expectedOutcome ?? 'PASS'))
                                }}
                              >
                                Edit expected outcome
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </CiSection>

      {!prospectDemoMode ? (
        <div>
          <button
            type="button"
            className="text-xs font-semibold text-slate-500 underline"
            onClick={() => setShowTech((v) => !v)}
          >
            {showTech ? 'Hide developer diagnostics' : 'Developer Diagnostics'}
          </button>
          {showTech ? (
            <CiTechnicalDetails title="Developer Diagnostics">{JSON.stringify(payload, null, 2)}</CiTechnicalDetails>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

