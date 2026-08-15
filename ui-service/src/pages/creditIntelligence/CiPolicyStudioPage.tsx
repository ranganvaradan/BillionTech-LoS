import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  copyPolicyStudioDocument,
  createPolicyFromScratch,
  deleteDraftLifecyclePolicy,
  getPolicyStudioLanding,
  getPolicyStudioSession,
  getStagingPolicyStudio,
  resetDemoPolicy,
  resolvePolicyAmbiguity,
  retireLifecyclePolicy,
  reviewPolicyRule,
  saveLifecycleDraft,
  uploadPolicyStudioFile,
  type PolicyStudioLanding,
  type StagingPolicyStudio,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection, CiTechnicalDetails } from '@/components/creditIntelligence/CiSection'
import {
  CiCreditPoliciesLanding,
  EXAMPLE_DELETE_SUCCESS_MSG,
} from '@/pages/creditIntelligence/CiCreditPoliciesLanding'
import { POLICY_STUDIO_PRIMARY_TAB_IDS } from '@/lib/applicationWorkbench'
import { derivePolicyNextStep, progressStageLabels } from '@/lib/creditIntelligence/businessLexicon'
import {
  formatShellStatusLine,
  POLICY_STUDIO_DETAILS_SECTIONS,
  POLICY_STUDIO_WORKFLOW_TABS,
  underwritingRuleStats,
  type PolicyStudioDetailsSectionId,
} from '@/lib/ux/policyStudioShell'
import { CiPolicyAmbiguitiesTab } from '@/pages/creditIntelligence/CiPolicyAmbiguitiesTab'
import { CiPolicyApprovalsTab } from '@/pages/creditIntelligence/CiPolicyApprovalsTab'
import { CiPolicyKycTab } from '@/pages/creditIntelligence/CiPolicyKycTab'
import { CiPolicyRulesTab } from '@/pages/creditIntelligence/CiPolicyRulesTab'
import { PolicyParameterInventoryPanel } from '@/components/policy/PolicyParameterInventoryPanel'
import {
  resolveDataAndCalculations,
  resolveOtherPolicyContent,
  resolveUnderwritingRules,
} from '@/pages/creditIntelligence/policyRuleDisplayGroups'
import { CiPolicySimulationTab } from '@/pages/creditIntelligence/CiPolicySimulationTab'
import { CiPolicyDataReadinessTab } from '@/pages/creditIntelligence/CiPolicyDataReadinessTab'
import { CiPolicyLifecycleTab } from '@/pages/creditIntelligence/CiPolicyLifecycleTab'
import { CiPolicyScopeTab } from '@/pages/creditIntelligence/CiPolicyScopeTab'
import { CiPolicyScorecardTab } from '@/pages/creditIntelligence/CiPolicyScorecardTab'
import { CiPolicyTestsTab } from '@/pages/creditIntelligence/CiPolicyTestsTab'

type TabId =
  | 'overview'
  | 'kyc-eligibility'
  | 'structure'
  | 'ambiguities'
  | 'scope'
  | 'rules'
  | 'scorecard'
  | 'data-readiness'
  | 'tests'
  | 'simulation'
  | 'approvals'
  | 'lifecycle'
type Kind = 'banking' | 'bureau' | 'kyc'
type StudioView = 'landing' | 'session'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function chipClass(state: string): string {
  if (state === 'DONE' || state === 'CURRENT') {
    return state === 'CURRENT'
      ? 'bg-sky-600 text-white'
      : 'bg-emerald-100 text-emerald-900'
  }
  return 'bg-slate-100 text-slate-500'
}

const PRIMARY_TAB_IDS = POLICY_STUDIO_PRIMARY_TAB_IDS as readonly string[]

export function CiPolicyStudioPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [landing, setLanding] = useState<PolicyStudioLanding | null>(null)
  const [view, setView] = useState<StudioView>('landing')
  const [session, setSession] = useState<StagingPolicyStudio | null>(null)
  const [tab, setTab] = useState<TabId>('scope')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  /** Landing-row Open / Delete / Retire — scoped so other rows stay interactive. */
  const [rowBusyId, setRowBusyId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const formatLifecycleActionError = (e: unknown, fallback: string): string => {
    if (!(e instanceof ApiError)) return fallback
    const parts = [e.message.trim()]
    if (e.reason?.trim() && !parts[0].includes(e.reason.trim())) {
      parts.push(`(${e.reason.trim()})`)
    }
    const action =
      typeof e.context?.action === 'string'
        ? e.context.action.trim()
        : undefined
    // ApiError also exposes server fields via body when present
    const body = e.body && typeof e.body === 'object' && !Array.isArray(e.body)
      ? (e.body as Record<string, unknown>)
      : null
    const bodyAction = typeof body?.action === 'string' ? body.action.trim() : ''
    const hint = action || bodyAction
    if (hint && !parts.join(' ').includes(hint)) {
      parts.push(hint)
    }
    return parts.filter(Boolean).join(' — ')
  }
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [prospectDemoMode, setProspectDemoMode] = useState(() => {
    try {
      return localStorage.getItem('ci.prospectDemoMode') === '1'
    } catch {
      return false
    }
  })
  const [demoActor, setDemoActor] = useState('credit_manager')
  const [demoMsg, setDemoMsg] = useState<string | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [detailsSection, setDetailsSection] = useState<PolicyStudioDetailsSectionId>('overview')
  const [moreOpen, setMoreOpen] = useState(false)
  const [savedLabel, setSavedLabel] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [scopeDirty, setScopeDirty] = useState(false)
  const [rulesDirty, setRulesDirty] = useState(false)
  const moreRef = useRef<HTMLDivElement>(null)

  const selectWorkflowTab = (id: TabId) => {
    setDetailsOpen(false)
    setMoreOpen(false)
    setTab(id)
  }

  const openPolicyDetails = (section: PolicyStudioDetailsSectionId = 'overview') => {
    setDetailsSection(section)
    setDetailsOpen(true)
    setMoreOpen(false)
  }

  const goToSurface = (id: TabId) => {
    if ((PRIMARY_TAB_IDS as readonly string[]).includes(id)) {
      selectWorkflowTab(id)
      return
    }
    if (POLICY_STUDIO_DETAILS_SECTIONS.some((s) => s.id === id)) {
      openPolicyDetails(id as PolicyStudioDetailsSectionId)
      return
    }
    selectWorkflowTab('scope')
  }

  const toggleProspectDemoMode = () => {
    setProspectDemoMode((prev) => {
      const next = !prev
      try {
        localStorage.setItem('ci.prospectDemoMode', next ? '1' : '0')
      } catch {
        /* ignore */
      }
      return next
    })
  }

  const loadLanding = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setLanding(await getPolicyStudioLanding())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load Policy Studio')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadLanding()
  }, [loadLanding])

  /** Sidebar / workspace "Policies" must always return to the policy landing list. */
  const returnToPoliciesLanding = useCallback(() => {
    setView('landing')
    setSession(null)
    setError(null)
    setDemoMsg(null)
    setSavedLabel(null)
    setDetailsOpen(false)
    setDirty(false)
    setScopeDirty(false)
    setRulesDirty(false)
    void loadLanding()
  }, [loadLanding])

  useEffect(() => {
    const st = location.state as { openPoliciesLanding?: boolean } | null
    if (!st?.openPoliciesLanding) return
    returnToPoliciesLanding()
    navigate(location.pathname, { replace: true, state: {} })
  }, [location.state, location.pathname, navigate, returnToPoliciesLanding])

  useEffect(() => {
    if (!moreOpen) return
    const onDoc = (e: MouseEvent) => {
      if (moreRef.current && !moreRef.current.contains(e.target as Node)) setMoreOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [moreOpen])

  const enterSession = (data: StagingPolicyStudio, defaultTab: TabId = 'scope') => {
    setSession(data)
    setBusy(false)
    setTab(defaultTab)
    setView('session')
    setExpanded({})
    setError(null)
    setDirty(false)
    setScopeDirty(false)
    setRulesDirty(false)
    setDetailsOpen(false)
    setSavedLabel(null)
  }

  const openDemo = async (kind: Kind) => {
    setBusy(true)
    setError(null)
    try {
      const data = await getStagingPolicyStudio(kind)
      enterSession(data, 'scope')
      if (kind === 'kyc') {
        setDetailsSection('kyc-eligibility')
        setDetailsOpen(true)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not open demo policy')
    } finally {
      setBusy(false)
    }
  }

  const processFile = async (file: File | null | undefined) => {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      // POLICY-CREATION-1 — upload converges into the same draft workspace (no separate AI tour)
      const data = await uploadPolicyStudioFile(file)
      enterSession(data, 'scope')
      void loadLanding()
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.message
          : 'We could not process this upload. Please try a PDF, Word, or text file.',
      )
    } finally {
      setBusy(false)
    }
  }

  const createScratch = async (policyName: string, description: string) => {
    setBusy(true)
    setError(null)
    try {
      const data = await createPolicyFromScratch({ policyName, description })
      enterSession(data, 'scope')
      void loadLanding()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create policy draft')
    } finally {
      setBusy(false)
    }
  }

  const copyPolicy = async (documentId: string, policyName?: string) => {
    setBusy(true)
    setError(null)
    try {
      const data = await copyPolicyStudioDocument(documentId, policyName ? { policyName } : undefined)
      enterSession(data, 'scope')
      void loadLanding()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not copy policy')
    } finally {
      setBusy(false)
    }
  }

  const deleteDraft = async (documentId: string, opts?: { demo?: boolean }) => {
    setRowBusyId(documentId)
    setError(null)
    setDemoMsg(null)
    try {
      await deleteDraftLifecyclePolicy(documentId, {
        reason: opts?.demo
          ? 'Example session removed from Existing Policies'
          : 'Draft deleted from Existing Policies',
      })
      setDemoMsg(opts?.demo ? EXAMPLE_DELETE_SUCCESS_MSG : 'Draft policy deleted.')
      void loadLanding()
    } catch (e) {
      setError(
        formatLifecycleActionError(
          e,
          'Could not delete draft policy',
        ),
      )
    } finally {
      setRowBusyId(null)
    }
  }

  const retirePolicy = async (documentId: string, reason: string) => {
    setRowBusyId(documentId)
    setError(null)
    try {
      await retireLifecyclePolicy(documentId, { retirementReason: reason, reason })
      setDemoMsg('Policy version retired.')
      void loadLanding()
    } catch (e) {
      setError(formatLifecycleActionError(e, 'Could not retire policy'))
    } finally {
      setRowBusyId(null)
    }
  }

  const openExisting = async (documentId: string) => {
    setRowBusyId(documentId)
    setError(null)
    try {
      const data = await getPolicyStudioSession(documentId)
      enterSession(data, 'scope')
    } catch (e) {
      setError(formatLifecycleActionError(e, 'Could not open policy'))
    } finally {
      setRowBusyId(null)
    }
  }

  const documentId = String(asRecord(session?.policyHeader).documentId ?? '')

  const resetDemo = async () => {
    const kind = String(session?.kind ?? '') as Kind
    if (kind !== 'banking' && kind !== 'bureau') return
    setBusy(true)
    setError(null)
    try {
      const data = await resetDemoPolicy(kind)
      setSession(data)
      setTab('scope')
      setDetailsOpen(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reset demo policy')
    } finally {
      setBusy(false)
    }
  }

  const resolveAmbiguity = async (ambiguityId: string, body: Record<string, unknown>) => {
    if (!documentId) {
      setError('Missing document id for this session')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const data = await resolvePolicyAmbiguity(documentId, ambiguityId, body)
      setSession(data)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save resolution')
    } finally {
      setBusy(false)
    }
  }

  const reviewRule = async (ruleId: string, body: Record<string, unknown>) => {
    if (!documentId) {
      setError('Missing document id for this session')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const data = await reviewPolicyRule(documentId, ruleId, body as Parameters<typeof reviewPolicyRule>[2])
      setSession(data)
      setDirty(true)
      setRulesDirty(true)
      setSavedLabel(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not record rule review')
    } finally {
      setBusy(false)
    }
  }

  const saveDraft = async () => {
    if (!documentId) {
      setError('Missing document id for this session')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const data = await saveLifecycleDraft(documentId, {
        reasonForChange: 'Credit Manager draft save',
      })
      setSession(data)
      setDirty(false)
      setRulesDirty(false)
      setScopeDirty(false)
      setSavedLabel('Saved just now')
    } catch (e) {
      setSavedLabel(null)
      const msg = e instanceof ApiError ? e.message : 'Could not save draft'
      setError(
        msg.toLowerCase().includes('save failed')
          ? msg
          : `Save failed — the draft was not persisted. ${msg}`,
      )
    } finally {
      setBusy(false)
    }
  }

  const applyCatalogueSession = (data?: unknown) => {
    if (data && typeof data === 'object') {
      setSession(data as typeof session)
      setDirty(true)
      setRulesDirty(true)
      setSavedLabel(null)
    }
  }

  const applyAuthoringSession = (data: Record<string, unknown>) => {
    setSession(data as typeof session)
    setDirty(true)
    setRulesDirty(true)
    setSavedLabel(null)
  }

  const header = asRecord(session?.policyHeader)
  const counts = asRecord(session?.counts)
  const cards = asList(session?.summaryCards)
  const structure = asList(session?.structure)
  const pipeline = asRecord(session?.pipeline)
  const stages = asList(pipeline.stages)
  const readinessBanner = asRecord(session?.readinessBanner)
  const ambiguityCards = asList(session?.ambiguityCards)
  const allRuleCards = asList(session?.ruleCards)
  // POLICY-DATA-CALC-CONVERGENCE-1: session arrays (including empty []) are authoritative.
  // Empty otherPolicyContent must NOT fall back to classificationOnly rebuild — that duplicated
  // Data & calculations stubs into Other with "Replace with underwriting rule".
  const ruleCards = resolveUnderwritingRules(session as Record<string, unknown> | null | undefined, allRuleCards)
  const dataAndCalculations = resolveDataAndCalculations(
    session as Record<string, unknown> | null | undefined,
    allRuleCards,
  )
  const otherPolicyContent = resolveOtherPolicyContent(
    session as Record<string, unknown> | null | undefined,
    allRuleCards,
  )
  const ambiguityCategories = asList(session?.ambiguityCategories)
  const testsCount = Number(asRecord(session?.executable).testCaseCount ?? counts.tests ?? 0)
  const uwStats = underwritingRuleStats(ruleCards)
  const isDirty = dirty || scopeDirty || rulesDirty
  const contentTab: TabId = detailsOpen ? (detailsSection as TabId) : tab
  const shellStatus = formatShellStatusLine({
    lifecycleStatus: String(header.status ?? 'DRAFT'),
    ready: uwStats.ready,
    needsInput: uwStats.needsInput,
    dirty: isDirty,
    savedLabel,
  })

  const demos = useMemo(
    () => (Array.isArray(landing?.demoPolicies) ? landing!.demoPolicies! : []),
    [landing],
  )

  if (view === 'landing') {
    return (
      <CiCreditPoliciesLanding
        landing={landing as Record<string, unknown> | null}
        loading={loading}
        busy={busy}
        rowBusyId={rowBusyId}
        error={error}
        successMsg={demoMsg}
        demos={demos}
        onCreateScratch={(n, d) => void createScratch(n, d)}
        onUploadFile={(f) => void processFile(f)}
        onCopy={(id, n) => void copyPolicy(id, n)}
        onOpen={(id) => void openExisting(id)}
        onOpenDemo={(kind) => void openDemo(kind)}
        onDeleteDraft={(id, _n, _v, opts) => void deleteDraft(id, opts)}
        onRetire={(id, _n, _v, reason) => void retirePolicy(id, reason)}
      />
    )
  }

  return (
    <div data-testid="policy-studio-session-shell">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <button
            type="button"
            className="mb-1 text-xs text-slate-500 hover:text-slate-800"
            data-testid="policies-breadcrumb"
            onClick={() => returnToPoliciesLanding()}
          >
            ← Policies
          </button>
          <h1 className="truncate text-xl font-semibold text-slate-900">
            {String(header.policyName ?? 'Policy')}
          </h1>
          <p className="mt-0.5 text-sm text-slate-600" data-testid="policy-shell-status">
            {shellStatus}
            {session?.demo ? (
              <span className="ml-2 text-xs font-medium text-amber-800">· Demo sample</span>
            ) : null}
          </p>
          {asRecord(session).copiedFromLabel ? (
            <p className="mt-0.5 text-xs text-slate-500">{String(asRecord(session).copiedFromLabel)}</p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm"
            disabled={busy || !documentId}
            onClick={() => void saveDraft()}
            data-testid="save-draft"
          >
            Save Draft
          </button>
          {tab === 'simulation' && !detailsOpen ? (
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              onClick={() => selectWorkflowTab('simulation')}
            >
              Run Test
            </button>
          ) : null}
          {tab === 'lifecycle' && !detailsOpen ? (
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              onClick={() => selectWorkflowTab('lifecycle')}
            >
              Activation Check
            </button>
          ) : null}
          <div className="relative" ref={moreRef}>
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              aria-expanded={moreOpen}
              onClick={() => setMoreOpen((v) => !v)}
              data-testid="policy-more-menu"
            >
              More ▾
            </button>
            {moreOpen ? (
              <div
                className="absolute right-0 z-50 mt-1 w-56 rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
                data-testid="policy-more-menu-dropdown"
                role="menu"
              >
                <button
                  type="button"
                  className="block w-full px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50"
                  onClick={() => openPolicyDetails('overview')}
                >
                  Policy details
                </button>
                {(PRIMARY_TAB_IDS as readonly string[]).includes(tab) && tab !== 'lifecycle' ? (
                  <button
                    type="button"
                    className="block w-full px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50"
                    onClick={() => {
                      selectWorkflowTab('lifecycle')
                    }}
                  >
                    Activation Check
                  </button>
                ) : null}
                {(PRIMARY_TAB_IDS as readonly string[]).includes(tab) && tab !== 'simulation' ? (
                  <button
                    type="button"
                    className="block w-full px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50"
                    onClick={() => selectWorkflowTab('simulation')}
                  >
                    Test Policy
                  </button>
                ) : null}
                <div className="my-1 border-t border-slate-100" />
                <p className="px-3 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400">
                  Development / demo
                </p>
                <button
                  type="button"
                  className="block w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                  onClick={() => {
                    toggleProspectDemoMode()
                    setMoreOpen(false)
                  }}
                >
                  {prospectDemoMode ? 'Demo view ON' : 'Demo view'}
                </button>
                {session?.demo || session?.canResetDemo ? (
                  <button
                    type="button"
                    className="block w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                    disabled={busy}
                    onClick={() => {
                      setMoreOpen(false)
                      void resetDemo()
                    }}
                  >
                    Reset Demo Policy
                  </button>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>
      </div>

      {error ? (
        <p
          className="mb-3 rounded border border-rose-300 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-900"
          role="alert"
          data-testid="policy-studio-save-error"
          aria-live="assertive"
        >
          {error}
        </p>
      ) : null}
      {demoMsg ? (
        <p className="mb-3 rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          {demoMsg}
        </p>
      ) : null}

      <PolicyParameterInventoryPanel documentId={documentId || null} />

      <nav
        className="mb-3 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-2"
        data-testid="policy-primary-tabs"
        aria-label="Policy workflow"
      >
        {POLICY_STUDIO_WORKFLOW_TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => selectWorkflowTab(t.id as TabId)}
            className={`rounded-full px-3 py-1.5 text-sm font-medium ${
              !detailsOpen && tab === t.id
                ? 'bg-slate-900 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
            data-testid={`primary-tab-${t.id}`}
          >
            {t.label}
          </button>
        ))}
        <button
          type="button"
          onClick={() => openPolicyDetails(detailsSection)}
          className={`ml-auto rounded-full px-3 py-1.5 text-sm font-medium ${
            detailsOpen
              ? 'bg-slate-800 text-white'
              : 'bg-slate-50 text-slate-600 ring-1 ring-slate-200 hover:bg-slate-100'
          }`}
          data-testid="policy-details-entry"
        >
          Policy details
        </button>
      </nav>

      {detailsOpen ? (
        <div
          className="mb-3 flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2"
          data-testid="policy-details-sections"
        >
          {POLICY_STUDIO_DETAILS_SECTIONS.map((s) => (
            <button
              key={s.id}
              type="button"
              onClick={() => setDetailsSection(s.id)}
              className={`rounded px-2 py-1 text-xs font-medium ${
                detailsSection === s.id
                  ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      ) : null}

      {busy ? <p className="text-sm text-slate-600">Working on your policy…</p> : null}

      {contentTab === 'overview' && session ? (
        <div className="space-y-4">
          {(() => {
            const implSum = asRecord(asRecord(session.implementability).summary ?? session.implementabilitySummary)
            const next = derivePolicyNextStep({
              openAmbiguities: Number(readinessBanner.ambiguities ?? counts.openAmbiguities ?? 0),
              rulesNeedReview: Number(readinessBanner.rulesNeedReview ?? counts.rulesNeedReview ?? 0),
              testsGenerated: Number(readinessBanner.testsGenerated ?? counts.tests ?? testsCount),
              dataReadinessBlocked: Boolean(implSum.draftBlockedByCriticalDataGap),
              dataReadinessNeedsAttention:
                Number(implSum.needsClarification ?? 0) +
                  Number(implSum.missingData ?? 0) +
                  Number(implSum.blocked ?? 0) >
                0,
            })
            return (
              <CiExecutiveSummary
                title="What should I do next?"
                nextAction={
                  <button
                    type="button"
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    onClick={() => goToSurface(next.tabHint as TabId)}
                  >
                    {next.title}
                  </button>
                }
              >
                <p>{next.detail}</p>
              </CiExecutiveSummary>
            )
          })()}

          <CiSection title="Progress">
            <ol className="flex flex-wrap gap-2">
              {progressStageLabels().map((s, i) => {
                const pct = Number(readinessBanner.policyReadinessPercent ?? pipeline.progressPercent ?? 0)
                const done = pct >= (i + 1) * 18
                const current = !done && pct >= i * 18
                return (
                  <li
                    key={s.key}
                    className={`rounded-full px-3 py-1 text-xs font-semibold ${
                      done
                        ? 'bg-emerald-100 text-emerald-900'
                        : current
                          ? 'bg-sky-600 text-white'
                          : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    {s.label}
                  </li>
                )
              })}
            </ol>
          </CiSection>

          <CiSection title="Policy summary">
            <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-5">
              <div>
                <dt className="text-xs text-slate-500">Policy name</dt>
                <dd className="font-semibold">{String(header.policyName ?? '—')}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">File name</dt>
                <dd className="font-semibold">{String(header.fileName ?? session.fileName ?? '—')}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Version</dt>
                <dd className="font-semibold">{String(header.version ?? '1')}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Upload date</dt>
                <dd className="font-semibold">
                  {header.uploadDate ? new Date(String(header.uploadDate)).toLocaleString() : '—'}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Status</dt>
                <dd>
                  <span className="inline-flex rounded-full bg-sky-100 px-2 py-0.5 text-xs font-semibold text-sky-900">
                    {String(header.status ?? '—')}
                  </span>
                </dd>
              </div>
            </dl>
          </CiSection>

          <CiSection title="AI understanding progress">
            <div className="mb-3 text-sm text-slate-700">
              Current stage: <strong>{String(pipeline.currentStage ?? 'Review')}</strong>
            </div>
            <ol className="flex flex-wrap gap-2">
              {stages.map((s, i) => {
                const row = asRecord(s)
                return (
                  <li
                    key={i}
                    className={`rounded-full px-3 py-1 text-xs font-semibold ${chipClass(String(row.state ?? ''))}`}
                  >
                    {String(row.label ?? row.key ?? '')}
                  </li>
                )
              })}
            </ol>
            <p className="mt-3 text-xs text-slate-600">
              Uploaded → Understood → Mapped → Rule review → Test review → Draft ready
            </p>
          </CiSection>

          {typeof asRecord(session).analystMessage === 'string' ? (
            <CiSection title="AI Policy Analyst">
              <p className="text-sm text-slate-800">{String(asRecord(session).analystMessage)}</p>
              <p className="mt-2 text-xs text-slate-500">
                Counts come from this document&apos;s interpretation only. KYC authoring is draft/shadow — not
                production authority.
              </p>
            </CiSection>
          ) : null}

          <CiSection title="What we found" description="Only categories detected in this policy">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {cards.map((c, i) => {
                const row = asRecord(c)
                const label = String(row.label ?? '')
                return (
                  <button
                    key={i}
                    type="button"
                    className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-left hover:border-sky-300 hover:bg-sky-50"
                    onClick={() => {
                      if (label.includes('KYC')) goToSurface('kyc-eligibility')
                      else if (label.includes('Ambigu')) goToSurface('ambiguities')
                      else if (label.includes('Credit') || label.includes('Rule') || label.includes('Banking') || label.includes('Bureau'))
                        goToSurface('rules')
                    }}
                  >
                    <div className="text-2xl font-semibold text-slate-900">{String(row.count ?? 0)}</div>
                    <div className="mt-1 text-sm font-medium text-slate-700">{label}</div>
                  </button>
                )
              })}
              {cards.length === 0 ? <p className="text-sm text-slate-500">No categories detected yet.</p> : null}
            </div>
          </CiSection>

          <CiSection title="Coverage">
            <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-5">
              <div>
                <dt className="text-xs text-slate-500">Total clauses</dt>
                <dd className="text-lg font-semibold">{String(counts.totalClauses ?? 0)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">AI understood</dt>
                <dd className="text-lg font-semibold">{String(counts.interpretedClauses ?? 0)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Mapped</dt>
                <dd className="text-lg font-semibold">{String(counts.mappedClauses ?? 0)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Review required</dt>
                <dd className="text-lg font-semibold text-amber-800">{String(counts.reviewRequired ?? 0)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Ready clauses</dt>
                <dd className="text-lg font-semibold text-emerald-800">{String(counts.readyClauses ?? 0)}</dd>
              </div>
            </dl>
          </CiSection>

          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
            {String(
              session.humanReviewBanner ??
                'AI understanding requires human review. Nothing is published automatically.',
            )}
          </div>
        </div>
      ) : null}

      {contentTab === 'structure' && session ? (
        <CiSection title="Policy structure" description="Expand a section to review clause titles">
          <ul className="space-y-2">
            {structure.map((s, i) => {
              const row = asRecord(s)
              const key = String(row.name ?? i)
              const open = !!expanded[key]
              const items = asList(row.items)
              return (
                <li key={key} className="rounded-lg border border-slate-200 bg-white">
                  <button
                    type="button"
                    className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
                    onClick={() => setExpanded((prev) => ({ ...prev, [key]: !prev[key] }))}
                  >
                    <div>
                      <div className="font-semibold text-slate-900">{String(row.name)}</div>
                      <div className="mt-1 text-xs text-slate-500">
                        {String(row.clauseCount ?? 0)} clauses · {String(row.rules ?? 0)} rules ·{' '}
                        {String(row.ambiguities ?? 0)} ambiguous terms · {String(row.missingMetrics ?? 0)} business
                        measures
                      </div>
                    </div>
                    <span className="text-slate-400">{open ? '▾' : '▸'}</span>
                  </button>
                  {open ? (
                    <ol className="space-y-1 border-t border-slate-100 px-4 py-3 text-sm">
                      {items.map((it, j) => {
                        const child = asRecord(it)
                        return (
                          <li key={j} className="flex gap-2 text-slate-800">
                            <span className="w-6 shrink-0 text-slate-400">{String(child.index ?? j + 1)}.</span>
                            <span>
                              <span className="font-medium">{String(child.title ?? '—')}</span>
                              {child.clauseType ? (
                                <span className="ml-2 text-xs capitalize text-slate-500">
                                  {String(child.clauseType)}
                                </span>
                              ) : null}
                            </span>
                          </li>
                        )
                      })}
                    </ol>
                  ) : null}
                </li>
              )
            })}
          </ul>
        </CiSection>
      ) : null}

      {contentTab === 'kyc-eligibility' && session ? (
        <CiPolicyKycTab
          cards={ruleCards}
          domainBreakdown={asRecord(asRecord(session).domainBreakdown)}
          analystMessage={
            typeof asRecord(session).analystMessage === 'string'
              ? String(asRecord(session).analystMessage)
              : null
          }
        />
      ) : null}

      {contentTab === 'ambiguities' && session ? (
        <CiPolicyAmbiguitiesTab
          cards={ambiguityCards}
          categories={ambiguityCategories}
          busy={busy}
          onResolve={resolveAmbiguity}
          prospectDemoMode={prospectDemoMode}
        />
      ) : null}

      {contentTab === 'scope' && session ? (
        documentId ? (
          <CiPolicyScopeTab
            documentId={documentId}
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            session={session}
            onScopeDirty={(d) => {
              setScopeDirty(d)
              if (d) setSavedLabel(null)
            }}
            onSessionRefresh={(next) => {
              if (next) setSession(next)
              setScopeDirty(false)
            }}
          />
        ) : (
          <CiSection title="Scope">
            <p className="text-sm text-slate-600">Open a policy session to define where this policy applies.</p>
          </CiSection>
        )
      ) : null}

      {contentTab === 'rules' && session ? (
        <CiPolicyRulesTab
          cards={ruleCards}
          dataAndCalculations={dataAndCalculations}
          otherPolicyContent={otherPolicyContent}
          policyDataResolutions={asRecord(asRecord(session).policyDataResolutions)}
          busy={busy}
          setBusy={setBusy}
          onError={setError}
          onReview={reviewRule}
          onViewTests={() => selectWorkflowTab('simulation')}
          onCatalogueChanged={applyCatalogueSession}
          onSession={applyAuthoringSession}
          documentId={documentId}
          ingestionBinding={asRecord(asRecord(session).ingestionBinding)}
          ambiguities={asList(asRecord(session).ambiguities).length
            ? asList(asRecord(session).ambiguities)
            : asList(asRecord(session).ambiguityCards)}
          prospectDemoMode={prospectDemoMode}
          compactShell
        />
      ) : null}

      {contentTab === 'scorecard' && session ? (
        documentId ? (
          <CiPolicyScorecardTab
            documentId={documentId}
            policyName={String(header.policyName ?? '')}
            loanProduct={String(header.productScope ?? header.loanProduct ?? '')}
            borrowerType={String(header.borrowerType ?? '')}
            linkedScorecardId={
              header.scorecardId != null
                ? String(header.scorecardId)
                : asRecord(session).scorecardId != null
                  ? String(asRecord(session).scorecardId)
                  : null
            }
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            onLinked={() => {
              void getPolicyStudioSession(documentId).then(setSession).catch(() => undefined)
            }}
          />
        ) : (
          <CiSection title="Scorecard">
            <p className="text-sm text-slate-600">Open a policy session to configure Scorecard.</p>
          </CiSection>
        )
      ) : null}

      {contentTab === 'data-readiness' && session ? (
        <CiPolicyDataReadinessTab
          session={session}
          prospectDemoMode={prospectDemoMode}
          onGoApprovals={() => openPolicyDetails('approvals')}
          documentId={documentId || undefined}
          busy={busy}
          setBusy={setBusy}
          onError={setError}
          onSessionUpdate={(next) => setSession(next as StagingPolicyStudio)}
        />
      ) : null}

      {contentTab === 'tests' && session ? (
        documentId ? (
          <CiPolicyTestsTab
            documentId={documentId}
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            prospectDemoMode={prospectDemoMode}
          />
        ) : (
          <CiSection title="Tests">
            <p className="text-sm text-slate-600">Generated tests: {testsCount}</p>
          </CiSection>
        )
      ) : null}

      {contentTab === 'simulation' && session ? (
        documentId ? (
          <CiPolicySimulationTab
            documentId={documentId}
            policyName={String(header.policyName ?? '')}
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            prospectDemoMode={prospectDemoMode}
            onResolveParameter={() => selectWorkflowTab('rules')}
          />
        ) : (
          <CiSection title="Simulation">
            <p className="text-sm text-slate-600">Open a policy session before running simulation.</p>
          </CiSection>
        )
      ) : null}

      {contentTab === 'approvals' && session ? (
        documentId ? (
          <CiPolicyApprovalsTab
            documentId={documentId}
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            demoActor={demoActor}
            setDemoActor={setDemoActor}
            prospectDemoMode={prospectDemoMode}
          />
        ) : (
          <CiSection title="Approvals">
            <p className="text-sm text-slate-600">Open a policy session first.</p>
          </CiSection>
        )
      ) : null}

      {contentTab === 'lifecycle' && session ? (
        documentId ? (
          <CiPolicyLifecycleTab
            documentId={documentId}
            busy={busy}
            setBusy={setBusy}
            onError={setError}
            session={session}
            onNavigateTab={(nextTab) => selectWorkflowTab(nextTab as TabId)}
            onOpenScorecardTab={() => selectWorkflowTab('scorecard')}
            onSessionRefresh={(next) => {
              if (next && typeof next === 'object' && 'policyHeader' in next) {
                setSession(next)
                const nextId = String(asRecord(next.policyHeader).documentId ?? '')
                if (nextId && nextId !== documentId) {
                  setTab('lifecycle')
                }
              } else {
                void getPolicyStudioSession(documentId).then(setSession).catch(() => undefined)
              }
            }}
          />
        ) : (
          <CiSection title="Policy Settings">
            <p className="text-sm text-slate-600">Open a policy session first.</p>
          </CiSection>
        )
      ) : null}

      {session && detailsOpen && !prospectDemoMode ? (
        <div className="mt-6" data-testid="policy-advanced-technical">
          <CiTechnicalDetails title="Advanced / technical">
            {JSON.stringify(
              {
                documentId: header.documentId,
                readiness: asRecord(session.executable).readiness,
                completeness: asRecord(session.executable).completeness,
              },
              null,
              2,
            )}
          </CiTechnicalDetails>
        </div>
      ) : null}
    </div>
  )
}
