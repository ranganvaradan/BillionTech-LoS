import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import {
  canRunKycFlow,
  canRunUnderwriting,
  isRelationshipManager,
} from '@/auth/types'
import { DocumentsSection } from '@/components/DocumentsSection'
import { ErrorState } from '@/components/ErrorState'
import { KycDetailsSection } from '@/components/KycDetailsSection'
import { GstAnalysisAdminPanel } from '@/components/GstAnalysisAdminPanel'
import { LoadingState } from '@/components/LoadingState'
import { CollateralIntakeStaffPanel } from '@/components/CollateralIntakeStaffPanel'
import { CollateralPanel } from '@/components/CollateralPanel'
import { AaConsentPanel } from '@/components/AaConsentPanel'
import { BorrowerSubmittedIntakePanel } from '@/components/BorrowerSubmittedIntakePanel'
import { CustomerRequirementsPanel } from '@/components/requirements/CustomerRequirementsPanel'
import { CategorySelectionPanel } from '@/components/category/CategorySelectionPanel'
import { showStagingDemoNav } from '@/nav/workspaceNav'
import { BorrowerSubmissionReviewPanel } from '@/components/application/BorrowerSubmissionReviewPanel'
import { CamSection } from '@/components/CamSection'
import { DisbursementSection } from '@/components/DisbursementSection'
import { EsignSection } from '@/components/EsignSection'
import { SanctionKfsSection } from '@/components/SanctionKfsSection'
import { UnderwritingSection } from '@/components/UnderwritingSection'
import { AnchorDueDiligenceSection } from '@/components/AnchorDueDiligenceSection'
import { ApplicationDeletePanel } from '@/components/ApplicationDeletePanel'
import { ApplicationHistoryPanel } from '@/components/ApplicationHistoryPanel'
import { useApplication } from '@/hooks/useApplication'
import { useApplicationTimeline } from '@/hooks/useApplicationTimeline'
import { useStepExecutions } from '@/hooks/useStepExecutions'
import { borrowerStatusPath, buildWhatsAppStatusShareUrl } from '@/lib/borrowerShare'
import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { formatInstant, formatMoney, isUuid } from '@/lib/format'
import { borrowerTypeLabel } from '@/catalog/borrowerTypes'
import { loanProductLabel, isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import { lmsTenureUnitLabel, tenureMagnitudeLabel } from '@/catalog/lmsTenureUnits'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import { getWorkflow } from '@/api/workflows'
import { getVkycEligibility, getVkycTimeline } from '@/api/vkyc'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import { buildVkycWorkflowGate, type VkycWorkflowGate } from '@/lib/vkycWorkflowGate'
import type { ApplicationResponse } from '@/types/application'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { DetailField } from '@/components/ui/AdminLayout'
import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { BtBadge } from '@/components/ui/BtBadge'
import { VkycDetailsSection } from '@/components/VkycDetailsSection'
import { VkycDownstreamGate } from '@/components/VkycDownstreamGate'
import { displayBorrowerName } from '@/lib/intake/applicationPartyResolve'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import { formatAssessmentOutcome } from '@/lib/credit/assessmentPresentation'
import {
  collateralOverviewCopy,
  defaultWorkbenchTab,
  kycWorkbenchSubSections,
  nextActionUsesIntakeHref,
  resolveWorkbenchNextAction,
  showCamInDecision,
  showDisbursementInDecision,
  showEsignInDecision,
  visibleWorkbenchTabs,
  type WorkbenchTabId,
} from '@/lib/applicationWorkbench'
import { staffCanContinueIntake } from '@/lib/intake/intakeResume'
import { defaultKycSubSection } from '@/lib/ux/presentationProfile'
import {
  buildDecisionSummary,
  completionDisburseActive,
  completionEsignActive,
  type DecisionFocusSection,
} from '@/lib/decision/decisionPresentation'
import { DecisionSummaryPanel } from '@/components/decision/DecisionSummaryPanel'
import { isInvoiceDiscountingAnchorApp } from '@/lib/invoiceDiscountingFlow'

type KycSubSection = 'identity' | 'vkyc' | 'banking' | 'verification'

/** AA bank-data applies to borrower-facing retail / business credit journeys, not anchor onboarding. */
function isAaApplicable(app: ApplicationResponse): boolean {
  if (app.intakeSegment === 'ANCHOR') return false
  if (isInvoiceDiscountingAnchorApp(app)) return false
  return true
}

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const role = user?.role ?? ''
  const isRm = user ? isRelationshipManager(user.role) : false

  const [tab, setTab] = useState<WorkbenchTabId>('overview')
  const [tabReady, setTabReady] = useState(false)
  const [kycSub, setKycSub] = useState<KycSubSection>('identity')
  const [profileOpen, setProfileOpen] = useState(false)
  const [moreOpen, setMoreOpen] = useState(false)

  const [activeWorkflow, setActiveWorkflow] = useState<WorkflowConfigResponse | null>(null)
  const [vkycEligibility, setVkycEligibility] = useState<Record<string, unknown> | null>(null)
  const [vkycTimeline, setVkycTimeline] = useState<Record<string, unknown> | null>(null)

  const valid = id && isUuid(id)
  const { data: app, loading: appLoading, error: appError, refetch: refetchApp } = useApplication(
    valid ? id : undefined,
  )
  const { data: steps, refetch: refetchSteps } = useStepExecutions(valid ? id : undefined)
  const { data: timeline, loading: timelineLoading, error: timelineError } = useApplicationTimeline(
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
      const boundId = (app.workflowId ?? '').trim()
      const workflow = boundId
        ? await getWorkflow(boundId)
        : null
      setActiveWorkflow(workflow)
      const hasVkyc = (workflow?.steps ?? []).some((s) => {
        const step = String((s as Record<string, unknown>).step ?? '').trim().toUpperCase()
        return step === 'VIDEO_KYC' || step === 'VKYC'
      })
      if (hasVkyc) {
        const [eligibility, tl] = await Promise.all([getVkycEligibility(id), getVkycTimeline(id)])
        setVkycEligibility(eligibility)
        setVkycTimeline(tl)
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

  const workbenchTabs = useMemo(() => visibleWorkbenchTabs({ role }), [role])
  const visibleIds = useMemo(() => workbenchTabs.map((t) => t.id), [workbenchTabs])

  useEffect(() => {
    setTabReady(false)
  }, [id])

  useEffect(() => {
    if (!app || tabReady) return
    const preferred = defaultWorkbenchTab(app.status, visibleIds)
    setTab(preferred)
    if (preferred === 'kyc') {
      setKycSub(defaultKycSubSection({ role, vkycVisible: vkycGate.visible }))
    }
    setTabReady(true)
  }, [app, tabReady, visibleIds, role, vkycGate.visible])

  useEffect(() => {
    if (!visibleIds.includes(tab)) {
      setTab(visibleIds[0] ?? 'overview')
    }
  }, [visibleIds, tab])

  useEffect(() => {
    if (!vkycGate.visible && kycSub === 'vkyc') setKycSub('identity')
  }, [vkycGate.visible, kycSub])

  useEffect(() => {
    if (app && !isAaApplicable(app) && kycSub === 'banking') setKycSub('identity')
  }, [app, kycSub])

  const nextAction = useMemo(
    () => (app ? resolveWorkbenchNextAction({ app, role }) : null),
    [app, role],
  )

  const goNext = () => {
    if (!nextAction || !app || !id) return
    if (nextActionUsesIntakeHref(app, role)) {
      window.location.assign(`/applications/${id}/intake`)
      return
    }
    setTab(nextAction.targetTab)
    if (nextAction.targetTab === 'kyc' && (app.status === 'KYC_IN_PROGRESS' || app.status === 'KYC_FAILED')) {
      setKycSub(defaultKycSubSection({ role, vkycVisible: vkycGate.visible }))
    }
  }

  if (!valid) {
    return (
      <div>
        <h1 className="bt-page-title">Application</h1>
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
    <div className="bt-app-detail space-y-4">
      <p className="text-sm">
        <Link to="/applications" className="font-medium text-[var(--bt-orange)] hover:underline">
          ← Applications
        </Link>
      </p>

      {appLoading && !app && <LoadingState label="Loading application…" />}
      {appError && !app && <ErrorState message={appError} />}

      {app ? (
        <ApplicationWorkbenchHeader
          app={app}
          applicationId={id}
          nextAction={nextAction}
          onNext={goNext}
          useIntakeHref={nextActionUsesIntakeHref(app, role)}
          moreOpen={moreOpen}
          setMoreOpen={setMoreOpen}
        />
      ) : null}

      {app?.status === 'CONSENT_PENDING' && app.intakeOwner === 'BORROWER' ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          Waiting for the borrower to complete intake in the borrower portal. They can resume from the link in their
          notification email.
        </div>
      ) : null}

      {valid && app ? (
        <div className="bt-tabs overflow-x-auto" role="tablist" aria-label="Application workbench">
          {workbenchTabs.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
              className={tab === t.id ? 'bt-tab active' : 'bt-tab'}
            >
              {t.label}
            </button>
          ))}
        </div>
      ) : null}

      {valid && id && app ? (
        <div className="bt-card p-5">
          {appLoading ? <p className="mb-3 text-sm text-slate-500">Refreshing application data…</p> : null}

          {tab === 'overview' && (
            <OverviewWorkspace
              app={app}
              applicationId={id}
              role={user?.role ?? ''}
              onRefetch={refetchApp}
              profileOpen={profileOpen}
              setProfileOpen={setProfileOpen}
              nextDescription={nextAction?.description}
            />
          )}

          {tab === 'kyc' && (
            <KycWorkspace
              applicationId={id}
              app={app}
              vkycGate={vkycGate}
              kycSub={kycSub}
              setKycSub={setKycSub}
              allowRunKyc={user ? canRunKycFlow(user.role) : false}
              onApplicationRefetch={refetchApp}
              onStepsRefetch={refetchSteps}
              onWorkflowStateRefetch={reloadVkycWorkflowState}
            />
          )}

          {tab === 'credit' && !isRm && (
            <CreditAssessmentWorkspace
              applicationId={id}
              app={app}
              onRefetch={refetchApp}
              allowRunUnderwriting={user ? canRunUnderwriting(user.role) : false}
            />
          )}

          {tab === 'decision' && (
            <DecisionWorkspace
              applicationId={id}
              app={app}
              role={role}
              vkycGate={vkycGate}
              vkycTimeline={vkycTimeline}
              activeWorkflow={activeWorkflow}
              onRefetch={refetchApp}
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

          {tab === 'history' && (
            <div>
              {timelineLoading && <LoadingState label="Loading history…" />}
              {timelineError && <ErrorState message={timelineError} />}
              {timeline && !timelineLoading && <ApplicationHistoryPanel timeline={timeline} />}
            </div>
          )}
        </div>
      ) : null}
    </div>
  )
}

function ApplicationWorkbenchHeader({
  app,
  applicationId,
  nextAction,
  onNext,
  useIntakeHref,
  moreOpen,
  setMoreOpen,
}: {
  app: ApplicationResponse
  applicationId: string
  nextAction: ReturnType<typeof resolveWorkbenchNextAction>
  onNext: () => void
  useIntakeHref: boolean
  moreOpen: boolean
  setMoreOpen: (v: boolean) => void
}) {
  const decision =
    app.creditDecision && String(app.creditDecision).trim() ? String(app.creditDecision) : null

  return (
    <header className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-4 py-3 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-lg font-semibold text-[var(--bt-gray-900)]">{app.applicationNumber}</h1>
            <BtBadge status={app.status}>{formatStatusLabel(app.status)}</BtBadge>
            {decision ? (
              <span className="rounded border border-[var(--bt-gray-200)] bg-[var(--bt-gray-50)] px-1.5 py-0.5 text-[11px] font-medium text-[var(--bt-gray-700)]">
                Decision: {formatAssessmentOutcome(decision)}
              </span>
            ) : null}
          </div>
          <p className="mt-1 truncate text-sm text-[var(--bt-gray-800)]">{displayBorrowerName(app)}</p>
          <p className="mt-0.5 text-xs text-[var(--bt-gray-500)]">
            {loanProductLabel(app.loanProduct)}
            {app.requestedAmount != null ? ` · ${formatMoney(app.requestedAmount)}` : ''}
          </p>
        </div>

        <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
          {nextAction ? (
            useIntakeHref ? (
              <Link to={`/applications/${applicationId}/intake`} className="bt-btn bt-btn-primary">
                {nextAction.label}
              </Link>
            ) : (
              <button type="button" className="bt-btn bt-btn-primary" onClick={onNext}>
                {nextAction.label}
              </button>
            )
          ) : null}

          <div className="relative">
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              aria-expanded={moreOpen}
              onClick={() => setMoreOpen(!moreOpen)}
            >
              More ▾
            </button>
            {moreOpen ? (
              <>
                <button
                  type="button"
                  className="fixed inset-0 z-10 cursor-default"
                  aria-label="Close menu"
                  onClick={() => setMoreOpen(false)}
                />
                <div className="absolute right-0 z-20 mt-1 min-w-[12rem] rounded-md border border-[var(--bt-gray-200)] bg-white p-2 shadow-lg">
                  <ApplicationDeletePanel
                    applicationId={applicationId}
                    app={app}
                    buttonClassName="bt-btn bt-btn-danger bt-btn-sm w-full"
                    buttonLabel="Delete application"
                  />
                </div>
              </>
            ) : null}
          </div>
        </div>
      </div>
      {nextAction ? (
        <p className="mt-2 text-xs text-[var(--bt-gray-500)]">
          <span className="font-medium text-[var(--bt-gray-700)]">Next step: </span>
          {nextAction.description}
        </p>
      ) : null}
    </header>
  )
}

function WorkspaceSection({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-base font-semibold text-[var(--bt-gray-900)]">{title}</h2>
        {description ? <p className="mt-0.5 text-sm text-[var(--bt-gray-500)]">{description}</p> : null}
      </div>
      {children}
    </section>
  )
}

function OverviewWorkspace({
  app,
  applicationId,
  role,
  onRefetch,
  profileOpen,
  setProfileOpen,
  nextDescription,
}: {
  app: ApplicationResponse
  applicationId: string
  role: string
  onRefetch: () => void | Promise<unknown>
  profileOpen: boolean
  setProfileOpen: (v: boolean) => void
  nextDescription?: string
}) {
  const partyLabels = applicationPartyLabels(app.intakeSegment)
  return (
    <div className="space-y-6">
      {nextDescription ? (
        <p className="rounded-md border border-[var(--bt-orange-border)] bg-[var(--bt-orange-light)] px-3 py-2 text-sm text-[var(--bt-gray-800)]">
          {nextDescription}
        </p>
      ) : null}
      <BorrowerSubmissionReviewPanel app={app} onRefetch={onRefetch} />
      <CategorySelectionPanel
        applicationId={applicationId}
        actorRole="RM"
        actor="rm"
        allowDraftSimulation={showStagingDemoNav()}
      />
      <CustomerRequirementsPanel applicationId={applicationId} variant="staff" actorRole="RM" />
      <SummaryPanel app={app} applicationId={applicationId} role={role} />
      <div className="rounded-lg border border-[var(--bt-gray-200)]">
        <button
          type="button"
          className="flex w-full items-center justify-between px-4 py-3 text-left text-sm font-medium text-[var(--bt-gray-900)] hover:bg-[var(--bt-gray-50)]"
          onClick={() => setProfileOpen(!profileOpen)}
          aria-expanded={profileOpen}
        >
          <span>{partyLabels.submittedDetailsTitle}</span>
          <span className="text-xs text-[var(--bt-gray-500)]">{profileOpen ? 'Hide' : 'Show full profile'}</span>
        </button>
        {profileOpen ? (
          <div className="border-t border-[var(--bt-gray-200)] px-4 py-4">
            <p className="mb-3 text-sm text-slate-600">
              Information captured from the intake journey. For credit and operations review — not the public status page
              format.
            </p>
            <BorrowerSubmittedIntakePanel app={app} />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function KycWorkspace({
  applicationId,
  app,
  vkycGate,
  kycSub,
  setKycSub,
  allowRunKyc,
  onApplicationRefetch,
  onStepsRefetch,
  onWorkflowStateRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  vkycGate: VkycWorkflowGate
  kycSub: KycSubSection
  setKycSub: (s: KycSubSection) => void
  allowRunKyc: boolean
  onApplicationRefetch: () => void
  onStepsRefetch: () => void
  onWorkflowStateRefetch: () => void | Promise<void>
}) {
  const showBanking = isAaApplicable(app)
  const subs = kycWorkbenchSubSections({
    vkycVisible: vkycGate.visible,
    bankingVisible: showBanking,
  })

  return (
    <div className="space-y-4">
      <WorkspaceSection
        title="KYC"
        description="Identity verification, business verification (GST), and supporting evidence."
      >
        <nav className="bt-tabs mb-0 overflow-x-auto" aria-label="KYC sections">
          {subs.map((s) => (
            <button
              key={s.id}
              type="button"
              className={kycSub === s.id ? 'bt-tab active' : 'bt-tab'}
              onClick={() => setKycSub(s.id)}
            >
              {s.label}
            </button>
          ))}
        </nav>
      </WorkspaceSection>

      {kycSub === 'identity' && (
        <KycDetailsSection
          applicationId={applicationId}
          app={app}
          onApplicationRefetch={onApplicationRefetch}
          onStepsRefetch={onStepsRefetch}
          className="mb-0 border-0 p-0 shadow-none"
          allowRunKyc={allowRunKyc}
        />
      )}

      {kycSub === 'vkyc' && vkycGate.visible && (
        <VkycDetailsSection
          applicationId={applicationId}
          workflowGate={vkycGate}
          intakeSegment={app.intakeSegment}
          onApplicationRefetch={onApplicationRefetch}
          onWorkflowStateRefetch={onWorkflowStateRefetch}
        />
      )}

      {kycSub === 'banking' && showBanking && (
        <div>
          <h3 className="mb-1 text-sm font-semibold text-slate-900">Banking Evidence</h3>
          <p className="mb-4 text-sm text-slate-600">
            RBI Account Aggregator consent lifecycle and fetched bank statement data for income and obligation
            verification.
          </p>
          <AaConsentPanel applicationId={applicationId} />
        </div>
      )}

      {kycSub === 'verification' && (
        <div className="space-y-4">
          <div>
            <h3 className="mb-1 text-sm font-semibold text-slate-900">GST / Business Verification</h3>
            <p className="mb-4 text-sm text-slate-600">
              GST analysis and business verification for this application. Day-to-day identity checks stay under
              Identity Verification.
            </p>
            <GstAnalysisAdminPanel
              applicationId={applicationId}
              onUpdated={() => {
                void onApplicationRefetch()
                void onStepsRefetch()
              }}
            />
          </div>
          <details className="rounded-lg border border-[var(--bt-gray-200)] bg-white">
            <summary className="cursor-pointer px-4 py-3 text-sm font-medium text-[var(--bt-gray-500)]">
              Additional verification details
            </summary>
            <div className="border-t border-[var(--bt-gray-200)] px-4 py-3 text-xs text-[var(--bt-gray-500)]">
              Technical provider statuses and advanced diagnostics appear with the GST tools above when available.
              CIN / MCA / Udyam appear in Identity Verification when those steps are part of this product workflow.
            </div>
          </details>
        </div>
      )}
    </div>
  )
}

function CreditAssessmentWorkspace({
  applicationId,
  app,
  onRefetch,
  allowRunUnderwriting,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void
  allowRunUnderwriting: boolean
}) {
  const showCollateral = requiresCollateral(app.loanProduct)
  return (
    <div className="space-y-8">
      <WorkspaceSection
        title="Credit Assessment"
        description="What the system found, why, supporting evidence, and what the credit user should review."
      >
        {isInvoiceDiscountingAnchorApp(app) ? (
          <AnchorDueDiligenceSection applicationId={applicationId} app={app} onRefetch={onRefetch} />
        ) : (
          <UnderwritingSection
            applicationId={applicationId}
            app={app}
            onRefetch={onRefetch}
            allowRunUnderwriting={allowRunUnderwriting}
          />
        )}
      </WorkspaceSection>

      {showCollateral ? (
        <WorkspaceSection
          title="Collateral"
          description="Official valuations, LTV checks, and intake security details for this secured product."
        >
          <CollateralPanel applicationId={applicationId} loanAmount={app.requestedAmount} />
          <div className="mt-6">
            <h3 className="mb-1 text-sm font-medium text-slate-900">Intake declaration</h3>
            <p className="mb-3 text-sm text-slate-600">
              Security details and uploads submitted with the application.
            </p>
            <CollateralIntakeStaffPanel app={app} />
          </div>
        </WorkspaceSection>
      ) : null}
    </div>
  )
}

function DecisionWorkspace({
  applicationId,
  app,
  role,
  vkycGate,
  vkycTimeline,
  activeWorkflow,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  role: string
  vkycGate: VkycWorkflowGate
  vkycTimeline: Record<string, unknown> | null
  activeWorkflow: WorkflowConfigResponse | null
  onRefetch: () => void
}) {
  const showCam = showCamInDecision({ role, app })
  const showEsign = showEsignInDecision({ role })
  const showDisburse = showDisbursementInDecision({ role, app })
  const vkycStatus = vkycTimelineVkycStatus(vkycTimeline)
  const workflowPosition = activeWorkflow?.workflowPosition ?? ''
  const decisionSummary = buildDecisionSummary(app)
  const esignActive = completionEsignActive(app.status)
  const disburseActive = completionDisburseActive(app.status)
  const sanctionConditions =
    typeof app.financialInfo?.sanctionConditions === 'string'
      ? String(app.financialInfo.sanctionConditions).trim()
      : typeof (app as { sanctionConditions?: string }).sanctionConditions === 'string'
        ? String((app as { sanctionConditions?: string }).sanctionConditions).trim()
        : ''

  function focusSection(section: NonNullable<DecisionFocusSection>) {
    const el = document.querySelector(`[data-decision-section="${section}"]`)
    if (el instanceof HTMLElement) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  return (
    <div className="space-y-10" data-testid="decision-workspace">
      <DecisionSummaryPanel model={decisionSummary} onFocusSection={focusSection} />

      {showCam ? (
        <div data-decision-section="cam">
          <WorkspaceSection
            title="Credit Appraisal (CAM)"
            description="Human credit appraisal memo — save, submit, send back, approve, and download PDF."
          >
            <VkycDownstreamGate
              blocked={vkycGate.downstreamBlocked.cam}
              vkycStatus={vkycStatus}
              workflowPosition={workflowPosition}
            >
              <CamSection applicationId={applicationId} app={app} onRefetch={onRefetch} />
            </VkycDownstreamGate>
          </WorkspaceSection>
        </div>
      ) : null}

      <div data-decision-section="sanction">
        <WorkspaceSection
          title="Sanction & Terms"
          description="Sanction terms, KFS, and Program / PLP actions where applicable."
        >
          <VkycDownstreamGate
            blocked={vkycGate.downstreamBlocked.sanction}
            vkycStatus={vkycStatus}
            workflowPosition={workflowPosition}
          >
            <SanctionKfsSection
              key={applicationId}
              applicationId={applicationId}
              app={app}
              onRefetch={onRefetch}
              suppressCamGuidance={showCam}
            />
          </VkycDownstreamGate>
        </WorkspaceSection>
      </div>

      {sanctionConditions ? (
        <WorkspaceSection
          title="Conditions / Requirements"
          description="Conditions recorded with sanction terms."
        >
          <p className="whitespace-pre-wrap text-sm text-slate-800">{sanctionConditions}</p>
        </WorkspaceSection>
      ) : null}

      {(showEsign || showDisburse) && (
        <WorkspaceSection title="Completion" description="Customer eSign and disbursement readiness.">
          <div className="space-y-8">
            {showEsign ? (
              <div
                data-decision-section="esign"
                className={esignActive ? undefined : 'opacity-90'}
              >
                <h3 className="mb-1 text-sm font-semibold text-slate-900">eSign</h3>
                {!esignActive ? (
                  <p className="mb-2 text-sm text-slate-600">
                    eSign becomes primary after sanction terms / KFS are issued.
                  </p>
                ) : (
                  <p className="mb-3 text-sm text-slate-600">
                    Current status and primary eSign actions for this stage.
                  </p>
                )}
                <VkycDownstreamGate
                  blocked={vkycGate.downstreamBlocked.esign}
                  vkycStatus={vkycStatus}
                  workflowPosition={workflowPosition}
                >
                  <EsignSection applicationId={applicationId} app={app} onRefetch={onRefetch} />
                </VkycDownstreamGate>
              </div>
            ) : null}
            {showDisburse ? (
              <div
                data-decision-section="disburse"
                className={disburseActive ? undefined : 'opacity-90'}
              >
                <h3 className="mb-1 text-sm font-semibold text-slate-900">Disbursement</h3>
                {!disburseActive ? (
                  <p className="mb-2 text-sm text-slate-600">
                    Disbursement becomes primary after eSign is completed for this journey.
                  </p>
                ) : (
                  <p className="mb-3 text-sm text-slate-600">Readiness checklist and LMS handover.</p>
                )}
                <VkycDownstreamGate
                  blocked={vkycGate.downstreamBlocked.disburse}
                  vkycStatus={vkycStatus}
                  workflowPosition={workflowPosition}
                >
                  <DisbursementSection applicationId={applicationId} app={app} onRefetch={onRefetch} />
                </VkycDownstreamGate>
              </div>
            ) : null}
          </div>
        </WorkspaceSection>
      )}

      {!showCam && !showEsign && !showDisburse ? (
        <p className="text-sm text-slate-600">
          Sanction and program setup for your role. Post-credit completion steps are not available for this access
          level.
        </p>
      ) : null}
    </div>
  )
}

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

function SummaryPanel({
  app,
  applicationId,
  role,
}: {
  app: ApplicationResponse
  applicationId: string
  role: string
}) {
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
  const collateralBi = (app.collateralInfo as Record<string, unknown> | null)?.[BORROWER_INTAKE_KEY] as
    | Record<string, unknown>
    | undefined
  const collateralCopy = requiresCollateral(app.loanProduct)
    ? collateralOverviewCopy({
        role,
        hasCollateralIntake: Boolean(collateralBi),
        canContinueIntake: staffCanContinueIntake(app, role),
      })
    : null

  return (
    <div>
      <h2 className="mb-3 bt-card-title">Application snapshot</h2>
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
          <div className="grid gap-2 text-sm text-[var(--bt-gray-800)] sm:grid-cols-2">
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
            Expand <strong>{partyLabels.submittedDetailsTitle}</strong> below for the full intake profile.
            {collateralCopy?.snapshotHint ? <span> {collateralCopy.snapshotHint}</span> : null}
          </p>
        </div>
      </AppSectionCard>
      {requiresCollateral(app.loanProduct) && collateralCopy ? (
        <AppSectionCard tone="success" className="mb-4 text-sm text-slate-800" title="Secured product — collateral" unstyledBody>
          <div className="p-4">
            {collateralBi ? (
              <p className="mt-1">
                Estimated value (declared):{' '}
                <span className="font-medium tabular-nums">
                  {collateralBi.estimatedValue != null
                    ? new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(
                        Number(collateralBi.estimatedValue),
                      )
                    : '—'}
                </span>{' '}
                INR — {collateralCopy.body}
              </p>
            ) : (
              <p className="mt-1 text-amber-800">
                {collateralCopy.body}{' '}
                {collateralCopy.suggestContinueIntake ? (
                  <Link to={`/applications/${applicationId}/intake`} className="font-medium text-bt-primary underline">
                    Continue intake
                  </Link>
                ) : null}
              </p>
            )}
          </div>
        </AppSectionCard>
      ) : null}
      <AppSectionCard tone="default" className="mb-4" unstyledBody>
        <div className="grid gap-4 p-4 sm:grid-cols-2">
          <Detail label={partyLabels.entityTypeDetail} value={borrowerTypeLabel(app.borrowerType)} />
          <Detail
            label={tenureMagnitudeLabel(app.lmsTenureUnit)}
            value={app.tenureMonths != null ? String(app.tenureMonths) : '—'}
          />
          {!isInvoiceDiscountingProduct(app.loanProduct) ? (
            <>
              <Detail label="Product code (LMS)" value={app.lmsProductCode?.trim() || '—'} />
              <Detail label="LMS tenure type" value={lmsTenureUnitLabel(app.lmsTenureUnit)} />
            </>
          ) : null}
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
              <Detail
                label="Credit decision"
                value={app.creditDecision ? formatAssessmentOutcome(app.creditDecision) : '—'}
              />
            </>
          ) : (
            <>
              <Detail label="Bureau score" value={app.bureauScore != null ? String(app.bureauScore) : '—'} />
              <Detail
                label="Credit decision"
                value={app.creditDecision ? formatAssessmentOutcome(app.creditDecision) : '—'}
              />
            </>
          )}
          <Detail label="eSign reference" value={app.esignTransactionId ?? '—'} />
          <Detail label="Created" value={formatInstant(app.createdAt)} />
          {hasManualOverride ? (
            <Detail label="Override" value={`Manual override (${manualOverrideCount || 'flagged'})`} />
          ) : null}
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
