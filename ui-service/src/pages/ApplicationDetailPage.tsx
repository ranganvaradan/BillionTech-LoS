import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DocumentsSection } from '@/components/DocumentsSection'
import { ErrorState } from '@/components/ErrorState'
import { KycDetailsSection } from '@/components/KycDetailsSection'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { CollateralIntakeStaffPanel } from '@/components/CollateralIntakeStaffPanel'
import { BorrowerSubmittedIntakePanel } from '@/components/BorrowerSubmittedIntakePanel'
import { CamSection } from '@/components/CamSection'
import { DisbursementSection } from '@/components/DisbursementSection'
import { EsignSection } from '@/components/EsignSection'
import { SanctionKfsSection } from '@/components/SanctionKfsSection'
import { UnderwritingSection } from '@/components/UnderwritingSection'
import { useApplication } from '@/hooks/useApplication'
import { useStepExecutions } from '@/hooks/useStepExecutions'
import { borrowerStatusPath, buildWhatsAppStatusShareUrl } from '@/lib/borrowerShare'
import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { formatInstant, formatMoney, isUuid } from '@/lib/format'
import { borrowerTypeLabel } from '@/catalog/borrowerTypes'
import { loanProductLabel } from '@/catalog/loanProducts'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import { getActiveWorkflow } from '@/api/workflows'
import { getVkycEligibility, getVkycTimeline } from '@/api/vkyc'
import { buildVkycWorkflowGate, insertVkycTab, type VkycWorkflowGate } from '@/lib/vkycWorkflowGate'
import type { ApplicationResponse } from '@/types/application'
import type { StepExecutionRecordView } from '@/types/stepExecution'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { VkycDetailsSection } from '@/components/VkycDetailsSection'
import { VkycDownstreamGate } from '@/components/VkycDownstreamGate'

const TABS = [
  { id: 'summary' as const, label: 'Summary' },
  { id: 'borrower' as const, label: 'Borrower profile' },
  { id: 'collateral' as const, label: 'Collateral' },
  { id: 'kyc' as const, label: 'KYC' },
  { id: 'documents' as const, label: 'Documents' },
  { id: 'underwriting' as const, label: 'Underwriting' },
  { id: 'cam' as const, label: 'CAM' },
  { id: 'sanction' as const, label: 'Sanction' },
  { id: 'esign' as const, label: 'eSign' },
  { id: 'disbursement' as const, label: 'Disbursement' },
  { id: 'history' as const, label: 'History' },
]

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<'summary' | 'borrower' | 'collateral' | 'kyc' | 'vkyc' | 'documents' | 'underwriting' | 'cam' | 'sanction' | 'esign' | 'disbursement' | 'history'>('summary')
  const [activeWorkflow, setActiveWorkflow] = useState<WorkflowConfigResponse | null>(null)
  const [vkycEligibility, setVkycEligibility] = useState<Record<string, unknown> | null>(null)
  const [vkycTimeline, setVkycTimeline] = useState<Record<string, unknown> | null>(null)
  const valid = id && isUuid(id)
  const { data: app, loading: appLoading, error: appError, refetch: refetchApp } = useApplication(
    valid ? id : undefined,
  )
  const { data: steps, loading: stepsLoading, error: stepsError, refetch: refetchSteps } = useStepExecutions(
    valid ? id : undefined,
  )
  const reloadVkycWorkflowState = useCallback(async () => {
    if (!app || !valid || !id) {
      setActiveWorkflow(null)
      setVkycEligibility(null)
      setVkycTimeline(null)
      return
    }
    try {
      const workflow = await getActiveWorkflow(app.borrowerType, app.loanProduct)
      setActiveWorkflow(workflow)
      const hasVkyc = (workflow.steps ?? []).some((s) => {
        const step = String((s as Record<string, unknown>).step ?? '').trim().toUpperCase()
        return step === 'VIDEO_KYC' || step === 'VKYC'
      })
      if (hasVkyc) {
        const [eligibility, timeline] = await Promise.all([getVkycEligibility(id), getVkycTimeline(id)])
        setVkycEligibility(eligibility)
        setVkycTimeline(timeline)
      } else {
        setVkycEligibility(null)
        setVkycTimeline(null)
      }
    } catch {
      setActiveWorkflow(null)
      setVkycEligibility(null)
      setVkycTimeline(null)
    }
  }, [app, id, valid])

  useEffect(() => {
    void reloadVkycWorkflowState()
  }, [reloadVkycWorkflowState])

  const vkycGate: VkycWorkflowGate = useMemo(
    () =>
      buildVkycWorkflowGate({
        app,
        workflow: activeWorkflow,
        stepExecutions: steps,
        eligibility: vkycEligibility,
        timeline: vkycTimeline,
      }),
    [app, activeWorkflow, steps, vkycEligibility, vkycTimeline],
  )

  const tabs = useMemo(() => {
    const base: Array<{ id: typeof tab; label: string }> = [...TABS] as Array<{ id: typeof tab; label: string }>
    return insertVkycTab(base, vkycGate)
  }, [vkycGate])
  useEffect(() => {
    if (!vkycGate.visible && tab === 'vkyc') setTab('summary')
  }, [vkycGate.visible, tab])

  if (!valid) {
    return (
      <div>
        <PageHeader title="Application" />
        <ErrorState message="Invalid application id in URL." />
        <p className="mt-4 text-sm">
          <Link to="/applications" className="text-slate-800 underline">
            Back to list
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        title="Application details"
        description="Review KYC, underwriting, CAM, sanction, KFS, eSign, and disbursement for this loan."
      />
      <p className="mb-4 text-sm text-slate-600">
        <Link to="/applications" className="font-medium text-slate-800 underline">
          ← Back to applications
        </Link>
      </p>

      {appLoading && !app && <LoadingState label="Loading application…" />}
      {appError && !app && <ErrorState message={appError} />}

      {valid && (
        <div
          className="mb-6 border-b border-slate-200"
          role="tablist"
          aria-label="Application sections"
        >
          <div className="flex flex-wrap gap-1">
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id as typeof tab)}
                className={[
                  'rounded-t-md border border-b-0 px-3 py-2 text-sm font-medium transition-colors',
                  tab === t.id
                    ? 'border-slate-200 bg-white text-slate-900'
                    : 'border-transparent bg-transparent text-slate-600 hover:text-slate-900',
                ].join(' ')}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {valid && id && (
        <div className="mx-auto max-w-6xl rounded-lg border border-slate-200 border-t-0 bg-white p-5 shadow-sm sm:-mt-px sm:border-t sm:pt-5">
          {appLoading && !app ? (
            <LoadingState label="Loading application data…" />
          ) : app && !appLoading ? (
            <>
              {tab === 'summary' && <SummaryPanel app={app} applicationId={id} />}
              {tab === 'borrower' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Borrower-submitted details</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    Information captured from the intake journey (self-service, sales-assisted, or internal). This view is
                    for credit and operations; it is not shown to the borrower on the public status page in this form.
                  </p>
                  <BorrowerSubmittedIntakePanel app={app} />
                </div>
              )}
              {tab === 'collateral' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Collateral (intake)</h2>
                  <p className="mb-4 text-sm text-slate-600">Security details and uploads submitted with the application for secured products (LAP, Loan Against Shares, Gold Loan).</p>
                  <CollateralIntakeStaffPanel app={app} />
                </div>
              )}
              {tab === 'kyc' && (
                <KycDetailsSection
                  applicationId={id}
                  app={app}
                  onApplicationRefetch={refetchApp}
                  onStepsRefetch={refetchSteps}
                  className="mb-0 border-0 p-0 shadow-none"
                />
              )}
              {tab === 'vkyc' && (
                <VkycDetailsSection
                  applicationId={id}
                  workflowGate={vkycGate}
                  onApplicationRefetch={refetchApp}
                  onWorkflowStateRefetch={reloadVkycWorkflowState}
                />
              )}
              {tab === 'documents' && <DocumentsSection applicationId={id} />}
              {tab === 'underwriting' && (
                <UnderwritingSection applicationId={id} app={app} onRefetch={refetchApp} />
              )}
              {tab === 'cam' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Credit Appraisal Memo (CAM)</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    Auto-filled from underwriting. Edit credit manager fields and mark reviewed before sanction. Steps
                    below may be status-gated; the tab stays open so you can prepare documents early.
                  </p>
                  <VkycDownstreamGate
                    blocked={vkycGate.downstreamBlocked.cam}
                    vkycStatus={vkycTimelineVkycStatus(vkycTimeline)}
                    workflowPosition={activeWorkflow?.workflowPosition ?? ''}
                  >
                    <CamSection applicationId={id} app={app} onRefetch={refetchApp} />
                  </VkycDownstreamGate>
                </div>
              )}
              {tab === 'sanction' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Sanction &amp; KFS</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    Issue terms, generate sanction letter, and view KFS key facts. If your role has not reached sanction
                    yet, use this tab to review the workflow — actions unlock when the application status allows them.
                  </p>
                  <VkycDownstreamGate
                    blocked={vkycGate.downstreamBlocked.sanction}
                    vkycStatus={vkycTimelineVkycStatus(vkycTimeline)}
                    workflowPosition={activeWorkflow?.workflowPosition ?? ''}
                  >
                    <SanctionKfsSection
                      key={`${id}-${app.updatedAt ?? app.id}`}
                      applicationId={id}
                      app={app}
                      onRefetch={refetchApp}
                    />
                  </VkycDownstreamGate>
                </div>
              )}
              {tab === 'esign' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">eSign</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    Sanction letter, KFS, and agreement via the integration router. Content below reflects current status.
                  </p>
                  <VkycDownstreamGate
                    blocked={vkycGate.downstreamBlocked.esign}
                    vkycStatus={vkycTimelineVkycStatus(vkycTimeline)}
                    workflowPosition={activeWorkflow?.workflowPosition ?? ''}
                  >
                    <EsignSection applicationId={id} app={app} onRefetch={refetchApp} />
                  </VkycDownstreamGate>
                </div>
              )}
              {tab === 'disbursement' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Disbursement</h2>
                  <p className="mb-4 text-sm text-slate-600">Readiness checklist and LMS handover.</p>
                  <VkycDownstreamGate
                    blocked={vkycGate.downstreamBlocked.disburse}
                    vkycStatus={vkycTimelineVkycStatus(vkycTimeline)}
                    workflowPosition={activeWorkflow?.workflowPosition ?? ''}
                  >
                    <DisbursementSection applicationId={id} app={app} onRefetch={refetchApp} />
                  </VkycDownstreamGate>
                </div>
              )}
              {tab === 'history' && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Verification / processing history</h2>
                  <p className="mb-3 text-sm text-slate-600">Recent checks and system steps for this application.</p>
                  {stepsLoading && <LoadingState label="Loading history…" />}
                  {stepsError && <ErrorState message={stepsError} />}
                  {steps && !stepsLoading && <StepTable rows={steps} />}
                </div>
              )}
            </>
          ) : appError ? (
            <ErrorState message={appError} />
          ) : null}
        </div>
      )}
    </div>
  )
}

/**
 * Returns the current VKYC status string from the timeline payload, falling
 * back to {@code NOT_STARTED} when the timeline hasn't loaded yet. Kept as a
 * tiny helper so the gate banner shows a meaningful status without leaking
 * timeline shape to the gate component.
 */
function vkycTimelineVkycStatus(timeline: Record<string, unknown> | null): string {
  if (!timeline) return 'NOT_STARTED'
  const raw = timeline.vkycStatus
  return raw == null ? 'NOT_STARTED' : String(raw)
}

function readPhoneFromPersonal(personal: ApplicationResponse['personalInfo']): string {
  if (!personal) return ''
  const raw = personal.phone ?? personal.mobile ?? personal.borrowerMobile
  return typeof raw === 'string' || typeof raw === 'number' ? String(raw) : ''
}

function readStr(m: ApplicationResponse['personalInfo'], key: string): string {
  if (!m || typeof m !== 'object') return ''
  const v = (m as Record<string, unknown>)[key]
  return v == null ? '' : String(v)
}

function formatIntakeLine(raw: string): string {
  const u = raw.toUpperCase()
  if (u === 'BORROWER_SELF_SERVICE') return 'Borrower self-service'
  if (u === 'SALES_ASSISTED') return 'Sales-assisted'
  if (u === 'ADMIN_INTERNAL') return 'Internal (admin)'
  return raw
}

function SummaryPanel({ app, applicationId }: { app: ApplicationResponse; applicationId: string }) {
  const statusUrl = typeof window !== 'undefined' ? borrowerStatusPath(applicationId, window.location.origin) : ''
  const phone = readPhoneFromPersonal(app.personalInfo)
  const fullName = readStr(app.personalInfo, 'fullName') || readStr(app.personalInfo, 'name')
  const email = readStr(app.personalInfo, 'email')
  const purpose = readStr(app.personalInfo, 'purpose')
  const im = readStr(app.personalInfo, 'intakeMode')
  const createdBy = readStr(app.personalInfo, 'createdByName') || readStr(app.personalInfo, 'lastSavedByName')
  const assisted = readStr(app.personalInfo, 'assistedBy')
  return (
    <div>
      <h2 className="mb-3 text-sm font-semibold text-slate-900">Application summary</h2>
      <div className="mb-6 flex flex-wrap gap-2">
        <button
          type="button"
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 shadow-sm"
          onClick={async () => {
            if (!statusUrl) return
            try {
              await navigator.clipboard.writeText(statusUrl)
            } catch {
              window.prompt('Copy this link for the borrower:', statusUrl)
            }
          }}
        >
          Copy borrower status link
        </button>
        <a
          href={buildWhatsAppStatusShareUrl(phone, statusUrl)}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 shadow-sm"
        >
          Send WhatsApp message
        </a>
      </div>
      <p className="mb-4 text-xs text-slate-500">
        The status page shows a borrower-friendly summary. WhatsApp uses your browser and a{' '}
        <code className="rounded bg-slate-100 px-1">wa.me</code> link; no message API is integrated yet.
        {!phone ? ' Add phone in personalInfo to pre-fill a chat destination on WhatsApp.' : null}
      </p>
      <div className="mb-4 rounded border border-slate-200 bg-slate-50/80 p-4">
        <h3 className="text-xs font-semibold uppercase text-slate-500">Applicant (from intake)</h3>
        <div className="mt-2 grid gap-2 sm:grid-cols-2 text-sm text-slate-800">
          <div>
            <span className="text-slate-500">Name: </span>
            {fullName || '—'}
          </div>
          <div>
            <span className="text-slate-500">Email: </span>
            {email || '—'}
          </div>
          <div>
            <span className="text-slate-500">Phone: </span>
            {phone || readStr(app.personalInfo, 'mobile') || '—'}
          </div>
          {purpose ? (
            <div className="sm:col-span-2">
              <span className="text-slate-500">Purpose: </span>
              {purpose}
            </div>
          ) : null}
          {im ? (
            <div>
              <span className="text-slate-500">Intake: </span>
              {formatIntakeLine(im)}
            </div>
          ) : null}
          {createdBy ? (
            <div>
              <span className="text-slate-500">Saved / created by: </span>
              {createdBy}
            </div>
          ) : null}
          {assisted ? (
            <div className="sm:col-span-2">
              <span className="text-slate-500">Assistance: </span>
              {assisted}
            </div>
          ) : null}
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Use the <strong>Borrower profile</strong> tab for the full list (address, KYC, bank, employment, consents, etc.).{' '}
          {requiresCollateral(app.loanProduct) ? (
            <span>
              The <strong>Collateral</strong> tab shows declared security and supporting uploads for this product.
            </span>
          ) : null}
        </p>
      </div>
      {requiresCollateral(app.loanProduct) ? (
        <div className="mb-4 rounded border border-slate-200 p-3 text-sm text-slate-800">
          <h3 className="text-xs font-semibold uppercase text-slate-500">Secured product — collateral</h3>
          {(() => {
            const bi = (app.collateralInfo as Record<string, unknown> | null)?.[BORROWER_INTAKE_KEY] as
              | Record<string, unknown>
              | undefined
            if (!bi) {
              return <p className="mt-1 text-amber-800">No collateral intake on file yet. Check the Collateral tab after the applicant completes the step.</p>
            }
            return (
              <p className="mt-1">
                Estimated value (declared):{' '}
                <span className="font-medium tabular-nums">
                  {bi.estimatedValue != null
                    ? new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(Number(bi.estimatedValue))
                    : '—'}
                </span>{' '}
                INR — open the <strong>Collateral</strong> tab for the full breakdown.
              </p>
            )
          })()}
        </div>
      ) : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <Detail label="Number" value={app.applicationNumber} />
        <Detail label="Processing status" value={app.status} />
        <Detail label="Product" value={loanProductLabel(app.loanProduct)} />
        <Detail label="Borrower type" value={borrowerTypeLabel(app.borrowerType)} />
        <Detail label="Requested amount" value={formatMoney(app.requestedAmount)} />
        <Detail label="Tenure (months)" value={app.tenureMonths != null ? String(app.tenureMonths) : '—'} />
        <Detail label="Bureau score" value={app.bureauScore != null ? String(app.bureauScore) : '—'} />
        <Detail label="Credit decision" value={app.creditDecision ?? '—'} />
        <Detail label="eSign transaction" value={app.esignTransactionId ?? '—'} />
        <Detail label="Created" value={formatInstant(app.createdAt)} />
      </div>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs font-medium uppercase text-slate-500">{label}</div>
      <div className="mt-0.5 text-sm text-slate-900">{value}</div>
    </div>
  )
}

function StepTable({ rows }: { rows: StepExecutionRecordView[] }) {
  if (rows.length === 0) {
    return <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">No records yet.</div>
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium text-slate-600">
          <tr>
            <th className="px-3 py-2">Check / process</th>
            <th className="px-3 py-2">Result</th>
            <th className="px-3 py-2">Started</th>
            <th className="px-3 py-2">Completed</th>
            <th className="px-3 py-2">Issue</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((r) => (
            <tr key={r.id}>
              <td className="px-3 py-2 font-mono text-xs text-slate-900">{r.stepType}</td>
              <td className="px-3 py-2 text-slate-800">{r.status}</td>
              <td className="px-3 py-2 text-slate-600 tabular-nums">{formatInstant(r.startedAt)}</td>
              <td className="px-3 py-2 text-slate-600 tabular-nums">{formatInstant(r.completedAt)}</td>
              <td className="px-3 py-2 text-slate-600">{r.errorMessage ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
