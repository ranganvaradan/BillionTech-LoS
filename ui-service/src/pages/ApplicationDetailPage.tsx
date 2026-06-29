import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DocumentsSection } from '@/components/DocumentsSection'
import { ErrorState } from '@/components/ErrorState'
import { KycDetailsSection } from '@/components/KycDetailsSection'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { CollateralIntakeStaffPanel } from '@/components/CollateralIntakeStaffPanel'
import { CollateralPanel } from '@/components/CollateralPanel'
import { AaConsentPanel } from '@/components/AaConsentPanel'
import { BorrowerSubmittedIntakePanel } from '@/components/BorrowerSubmittedIntakePanel'
import { CamSection } from '@/components/CamSection'
import { DisbursementSection } from '@/components/DisbursementSection'
import { EsignSection } from '@/components/EsignSection'
import { SanctionKfsSection } from '@/components/SanctionKfsSection'
import { UnderwritingSection } from '@/components/UnderwritingSection'
import { AnchorDueDiligenceSection } from '@/components/AnchorDueDiligenceSection'
import { ApplicationDeletePanel } from '@/components/ApplicationDeletePanel'
import { useApplication } from '@/hooks/useApplication'
import { useStepExecutions } from '@/hooks/useStepExecutions'
import { borrowerStatusPath, buildWhatsAppStatusShareUrl } from '@/lib/borrowerShare'
import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { formatInstant, formatMoney, isUuid } from '@/lib/format'
import { borrowerTypeLabel } from '@/catalog/borrowerTypes'
import { loanProductLabel, isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import { getActiveWorkflow } from '@/api/workflows'
import { getVkycEligibility, getVkycTimeline } from '@/api/vkyc'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import { buildVkycWorkflowGate, insertVkycTab, type VkycWorkflowGate } from '@/lib/vkycWorkflowGate'
import type { ApplicationResponse } from '@/types/application'
import type { StepExecutionRecordView } from '@/types/stepExecution'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { DetailField } from '@/components/ui/AdminLayout'
import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { VkycDetailsSection } from '@/components/VkycDetailsSection'
import { VkycDownstreamGate } from '@/components/VkycDownstreamGate'
import {
  anchorSkipsPostSanctionSteps,
  idBorrowerSkipsDisbursement,
  isInvoiceDiscountingAnchorApp,
  isInvoiceDiscountingBorrowerApp,
  underwritingTabLabel,
} from '@/lib/invoiceDiscountingFlow'

const TABS = [
  { id: 'summary' as const, label: 'Summary' },
  { id: 'borrower' as const, label: 'Borrower profile' },
  { id: 'collateral' as const, label: 'Collateral' },
  { id: 'kyc' as const, label: 'KYC' },
  { id: 'documents' as const, label: 'Documents' },
  { id: 'bankData' as const, label: 'Bank Data' },
  { id: 'underwriting' as const, label: 'Underwriting' },
  { id: 'cam' as const, label: 'CAM' },
  { id: 'sanction' as const, label: 'Sanction' },
  { id: 'esign' as const, label: 'eSign' },
  { id: 'disbursement' as const, label: 'Disbursement' },
  { id: 'history' as const, label: 'History' },
]

type ApplicationTabId = (typeof TABS)[number]['id'] | 'vkyc'

/** AA bank-data tab applies to borrower-facing retail / business credit journeys, not anchor onboarding. */
function isAaApplicable(app: ApplicationResponse): boolean {
  if (app.intakeSegment === 'ANCHOR') return false
  if (isInvoiceDiscountingAnchorApp(app)) return false
  return true
}

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<ApplicationTabId>('summary')
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
      const workflow = await getActiveWorkflow(app.borrowerType, app.loanProduct, app.intakeSegment ?? 'BORROWER')
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
    const L = applicationPartyLabels(app?.intakeSegment)
    const skipPostSanction = app ? anchorSkipsPostSanctionSteps(app) : false
    const skipDisbursement = app ? idBorrowerSkipsDisbursement(app) : false
    const hidden = skipPostSanction
      ? new Set<string>(['cam', 'esign', 'disbursement'])
      : skipDisbursement
        ? new Set<string>(['disbursement'])
        : new Set<string>()
    if (app && !requiresCollateral(app.loanProduct)) hidden.add('collateral')
    if (app && !isAaApplicable(app)) hidden.add('bankData')
    const base: Array<{ id: ApplicationTabId; label: string }> = TABS.filter((t) => !hidden.has(t.id)).map((t) => {
      if (t.id === 'borrower') return { ...t, label: L.profileTab }
      if (t.id === 'underwriting') {
        return { ...t, label: underwritingTabLabel(app?.intakeSegment, app?.loanProduct) }
      }
      return t
    }) as Array<{ id: ApplicationTabId; label: string }>
    return insertVkycTab(base, vkycGate)
  }, [app, vkycGate])
  useEffect(() => {
    if (app && idBorrowerSkipsDisbursement(app) && tab === 'disbursement') {
      setTab('esign')
    }
  }, [app, tab])

  useEffect(() => {
    if (!vkycGate.visible && tab === 'vkyc') setTab('summary')
  }, [vkycGate.visible, tab])

  useEffect(() => {
    if (!app) return
    if (tab === 'collateral' && !requiresCollateral(app.loanProduct)) setTab('summary')
    if (tab === 'bankData' && !isAaApplicable(app)) setTab('summary')
  }, [app, tab])

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
    <div className="bt-app-detail">
      <PageHeader
        title="Application details"
        description={
          app && anchorSkipsPostSanctionSteps(app)
            ? 'Review KYC, anchor rating, and sanction for this anchor onboarding case.'
            : app && isInvoiceDiscountingBorrowerApp(app)
              ? 'Review KYC, underwriting, CAM, sanction, terms eSign, and PLP program linkage for this invoice discounting borrower.'
              : 'Review KYC, underwriting, CAM, sanction, KFS, eSign, and disbursement for this loan.'
        }
        actions={app && id ? <ApplicationDeletePanel applicationId={id} app={app} /> : null}
      />
      <p className="mb-4 text-sm">
        <Link to="/applications" className="font-medium text-[var(--bt-orange)] hover:underline">
          ← Back to applications
        </Link>
      </p>

      {appLoading && !app && <LoadingState label="Loading application…" />}
      {appError && !app && <ErrorState message={appError} />}

      {valid && (
        <div className="bt-tabs" role="tablist" aria-label="Application sections">
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id as ApplicationTabId)}
                className={tab === t.id ? 'bt-tab active' : 'bt-tab'}
              >
                {t.label}
              </button>
            ))}
        </div>
      )}

      {valid && id && (
        <div className="bt-card p-5">
          {appLoading && !app ? (
            <LoadingState label="Loading application data…" />
          ) : app ? (
            <>
              {appLoading ? (
                <p className="mb-3 text-sm text-slate-500">Refreshing application data…</p>
              ) : null}
              {tab === 'summary' && <SummaryPanel app={app} applicationId={id} />}
              {tab === 'borrower' && (() => {
                const partyLabels = applicationPartyLabels(app.intakeSegment)
                return (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">{partyLabels.submittedDetailsTitle}</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    Information captured from the intake journey (self-service, sales-assisted, or internal). This view is
                    for credit and operations; it is not shown to the {partyLabels.partyLower} on the public status page in
                    this form.
                  </p>
                  <BorrowerSubmittedIntakePanel app={app} />
                </div>
                )
              })()}
              {tab === 'collateral' && requiresCollateral(app.loanProduct) && (
                <div className="space-y-6">
                  <div>
                    <h2 className="mb-1 text-lg font-medium text-slate-900">Collateral</h2>
                    <p className="mb-4 text-sm text-slate-600">
                      Official valuations, LTV checks, and intake security details for this secured product.
                    </p>
                    <CollateralPanel
                      applicationId={id}
                      loanAmount={app.requestedAmount}
                      applicationStatus={app.status}
                    />
                  </div>
                  <div>
                    <h3 className="mb-1 text-base font-medium text-slate-900">Intake declaration</h3>
                    <p className="mb-4 text-sm text-slate-600">
                      Security details and uploads submitted with the application (LAP, Loan Against Shares, Gold Loan, etc.).
                    </p>
                    <CollateralIntakeStaffPanel app={app} />
                  </div>
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
                  intakeSegment={app.intakeSegment}
                  onApplicationRefetch={refetchApp}
                  onWorkflowStateRefetch={reloadVkycWorkflowState}
                />
              )}
              {tab === 'documents' && (
                <DocumentsSection
                  applicationId={id}
                  intakeSegment={app.intakeSegment}
                  appStatus={app.status}
                  loanProduct={app.loanProduct}
                />
              )}
              {tab === 'bankData' && isAaApplicable(app) && (
                <div>
                  <h2 className="mb-1 text-lg font-medium text-slate-900">Bank Data / Account Aggregator</h2>
                  <p className="mb-4 text-sm text-slate-600">
                    RBI AA consent lifecycle and fetched bank statement data for income and obligation verification.
                  </p>
                  <AaConsentPanel applicationId={id} />
                </div>
              )}
              {tab === 'underwriting' &&
                (isInvoiceDiscountingAnchorApp(app) ? (
                  <AnchorDueDiligenceSection applicationId={id} app={app} onRefetch={refetchApp} />
                ) : (
                  <UnderwritingSection applicationId={id} app={app} onRefetch={refetchApp} />
                ))}
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
                      key={id}
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
  const partyLabels = applicationPartyLabels(app.intakeSegment)
  const statusUrl = typeof window !== 'undefined' ? borrowerStatusPath(applicationId, window.location.origin) : ''
  const phone = readPhoneFromPersonal(app.personalInfo)
  const fullName = readStr(app.personalInfo, 'fullName') || readStr(app.personalInfo, 'name')
  const email = readStr(app.personalInfo, 'email')
  const purpose = readStr(app.personalInfo, 'purpose')
  const im = readStr(app.personalInfo, 'intakeMode')
  const createdBy = readStr(app.personalInfo, 'createdByName') || readStr(app.personalInfo, 'lastSavedByName')
  const assisted = readStr(app.personalInfo, 'assistedBy')
  const manualOverridesRaw = (app.financialInfo as Record<string, unknown> | null)?.manualOverrides
  const manualOverrideCount = Array.isArray(manualOverridesRaw) ? manualOverridesRaw.length : 0
  const hasManualOverride =
    String((app.financialInfo as Record<string, unknown> | null)?.manualOverrideFlag ?? '').toUpperCase() ===
      'MANUALLY_OVERRIDDEN' || manualOverrideCount > 0
  return (
    <div>
      <h2 className="mb-3 bt-card-title">Application summary</h2>
      <div className="mb-4 flex flex-wrap gap-2">
        {app.intakeSegment === 'ANCHOR' ? (
          <span className="inline-flex rounded-md border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-900">
            Anchor onboarding (invoice discounting)
          </span>
        ) : null}
        <button
          type="button"
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 shadow-sm"
          onClick={async () => {
            if (!statusUrl) return
            try {
              await navigator.clipboard.writeText(statusUrl)
            } catch {
              window.prompt(`Copy this link for the ${partyLabels.partyLower}:`, statusUrl)
            }
          }}
        >
          {partyLabels.copyStatusLink}
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
        The status page shows a {partyLabels.statusPageAudience} summary. WhatsApp uses your browser and a{' '}
        <code className="rounded bg-slate-100 px-1">wa.me</code> link; no message API is integrated yet.
        {!phone ? ' Add phone in personalInfo to pre-fill a chat destination on WhatsApp.' : null}
      </p>
      <AppSectionCard tone="hero" className="mb-4" title="Applicant (from intake)" unstyledBody>
        <div className="p-4">
        <div className="grid gap-2 sm:grid-cols-2 text-sm text-[var(--bt-gray-800)]">
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
          Use the <strong>{partyLabels.profileTab}</strong> tab for the full list (address, KYC, bank, employment, consents, etc.).{' '}
          {requiresCollateral(app.loanProduct) ? (
            <span>
              The <strong>Collateral</strong> tab shows declared security and supporting uploads for this product.
            </span>
          ) : null}
        </p>
        </div>
      </AppSectionCard>
      {requiresCollateral(app.loanProduct) ? (
        <AppSectionCard tone="success" className="mb-4 text-sm text-slate-800" title="Secured product — collateral" unstyledBody>
          <div className="p-4">
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
        </AppSectionCard>
      ) : null}
      <AppSectionCard tone="default" className="mb-4" unstyledBody>
        <div className="grid gap-4 p-4 sm:grid-cols-2">
        <Detail label="Number" value={app.applicationNumber} />
        <Detail
          label="Processing status"
          value={hasManualOverride ? `${app.status} (MANUALLY_OVERRIDDEN)` : app.status}
        />
        <Detail label="Product" value={loanProductLabel(app.loanProduct)} />
        <Detail label={partyLabels.entityTypeDetail} value={borrowerTypeLabel(app.borrowerType)} />
        <Detail label="Requested amount" value={formatMoney(app.requestedAmount)} />
        <Detail label="Tenure (months)" value={app.tenureMonths != null ? String(app.tenureMonths) : '—'} />
        {app.intakeSegment === 'ANCHOR' && isInvoiceDiscountingProduct(app.loanProduct) ? (
          <>
            <Detail
              label="Anchor rating"
              value={(() => {
                const dd = (app.financialInfo as Record<string, unknown> | null)?.anchorDueDiligence as
                  | { creditRating?: string; score?: number }
                  | undefined
                if (!dd?.creditRating) return '—'
                return dd.score != null ? `${dd.creditRating} (score ${dd.score})` : dd.creditRating
              })()}
            />
            <Detail label="Credit decision" value={app.creditDecision ?? '—'} />
          </>
        ) : (
          <>
            <Detail label="Bureau score" value={app.bureauScore != null ? String(app.bureauScore) : '—'} />
            <Detail label="Credit decision" value={app.creditDecision ?? '—'} />
          </>
        )}
        <Detail label="eSign transaction" value={app.esignTransactionId ?? '—'} />
        <Detail label="Created" value={formatInstant(app.createdAt)} />
        </div>
      </AppSectionCard>
      {hasManualOverride ? (
        <AppSectionCard
          tone="override"
          title="Manual override applied"
          subtitle="Original failures remain traceable in workflow history and audit trail."
          badge={<span className="bt-section-card__chip bt-section-card__chip--override">Manually overridden</span>}
        />
      ) : null}
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return <DetailField label={label} value={value} />
}

function formatWorkflowHistoryStepLabel(stepType: string): string {
  if (stepType === 'VKYC_PKYC_COMPLETED') return 'VKYC fallback — Completed through Physical KYC'
  return stepType
}

function workflowHistoryIssueCell(r: StepExecutionRecordView): string {
  if (r.stepType === 'VKYC_PKYC_COMPLETED' && r.outputJson) {
    try {
      const o = JSON.parse(r.outputJson) as Record<string, unknown>
      const pieces: string[] = []
      if (o.message != null && String(o.message).trim()) pieces.push(String(o.message))
      if (o.reason != null && String(o.reason).trim()) pieces.push(`Reason: ${String(o.reason)}`)
      const by = o.verifiedByUserId != null ? String(o.verifiedByUserId).trim() : ''
      if (by) pieces.push(`Verified by: ${by}`)
      if (pieces.length) return pieces.join(' · ')
    } catch {
      /* ignore malformed JSON */
    }
  }
  return r.errorMessage ?? '—'
}

function StepTable({ rows }: { rows: StepExecutionRecordView[] }) {
  if (rows.length === 0) {
    return <div className="bt-card bt-empty-state p-4">No records yet.</div>
  }
  return (
    <div className="bt-card overflow-x-auto">
        <table className="bt-table min-w-full">
        <thead>
          <tr>
            <th>Check / process</th>
            <th>Result</th>
            <th>Started</th>
            <th>Completed</th>
            <th>Issue</th>
          </tr>
        </thead>
        <tbody className="">
          {rows.map((r) => (
            <tr key={r.id}>
              <td className="px-3 py-2 text-xs text-slate-900">
                {r.stepType === 'VKYC_PKYC_COMPLETED' ? (
                  <span>{formatWorkflowHistoryStepLabel(r.stepType)}</span>
                ) : (
                  <span className="font-mono">{r.stepType}</span>
                )}
              </td>
              <td className="px-3 py-2 text-slate-800">{r.status}</td>
              <td className="px-3 py-2 text-slate-600 tabular-nums">{formatInstant(r.startedAt)}</td>
              <td className="px-3 py-2 text-slate-600 tabular-nums">{formatInstant(r.completedAt)}</td>
              <td className="px-3 py-2 text-slate-600">{workflowHistoryIssueCell(r)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
