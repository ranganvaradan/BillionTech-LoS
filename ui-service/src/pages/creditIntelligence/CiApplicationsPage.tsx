import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listStagingCases, type StagingCaseSummary } from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { PoliciesWorkspaceNav } from '@/components/workspace/PoliciesWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import { CiEmptyState, CiExecutiveSummary, CiLoadingCopy } from '@/components/creditIntelligence/CiSection'
import { caseFriendlyTitle, softFixtureLabel } from '@/lib/creditIntelligence/businessLexicon'

export function CiApplicationsPage() {
  const [cases, setCases] = useState<StagingCaseSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setCases(await listStagingCases())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load sample applications')
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
        title="Demo Samples"
        description="Designed credit scenarios for policy walkthroughs — not live borrower applications."
      />
      <PoliciesWorkspaceNav />
      <CiFixtureBanner />
      {loading ? (
        <CiLoadingCopy lines={['Loading sample applications…', 'Preparing credit-file summaries']} />
      ) : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}
      {!loading && !error ? (
        <>
          <CiExecutiveSummary
            title="What should I do next?"
            nextAction={
              <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
                Open Policy Studio
              </Link>
            }
          >
            <p>
              Pick a sample application to review evidence and the draft recommendation, or open Policy Studio to
              analyse a credit policy.
            </p>
          </CiExecutiveSummary>

          {cases.length === 0 ? (
            <CiEmptyState
              title="No sample applications available"
              detail="Staging sample cases are not loaded yet. Open Policy Studio to continue with a policy upload."
              action={
                <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
                  Open Policy Studio
                </Link>
              }
            />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {cases.map((c) => (
                <Link
                  key={c.caseCode}
                  to={`/credit-intelligence/workspace/${c.caseCode}`}
                  className="bt-card block p-4 transition hover:border-slate-300 hover:shadow-sm"
                >
                  <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-amber-700">
                    {softFixtureLabel(c.fixtureBanner)}
                  </div>
                  <h2 className="text-lg font-semibold text-slate-900">
                    {caseFriendlyTitle(c.caseCode, c.title)}
                  </h2>
                  <p className="mt-2 text-[13px] text-slate-600">{c.description}</p>
                  <span className="mt-4 inline-block text-sm font-medium text-sky-800 underline">
                    Open credit file →
                  </span>
                </Link>
              ))}
            </div>
          )}
        </>
      ) : null}
    </div>
  )
}
