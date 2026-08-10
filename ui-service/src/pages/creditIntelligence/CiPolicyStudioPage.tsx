import { useCallback, useEffect, useMemo, useRef, useState, type DragEvent, type FormEvent } from 'react'
import {
  addPlainEnglishPolicyRule,
  getPolicyStudioLanding,
  getPolicyStudioSession,
  getStagingPolicyStudio,
  resetDemoPolicy,
  resolvePolicyAmbiguity,
  reviewPolicyRule,
  runDemoBlockedPath,
  runDemoHappyPath,
  saveLifecycleDraft,
  uploadPolicyStudioFile,
  type PolicyStudioLanding,
  type StagingPolicyStudio,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { useAuth } from '@/auth/useAuth'
import { PageHeader } from '@/components/PageHeader'
import { PoliciesWorkspaceNav } from '@/components/workspace/PoliciesWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import { CiExecutiveSummary, CiSection, CiTechnicalDetails } from '@/components/creditIntelligence/CiSection'
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
import { CiPolicyAnalystExperience } from '@/pages/creditIntelligence/CiPolicyAnalystExperience'
import { CiPolicyApprovalsTab } from '@/pages/creditIntelligence/CiPolicyApprovalsTab'
import { CiPolicyKycTab } from '@/pages/creditIntelligence/CiPolicyKycTab'
import { CiPolicyRulesTab } from '@/pages/creditIntelligence/CiPolicyRulesTab'
import { CiPolicySimulationTab } from '@/pages/creditIntelligence/CiPolicySimulationTab'
import { CiPolicyDataReadinessTab } from '@/pages/creditIntelligence/CiPolicyDataReadinessTab'
import { CiPolicyLifecycleTab } from '@/pages/creditIntelligence/CiPolicyLifecycleTab'
import { CiPolicyScopeTab } from '@/pages/creditIntelligence/CiPolicyScopeTab'
import { CiPolicyTestsTab } from '@/pages/creditIntelligence/CiPolicyTestsTab'

type TabId =
  | 'overview'
  | 'kyc-eligibility'
  | 'structure'
  | 'ambiguities'
  | 'scope'
  | 'rules'
  | 'data-readiness'
  | 'tests'
  | 'simulation'
  | 'approvals'
  | 'lifecycle'
type Kind = 'banking' | 'bureau' | 'kyc'
type StudioView = 'landing' | 'analyzing' | 'session'

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
  const { user } = useAuth()
  const [landing, setLanding] = useState<PolicyStudioLanding | null>(null)
  const [view, setView] = useState<StudioView>('landing')
  const [session, setSession] = useState<StagingPolicyStudio | null>(null)
  const [pendingSession, setPendingSession] = useState<StagingPolicyStudio | null>(null)
  const [analysisWaiting, setAnalysisWaiting] = useState(false)
  const [analysisStartedAt, setAnalysisStartedAt] = useState(0)
  const [analysisFileLabel, setAnalysisFileLabel] = useState<string | null>(null)
  const [tab, setTab] = useState<TabId>('scope')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
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
  const fileRef = useRef<HTMLInputElement>(null)
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

  useEffect(() => {
    if (!moreOpen) return
    const onDoc = (e: MouseEvent) => {
      if (moreRef.current && !moreRef.current.contains(e.target as Node)) setMoreOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [moreOpen])

  const beginAnalysis = (fileLabel: string) => {
    setError(null)
    setPendingSession(null)
    setAnalysisFileLabel(fileLabel)
    setAnalysisStartedAt(Date.now())
    setAnalysisWaiting(true)
    setView('analyzing')
    setBusy(true)
  }

  const finishAnalysis = (data: StagingPolicyStudio) => {
    setPendingSession(data)
    setAnalysisWaiting(false)
    setBusy(false)
  }

  const failAnalysis = (message: string) => {
    setAnalysisWaiting(false)
    setBusy(false)
    setError(message)
  }

  const openDemo = async (kind: Kind) => {
    beginAnalysis(
      kind === 'kyc'
        ? 'KYC & Eligibility (validation sample)'
        : kind === 'banking'
          ? 'Banking policy (demo)'
          : 'Bureau policy (demo)',
    )
    try {
      const data = await getStagingPolicyStudio(kind)
      finishAnalysis(data)
      if (kind === 'kyc') {
        setDetailsSection('kyc-eligibility')
        setDetailsOpen(true)
        setTab('scope')
      }
    } catch (e) {
      failAnalysis(e instanceof ApiError ? e.message : 'Could not open demo policy')
    }
  }

  const processFile = async (file: File | null | undefined) => {
    if (!file) return
    beginAnalysis(file.name)
    try {
      const data = await uploadPolicyStudioFile(file)
      finishAnalysis(data)
    } catch (e) {
      failAnalysis(
        e instanceof ApiError
          ? e.message
          : 'We could not process this upload. Please try a PDF, Word, or text file.',
      )
    } finally {
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const enterSessionFromAnalysis = () => {
    if (!pendingSession) return
    setSession(pendingSession)
    setPendingSession(null)
    setTab('scope')
    setView('session')
    setExpanded({})
    setError(null)
    setDirty(false)
    setScopeDirty(false)
    setRulesDirty(false)
  }

  const cancelAnalysis = () => {
    setView('landing')
    setAnalysisWaiting(false)
    setPendingSession(null)
    setBusy(false)
    setError(null)
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const f = e.dataTransfer.files?.[0]
    void processFile(f)
  }

  const onSubmitUpload = (e: FormEvent) => {
    e.preventDefault()
    void processFile(fileRef.current?.files?.[0])
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
      setError(e instanceof ApiError ? e.message : 'Could not save draft')
    } finally {
      setBusy(false)
    }
  }

  const addPlainEnglishRule = async (group: string, text: string) => {
    if (!documentId) {
      setError('Missing document id for this session')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const data = await addPlainEnglishPolicyRule(documentId, { text, group })
      setSession(data)
      setDirty(true)
      setRulesDirty(true)
      setSavedLabel(null)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not add rule')
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

  const header = asRecord(session?.policyHeader)
  const counts = asRecord(session?.counts)
  const cards = asList(session?.summaryCards)
  const structure = asList(session?.structure)
  const pipeline = asRecord(session?.pipeline)
  const stages = asList(pipeline.stages)
  const readinessBanner = asRecord(session?.readinessBanner)
  const ambiguityCards = asList(session?.ambiguityCards)
  const allRuleCards = asList(session?.ruleCards)
  const underwritingFromSession = asList(session?.underwritingRules)
  const ruleCards = underwritingFromSession.length > 0 ? underwritingFromSession : allRuleCards.filter((c) => {
    const s = String(asRecord(c).status ?? '')
    return s !== 'Data requirement' && s !== 'Metric adjustment' && s !== 'Non-underwriting' && !asRecord(c).compoundChild
  })
  const dataAndCalculations = (() => {
    const fromSession = asList(session?.dataAndCalculations)
    if (fromSession.length > 0) return fromSession
    return allRuleCards.filter((c) => {
      const s = String(asRecord(c).status ?? '')
      return s === 'Data requirement' || s === 'Metric adjustment' || s === 'Non-underwriting'
    })
  })()
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

  const capabilities = useMemo(
    () => (Array.isArray(landing?.capabilities) ? landing!.capabilities! : []),
    [landing],
  )
  const demos = useMemo(
    () => (Array.isArray(landing?.demoPolicies) ? landing!.demoPolicies! : []),
    [landing],
  )

  if (view === 'analyzing') {
    return (
      <CiPolicyAnalystExperience
        userName={user?.name}
        fileLabel={analysisFileLabel}
        waiting={analysisWaiting}
        waitStartedAt={analysisStartedAt}
        session={pendingSession}
        error={error}
        onReviewPolicy={enterSessionFromAnalysis}
        onCancel={cancelAnalysis}
      />
    )
  }

  if (view === 'landing') {
    return (
      <div>
        <PageHeader
          title={String(landing?.title ?? 'Policy Studio')}
          description={String(
            landing?.subtitle ??
              'Upload your credit policy. BillionTech will identify rules, definitions, exceptions and ambiguous terms, then prepare a draft policy for Credit Manager review. Draft / approved / scheduled only — not live LOS production configuration.',
          )}
        />
        <PoliciesWorkspaceNav />
        <CiFixtureBanner />
        {loading ? (
          <p className="text-sm text-slate-600">Preparing Policy Studio…</p>
        ) : null}
        {error ? (
          <p className="mb-4 rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">
            {error}
          </p>
        ) : null}

        <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-900">Upload Credit Policy</h2>
            <p className="mt-1 text-sm text-slate-600">
              Supported: {(landing?.supportedFormats as string[] | undefined)?.join(', ') ?? 'PDF, DOCX, TXT'}
            </p>
            <p className="mt-2 text-sm text-slate-500">
              After upload, the AI Policy Analyst will walk through structure, rules, definitions and ambiguities
              before you enter Policy Studio.
            </p>
            <form onSubmit={onSubmitUpload} className="mt-4">
              <div
                onDragOver={(e) => {
                  e.preventDefault()
                  setDragOver(true)
                }}
                onDragLeave={() => setDragOver(false)}
                onDrop={onDrop}
                className={`rounded-xl border-2 border-dashed px-6 py-10 text-center transition ${
                  dragOver ? 'border-sky-500 bg-sky-50' : 'border-slate-300 bg-slate-50'
                }`}
              >
                <p className="text-sm font-medium text-slate-800">Drag & drop your policy file here</p>
                <p className="mt-1 text-xs text-slate-500">or choose a file from your computer</p>
                <input
                  ref={fileRef}
                  type="file"
                  accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                  className="mt-4 block w-full text-sm text-slate-600"
                />
              </div>
              <button type="submit" className="bt-btn bt-btn-primary mt-4" disabled={busy}>
                {busy ? 'Processing…' : 'Upload Policy'}
              </button>
            </form>

            <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
              <strong>AI understanding requires human review.</strong>
              <div className="mt-1 font-normal text-amber-900">Nothing is published automatically.</div>
            </div>
          </section>

          <aside className="space-y-4">
            <CiExecutiveSummary title="What should I do next?">
              <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-700">
                <li>Upload a credit policy, or open a demo policy</li>
                <li>Review ambiguous terms and proposed business rules</li>
                <li>Run simulation, then send for approval</li>
              </ol>
            </CiExecutiveSummary>
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">BillionTech will</h3>
              <ul className="mt-3 space-y-2 text-sm text-slate-800">
                {capabilities.map((c) => (
                  <li key={String(c)} className="flex gap-2">
                    <span className="text-emerald-600" aria-hidden>
                      ✓
                    </span>
                    <span>{String(c)}</span>
                  </li>
                ))}
              </ul>
            </section>

            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Demo policies</h3>
              <p className="mt-1 text-xs text-slate-500">Open a sample to explore Policy Understanding.</p>
              <div className="mt-3 space-y-2">
                {demos.map((d) => {
                  const row = asRecord(d)
                  const kind = String(row.kind ?? '') as Kind
                  return (
                    <button
                      key={kind}
                      type="button"
                      disabled={busy || (kind !== 'banking' && kind !== 'bureau' && kind !== 'kyc')}
                      onClick={() => void openDemo(kind)}
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-left hover:border-sky-300 hover:bg-sky-50"
                    >
                      <div className="font-semibold text-slate-900">{String(row.name ?? kind)}</div>
                      <div className="mt-1 text-[11px] font-semibold uppercase tracking-wide text-amber-800">
                        {String(row.label ?? 'DEMO POLICY — CUSTOMER-SUPPLIED SAMPLE')}
                      </div>
                      <div className="mt-1 text-xs text-slate-600">{String(row.description ?? '')}</div>
                    </button>
                  )
                })}
              </div>
              {!prospectDemoMode ? (
              <div className="mt-4 space-y-2 border-t border-slate-100 pt-3">
                <p className="text-xs font-semibold uppercase text-slate-500">Demo walkthrough scripts</p>
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm w-full"
                  disabled={busy}
                  onClick={() =>
                    void (async () => {
                      setBusy(true)
                      setError(null)
                      setDemoMsg(null)
                      try {
                        const data = await runDemoHappyPath()
                        const doc = String(data.documentId ?? '')
                        if (doc) {
                          setSession(await getPolicyStudioSession(doc))
                          setView('session')
                          setTab('scope')
                          setDetailsSection('approvals')
                          setDetailsOpen(true)
                        }
                        setDemoMsg(
                          `Happy path complete — Draft ${String(asRecord(data.draftSummary).versionLabel ?? 'ready')}. ${String(data.demoResolutionBanner ?? '')}`,
                        )
                      } catch (e) {
                        setError(e instanceof ApiError ? e.message : 'Happy path failed')
                      } finally {
                        setBusy(false)
                      }
                    })()
                  }
                >
                  Run Banking happy path
                </button>
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm w-full"
                  disabled={busy}
                  onClick={() =>
                    void (async () => {
                      setBusy(true)
                      setError(null)
                      setDemoMsg(null)
                      try {
                        const data = await runDemoBlockedPath()
                        const doc = String(data.documentId ?? '')
                        if (doc) {
                          setSession(await getPolicyStudioSession(doc))
                          setView('session')
                          setTab('scope')
                          setDetailsSection('approvals')
                          setDetailsOpen(true)
                        }
                        setDemoMsg(String(data.message ?? 'Blocked path — draft cannot be built.'))
                      } catch (e) {
                        setError(e instanceof ApiError ? e.message : 'Blocked path failed')
                      } finally {
                        setBusy(false)
                      }
                    })()
                  }
                >
                  Run blocked-path demo
                </button>
                {demoMsg ? <p className="text-xs text-slate-600">{demoMsg}</p> : null}
              </div>
              ) : null}
            </section>
          </aside>
        </div>
      </div>
    )
  }

  return (
    <div data-testid="policy-studio-session-shell">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <button
            type="button"
            className="mb-1 text-xs text-slate-500 hover:text-slate-800"
            onClick={() => {
              setView('landing')
              setSession(null)
              setError(null)
              setDemoMsg(null)
              setSavedLabel(null)
              setDetailsOpen(false)
            }}
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
              <div className="absolute right-0 z-20 mt-1 w-56 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
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
        <p className="mb-3 rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">
          {error}
        </p>
      ) : null}
      {demoMsg ? (
        <p className="mb-3 rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          {demoMsg}
        </p>
      ) : null}

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
          busy={busy}
          onReview={reviewRule}
          onViewTests={() => selectWorkflowTab('simulation')}
          onAddPlainEnglishRule={addPlainEnglishRule}
          onCatalogueChanged={applyCatalogueSession}
          documentId={documentId}
          ingestionBinding={asRecord(asRecord(session).ingestionBinding)}
          prospectDemoMode={prospectDemoMode}
          compactShell
        />
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
            onSessionRefresh={(next) => {
              if (next && typeof next === 'object' && 'policyHeader' in next) {
                setSession(next)
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
