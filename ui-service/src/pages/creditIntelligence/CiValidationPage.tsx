import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getStagingDemoHealth, getStagingWorkspace } from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import {
  CiExecutiveSummary,
  CiLoadingCopy,
  CiSection,
  CiStatCard,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import { asRecord } from '@/lib/creditIntelligence/businessLexicon'

export function CiValidationPage() {
  const [health, setHealth] = useState<Record<string, unknown> | null>(null)
  const [cutover, setCutover] = useState<Record<string, unknown> | null>(null)
  const [replay, setReplay] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const h = await getStagingDemoHealth()
      setHealth(h)
      const ws = await getStagingWorkspace('CASE_A')
      setCutover(asRecord(ws.cutover))
      setReplay(asRecord(ws.replay))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load readiness overview')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const prodAuthorityOff = health?.allowCanonicalAuthority === false || health?.allowCanonicalAuthority === 'false'
  const stagingOn = Boolean(health?.stagingDemoEnabled)
  const validationOn = Boolean(health?.validationEnabled)

  return (
    <div>
      <PageHeader
        title="Staging Readiness"
        description="Confirm staging review is healthy before Credit Manager walkthroughs. Production lending authority stays off."
      />
      <AdministrationWorkspaceNav />
      <CiFixtureBanner />
      {loading ? (
        <CiLoadingCopy
          lines={[
            'Checking readiness…',
            'Confirming draft-only mode',
            'Reviewing sample credit file consistency',
          ]}
        />
      ) : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      {!loading && !error ? (
        <>
          <CiExecutiveSummary
            title="Summary"
            nextAction={
              <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
                Open Policy Studio
              </Link>
            }
          >
            <p>
              {prodAuthorityOff
                ? 'Production authority is off. Draft and simulation reviews are safe for prospect walkthroughs.'
                : 'Review production authority settings with engineering before continuing.'}
            </p>
          </CiExecutiveSummary>

          <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <CiStatCard
              label="Environment"
              value={String(health?.status ?? 'Ready')}
              tone="info"
            />
            <CiStatCard label="Demo staging" value={stagingOn ? 'Available' : 'Unavailable'} tone={stagingOn ? 'approved' : 'action'} />
            <CiStatCard
              label="Sample validation"
              value={validationOn ? 'Available' : 'Unavailable'}
              tone={validationOn ? 'approved' : 'review'}
            />
            <CiStatCard
              label="Live production authority"
              value={prodAuthorityOff ? 'Off' : 'Check'}
              tone={prodAuthorityOff ? 'approved' : 'action'}
            />
          </div>

          <CiSection title="What to do next" description="Guided links for Credit Head review.">
            <ul className="space-y-3 text-sm">
              <li>
                <Link className="font-medium text-sky-800 underline" to="/credit-intelligence/applications">
                  Review sample applications
                </Link>
                <p className="text-slate-600">Open credit-file style evidence and decisions.</p>
              </li>
              <li>
                <Link className="font-medium text-sky-800 underline" to="/credit-intelligence/policy-studio">
                  Upload or review a credit policy
                </Link>
                <p className="text-slate-600">Banking and Bureau sample policies available.</p>
              </li>
              <li>
                <Link className="font-medium text-sky-800 underline" to="/credit-intelligence/dual-run">
                  Compare current LOS vs proposed policy
                </Link>
                <p className="text-slate-600">Focus on material differences before approval.</p>
              </li>
            </ul>
          </CiSection>

          <CiTechnicalDetails title="Developer Diagnostics">
            {JSON.stringify({ health, cutover, replay }, null, 2)}
          </CiTechnicalDetails>
        </>
      ) : null}
    </div>
  )
}
