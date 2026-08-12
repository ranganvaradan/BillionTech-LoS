import { http } from './http'

const BASE = 'internal/credit-intelligence/staging-demo'

export type StagingCaseSummary = {
  caseCode: string
  enumCode?: string
  title: string
  origin: string
  description?: string
  fixtureBanner?: string
  productionActive?: boolean
  authoritative?: boolean
  allowCanonicalAuthority?: boolean
}

export type StagingWorkspace = Record<string, unknown> & {
  caseCode: string
  title?: string
  fixtureBanner?: string
  creditEvidenceView?: Record<string, unknown>
  creditDecisionView?: Record<string, unknown>
  aiUnderwriterView?: Record<string, unknown>
  legacyVsCanonical?: Record<string, unknown>
  decisionExplanation?: Record<string, unknown>
  policyResult?: Record<string, unknown>
  recommendation?: Record<string, unknown>
  replay?: { originalHash?: string; replayHash?: string; match?: boolean }
  cutover?: Record<string, unknown>
  investigationQuestions?: string[]
}

export type StagingPolicyStudio = Record<string, unknown> & {
  kind?: string
  demo?: boolean
  demoLabel?: string
  canResetDemo?: boolean
  fileName?: string
  policyHeader?: Record<string, unknown>
  summaryCards?: Array<Record<string, unknown>>
  counts?: Record<string, unknown>
  structure?: Array<Record<string, unknown>>
  pipeline?: Record<string, unknown>
  readinessBanner?: Record<string, unknown>
  ambiguityCards?: Array<Record<string, unknown>>
  ambiguityCategories?: Array<Record<string, unknown>>
  ruleCards?: Array<Record<string, unknown>>
  humanReviewBanner?: string
  source?: Record<string, unknown>
  interpretation?: Record<string, unknown>
  executable?: Record<string, unknown>
  sourceTextPreview?: string
}

export type PolicyStudioLanding = Record<string, unknown> & {
  title?: string
  subtitle?: string
  supportedFormats?: string[]
  capabilities?: string[]
  humanReviewBanner?: string
  demoPolicies?: Array<Record<string, unknown>>
}

export type StagingReplayResult = {
  caseCode: string
  originalHash?: string
  replayHash?: string
  match?: boolean
  intraRunReplayMatch?: boolean
  fixtureBanner?: string
}

export async function getStagingDemoHealth(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/health`)
  return data
}

export async function listStagingCases(): Promise<StagingCaseSummary[]> {
  const { data } = await http.get<{ cases: StagingCaseSummary[] }>(`${BASE}/cases`)
  return data.cases ?? []
}

export async function getStagingWorkspace(caseCode: string): Promise<StagingWorkspace> {
  const { data } = await http.get<StagingWorkspace>(`${BASE}/cases/${encodeURIComponent(caseCode)}/workspace`)
  return data
}

export async function getPolicyStudioLanding(): Promise<PolicyStudioLanding> {
  const { data } = await http.get<PolicyStudioLanding>(`${BASE}/policy-studio`)
  return data
}

/** POLICY-CREATION-1 — Start from scratch draft (no upload). */
export async function createPolicyFromScratch(body: {
  policyName: string
  description?: string
}): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(`${BASE}/policy-studio/create`, body)
  return data
}

/** POLICY-CREATION-1 — Copy existing policy into a new draft. */
export async function copyPolicyStudioDocument(
  documentId: string,
  body?: { policyName?: string },
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/copy`,
    body ?? {},
  )
  return data
}

/** POLICY-UX-2A — universal credit capability catalogue (read model). */
export type CreditCapabilityCatalogue = Record<string, unknown> & {
  title?: string
  capabilityCount?: number
  groups?: Record<string, Array<Record<string, unknown>>>
  allowCanonicalAuthority?: boolean
}

export async function getCreditCapabilityCatalogue(
  advanced = false,
): Promise<CreditCapabilityCatalogue> {
  const { data } = await http.get<CreditCapabilityCatalogue>(`${BASE}/policy-studio/capabilities`, {
    params: { advanced },
  })
  return data
}

export async function searchCreditCapabilities(
  q: string,
  advanced = false,
): Promise<CreditCapabilityCatalogue & { matches?: Array<Record<string, unknown>>; matchCount?: number }> {
  const { data } = await http.get(`${BASE}/policy-studio/capabilities/search`, {
    params: { q, advanced },
  })
  return data
}

/** POLICY-PARAMETER-RESOLVER-1 — CanonicalParameterRegistry read model */
export async function getParameterCatalogue(source?: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/policy-studio/parameters`, {
    params: source ? { source } : undefined,
  })
  return data
}

export async function searchCanonicalParameters(q: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/policy-studio/parameters/search`, {
    params: { q },
  })
  return data
}

export async function proposeParameterDefinition(body: {
  term: string
  description: string
}): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/parameters/propose-definition`,
    body,
  )
  return data
}

/** POLICY-STUDIO-GATE2 — authoritative business-concept resolution (optional source constraint). */
export async function resolveBusinessConcept(body: {
  concept: string
  source?: string
}): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/parameters/resolve-concept`,
    body,
  )
  return data
}

/** POLICY-STUDIO-GATE3 — executability ladder for a canonical parameter. */
export async function getParameterExecutability(
  parameterId: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/parameters/${encodeURIComponent(parameterId)}/executability`,
  )
  return data
}

export type CatalogueCapabilityAddBody = {
  businessCapabilityId: string
  parameters?: Record<string, unknown>
  failureTreatment?: string
  dataRequirement?: string
  useManualInput?: boolean
  manualInputLabel?: string
  manualInputType?: string
  requiredActor?: string
  ruleId?: string
}

export async function addCatalogueCapabilityRule(
  documentId: string,
  body: CatalogueCapabilityAddBody,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/rules/add-catalogue-capability`,
    body,
  )
  return data
}

export async function getStagingPolicyStudio(kind: 'banking' | 'bureau' | 'kyc'): Promise<StagingPolicyStudio> {
  const { data } = await http.get<StagingPolicyStudio>(`${BASE}/policy-studio/${kind}`)
  return data
}

export async function uploadPolicyStudioFile(file: File): Promise<StagingPolicyStudio> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<StagingPolicyStudio>(`${BASE}/policy-studio/upload`, form, {
    timeout: 120_000,
  })
  return data
}

export async function getPolicyStudioSession(documentId: string): Promise<StagingPolicyStudio> {
  const { data } = await http.get<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}`,
  )
  return data
}

export async function getPolicyImplementability(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/implementability`,
  )
  return data
}

export async function openBusinessMeasureDesigner(
  documentId: string,
  dataElementCode: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/business-measures/designer`,
    { params: { dataElementCode } },
  )
  return data
}

export async function confirmBusinessMeasure(
  documentId: string,
  body: Record<string, unknown>,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/business-measures/confirm`,
    body,
  )
  return data
}

export async function approveBusinessMeasure(
  documentId: string,
  measureId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/business-measures/${encodeURIComponent(measureId)}/approve`,
    body,
  )
  return data
}

export async function resetDemoPolicy(kind: 'banking' | 'bureau' | 'kyc'): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/${encodeURIComponent(kind)}/reset`,
  )
  return data
}

export type ResolveAmbiguityBody = {
  action?: string
  uiAction?: string
  resolvedOption?: string
  resolvedBy?: string
  notes?: string
  unclearTerm?: string
  rememberDefinition?: boolean
  rememberScope?: 'DOCUMENT' | 'PRODUCT' | 'TENANT'
  payload?: Record<string, unknown>
}

export async function resolvePolicyAmbiguity(
  documentId: string,
  ambiguityId: string,
  body: ResolveAmbiguityBody,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/ambiguities/${encodeURIComponent(ambiguityId)}/resolve`,
    body,
  )
  return data
}

export type ReviewRuleBody = {
  uiAction?:
    | 'APPROVE'
    | 'REJECT'
    | 'APPROVE_RULE'
    | 'REJECT_RULE'
    | 'ACCEPT'
    | 'EDIT'
    | 'IGNORE'
    | 'IGNORE_FOR_NOW'
    | 'IGNORE_FOR_AUTOMATION'
    | 'KEEP_AS_POLICY_REQUIREMENT'
    | 'RECLASSIFY'
    | 'DELETE'
    | 'EXCLUDE'
    | 'MANUAL_INPUT'
    | 'MANUAL_VERIFICATION'
    | 'DEFINE_CLEAN'
    | 'USE_EXISTING_CLEAN_DEFINITION'
    | 'MANUAL_CLEAN_INPUT'
    | 'RESOLVE_PARAMETER_MAP'
    | 'RESOLVE_PARAMETER_MANUAL'
    | 'RESOLVE_PARAMETER_USE_PROPOSAL'
    | 'RESOLVE_PARAMETER_UNAVAILABLE'
    | 'RESOLVE_DATA_THRESHOLD'
    | 'RESOLVE_DATA_CLASSIFICATION'
    | 'RESOLVE_DATA_CALCULATION'
    | 'RESOLVE_DATA_ADJUSTMENT'
    | 'RESOLVE_DATA_MANUAL'
  reviewer?: string
  reviewerRole?: string
  reviewState?: string
  reason?: string
  humanChanges?: Record<string, unknown>
  businessRule?: string
  threshold?: string | number
  manualInputLabel?: string
  manualInputType?: string
  requiredActor?: string
  /** POLICY-PARAMETER-RESOLVER-1 */
  operandKey?: string
  originalTerm?: string
  parameterId?: string
  unit?: string
  guidance?: string
  proposal?: Record<string, unknown>
  [key: string]: unknown
}

export async function reviewPolicyRule(
  documentId: string,
  ruleId: string,
  body: ReviewRuleBody,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/rules/${encodeURIComponent(ruleId)}/review`,
    body,
  )
  return data
}

export async function addPlainEnglishPolicyRule(
  documentId: string,
  body: Record<string, unknown>,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/rules/add-plain-english`,
    body,
  )
  return data
}

/** POLICY-RULE-AUTHORING-FIX-1 */
export async function getRuleAuthoringSources(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/policy-studio/rule-authoring/sources`)
  return data
}

export async function previewPolicyRule(
  documentId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/rules/preview`,
    body,
  )
  return data
}

/** POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 — EMI Bounce Count preview (same calculator as Policy Test). */
export async function previewDataCalculation(
  documentId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/data-calculations/preview`,
    body,
  )
  return data
}

export async function replayStagingCase(caseCode: string): Promise<StagingReplayResult> {
  const { data } = await http.post<StagingReplayResult>(
    `${BASE}/cases/${encodeURIComponent(caseCode)}/replay`,
  )
  return data
}

export type SimulationContext = Record<string, unknown> & {
  policyName?: string
  policyStatus?: string
  readyForSimulation?: boolean
  applications?: Array<Record<string, unknown>>
  history?: Array<Record<string, unknown>>
  defaultSelected?: string[]
  simulationBanner?: string
}

export type SimulationResult = Record<string, unknown> & {
  runId?: string
  aggregates?: Record<string, unknown>
  applications?: Array<Record<string, unknown>>
  policyImpact?: Array<Record<string, unknown>>
  ruleImpact?: Array<Record<string, unknown>>
  missingDataSummary?: Array<Record<string, unknown>>
  history?: Array<Record<string, unknown>>
  technicalDetails?: Record<string, unknown>
  simulationBanner?: string
}

export async function getSimulationContext(documentId: string): Promise<SimulationContext> {
  const { data } = await http.get<SimulationContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulation`,
  )
  return data
}

/** POLICY-UX-2E — Credit Manager Test experience */
export type PolicyTestContext = Record<string, unknown> & {
  title?: string
  modes?: Array<Record<string, unknown>>
  requiredParameters?: Array<Record<string, unknown>>
  readiness?: Record<string, unknown>
  applications?: Array<Record<string, unknown>>
  recentTests?: Array<Record<string, unknown>>
  historicalBatch?: Record<string, unknown>
}

export type PolicyTestResult = Record<string, unknown> & {
  simulatedDecision?: string
  summary?: Record<string, unknown>
  ruleResults?: Array<Record<string, unknown>>
  blockers?: unknown[]
  recentTests?: Array<Record<string, unknown>>
  currentVsDraft?: Record<string, unknown>
}

export async function getPolicyTestContext(documentId: string): Promise<PolicyTestContext> {
  const { data } = await http.get<PolicyTestContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/test`,
  )
  return data
}

export async function runPolicyQuickTest(
  documentId: string,
  body: { testValues?: Record<string, unknown>; product?: string } = {},
): Promise<PolicyTestResult> {
  const { data } = await http.post<PolicyTestResult>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/test/quick`,
    body,
    { timeout: 120_000 },
  )
  return data
}

export async function runPolicyApplicationTest(
  documentId: string,
  body: {
    applicationCode: string
    testValues?: Record<string, unknown>
    reviewer?: string
  },
): Promise<PolicyTestResult> {
  const { data } = await http.post<PolicyTestResult>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/test/application`,
    body,
    { timeout: 180_000 },
  )
  return data
}

export async function listSimulationApplications(
  dataSource = 'VALIDATION_FIXTURES',
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/simulation/applications`,
    { params: { dataSource } },
  )
  return data
}

export async function runPolicySimulation(
  documentId: string,
  body: {
    applicationCodes?: string[]
    dataSource?: string
    reviewer?: string
  },
): Promise<SimulationResult> {
  const { data } = await http.post<SimulationResult>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulate`,
    body,
    { timeout: 180_000 },
  )
  return data
}

export async function getSimulationRun(
  documentId: string,
  runId: string,
): Promise<SimulationResult> {
  const { data } = await http.get<SimulationResult>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulations/${encodeURIComponent(runId)}`,
  )
  return data
}

export async function listSimulationHistory(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulations`,
  )
  return data
}

export function simulationExportCsvUrl(documentId: string, runId: string): string {
  return `/api/v1/${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulations/${encodeURIComponent(runId)}/export.csv`
}

export type ApprovalsContext = Record<string, unknown> & {
  stages?: Array<Record<string, unknown>>
  readinessChecklist?: Array<Record<string, unknown>>
  blockingItems?: Array<Record<string, unknown>>
  nonBlockingItems?: Array<Record<string, unknown>>
  canBuildDraft?: boolean
  canApproveCreditManager?: boolean
  canApproveChecker?: boolean
  creditManagerApproved?: boolean
  checkerApproved?: boolean
  approvalInvalidated?: boolean
  draftSummary?: Record<string, unknown>
  draftBanner?: string
  demoActors?: Array<Record<string, unknown>>
}

export async function getApprovalsContext(documentId: string): Promise<ApprovalsContext> {
  const { data } = await http.get<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/approvals`,
  )
  return data
}

export async function submitCreditManagerApproval(
  documentId: string,
  body: { reviewer?: string; comments?: string; reason?: string },
): Promise<ApprovalsContext> {
  const { data } = await http.post<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/approvals/credit-manager`,
    body,
  )
  return data
}

export async function submitCheckerApproval(
  documentId: string,
  body: { reviewer?: string; comments?: string; reason?: string },
): Promise<ApprovalsContext> {
  const { data } = await http.post<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/approvals/checker`,
    body,
  )
  return data
}

export async function markSimulationReviewed(
  documentId: string,
  body: { runId?: string; reviewer?: string },
): Promise<ApprovalsContext> {
  const { data } = await http.post<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/simulation/reviewed`,
    body,
  )
  return data
}

export async function buildDraftPolicy(
  documentId: string,
  body: { createdBy?: string; reviewer?: string } = {},
): Promise<ApprovalsContext> {
  const { data } = await http.post<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/build-draft-policy`,
    body,
  )
  return data
}

export async function compareDraftVersions(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/draft-diff`,
  )
  return data
}

export async function invalidateApprovalDemo(
  documentId: string,
  body: { reviewer?: string } = {},
): Promise<ApprovalsContext> {
  const { data } = await http.post<ApprovalsContext>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/approvals/invalidate-demo`,
    body,
  )
  return data
}

export async function listPolicyTests(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/test-cases`,
  )
  return data
}

export async function reviewPolicyTest(
  documentId: string,
  testId: string,
  body: {
    uiAction?: string
    expectedOutcome?: string
    reviewer?: string
    reason?: string
  },
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/test-cases/${encodeURIComponent(testId)}/review`,
    body,
  )
  return data
}

export async function runDemoHappyPath(): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/demo/happy-path`,
    {},
    { timeout: 180_000 },
  )
  return data
}

export async function runDemoBlockedPath(): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/demo/blocked-path`,
    {},
    { timeout: 120_000 },
  )
  return data
}

// ─── Day 6.2 Policy lifecycle / applicability ───────────────────────

export async function getLifecycleSettings(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle`,
  )
  return data
}

export async function getLifecycleHistory(documentId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/history`,
  )
  return data
}

export async function saveLifecycleDraft(
  documentId: string,
  body: Record<string, unknown>,
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/save-draft`,
    body,
  )
  return data
}

export async function submitLifecycleReview(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/submit-review`,
    body,
  )
  return data
}

export async function approveLifecyclePolicy(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/approve`,
    body,
  )
  return data
}

export async function scheduleLifecyclePolicy(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/schedule`,
    body,
  )
  return data
}

export async function retireLifecyclePolicy(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/retire`,
    body,
  )
  return data
}

/** POLICY-STUDIO-UX-CLOSURE-1 — hard-delete never-activated DRAFT (backend-authorised). */
export async function deleteDraftLifecyclePolicy(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/delete-draft`,
    body,
  )
  return data
}

export async function createLifecycleVersion(
  documentId: string,
  body: Record<string, unknown> = {},
): Promise<StagingPolicyStudio> {
  const { data } = await http.post<StagingPolicyStudio>(
    `${BASE}/policy-studio/documents/${encodeURIComponent(documentId)}/lifecycle/new-version`,
    body,
  )
  return data
}

export async function resolveShadowApplication(
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-studio/lifecycle/resolve-shadow`,
    body,
  )
  return data
}

export type PolicyCatalogueEntry = Record<string, unknown> & {
  applicabilityId?: string
  policyName?: string
  policyVersion?: string
  status?: string
  products?: string[]
  effectiveFrom?: string
  effectiveUntil?: string
}

export async function getPolicyCatalogue(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/policy-catalogue`)
  return data
}

export async function scheduleCataloguePolicy(
  applicabilityId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-catalogue/${encodeURIComponent(applicabilityId)}/schedule`,
    body,
  )
  return data
}

export async function retireCataloguePolicy(
  applicabilityId: string,
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-catalogue/${encodeURIComponent(applicabilityId)}/retire`,
    body,
  )
  return data
}

export async function getApplicablePolicy(
  applicationId: string,
  evaluationDate?: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/applications/${encodeURIComponent(applicationId)}/applicable-policy`,
    { params: evaluationDate ? { evaluationDate } : undefined },
  )
  return data
}

export async function shadowRouteApplication(
  applicationId: string,
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/applications/${encodeURIComponent(applicationId)}/shadow-route`,
    body,
  )
  return data
}

/** KYC-5 — Decision Policy KYC shadow evaluation (never authoritative). */
export async function evaluateKycShadow(
  applicationId: string,
  body: Record<string, unknown> = { persist: true },
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/applications/${encodeURIComponent(applicationId)}/kyc-shadow/evaluate`,
    body,
  )
  return data
}

export async function getLatestKycShadow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/applications/${encodeURIComponent(applicationId)}/kyc-shadow/latest`,
  )
  return data
}

export async function replayKycShadow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/applications/${encodeURIComponent(applicationId)}/kyc-shadow/replay`,
    {},
  )
  return data
}

export async function getKycShadowFixtureMatrix(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/kyc-shadow/fixture-matrix`)
  return data
}

/** KYC-7 — end-to-end Decision Policy simulation fixture matrix (shadow only). */
export async function getDecisionPolicyE2eFixtureMatrix(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/decision-policy-e2e/fixture-matrix`,
  )
  return data
}

export async function runDecisionPolicyE2eSimulation(
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/decision-policy-e2e/simulate`,
    body,
  )
  return data
}

export async function getDecisionPolicyE2eVersionTransition(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/decision-policy-e2e/version-transition`,
  )
  return data
}

/** DP-V1 — real/stored Decision Policy corpus (shadow only). */
export async function getDecisionPolicyCorpusDiscovery(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/decision-policy-corpus/discovery`)
  return data
}

export async function getDecisionPolicyCorpusExportSpec(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/decision-policy-corpus/export-spec`)
  return data
}

export async function importDecisionPolicyCorpus(
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/decision-policy-corpus/import`,
    body,
  )
  return data
}

export async function runDecisionPolicyCorpusValidation(
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/decision-policy-corpus/validate`,
    body,
  )
  return data
}

export async function getDecisionPolicyCorpusDashboard(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/decision-policy-corpus/dashboard`)
  return data
}

export async function getPolicyDiscovery(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/policy-catalogue/discovery`)
  return data
}

export async function linkImmutableCataloguePolicy(
  applicabilityId: string,
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policy-catalogue/${encodeURIComponent(applicabilityId)}/link-immutable`,
    body,
  )
  return data
}

export async function reclassifyPolicyCatalogue(): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`${BASE}/policy-catalogue/reclassify`, {})
  return data
}

export async function getP2ValidationDashboard(): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`${BASE}/p2-validation/dashboard`)
  return data
}

export async function runP2Validation(
  body: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`${BASE}/p2-validation/run`, body)
  return data
}
