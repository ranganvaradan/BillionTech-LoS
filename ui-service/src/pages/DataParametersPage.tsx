import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import {
  getDataParametersBySource,
  getDataParametersDetail,
  getDataParametersOverview,
  searchDataParameters,
} from '@/api/liveReadiness'
import { ApiError } from '@/api/http'
import {
  capabilityFromParameter,
  lenderOrgLabel,
  matchesDp1Filters,
  nestStatus,
  overallReadinessLabel,
  parameterSupportBusinessLabel,
  platformIntegrationLabel,
  providerStatusLabel,
  sourceTypeLabel,
  type Dp1ListFilters,
} from '@/lib/dataParameters/dp1Display'
import { lenderPrimaryFromTruth, type CanonicalTruthLike } from '@/lib/policyStudio/lenderTruthDisplay'
import { SuggestCalculationWorkflow } from '@/components/dataParameters/SuggestCalculationWorkflow'

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

type Tab = 'by-source' | 'by-parameter' | 'gaps' | 'diagnostics'

function Badge({
  children,
  className = '',
  testId,
}: {
  children: React.ReactNode
  className?: string
  testId?: string
}) {
  return (
    <span
      data-testid={testId}
      className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${className}`}
    >
      {children}
    </span>
  )
}

function ParameterBadges({
  p,
}: {
  p: Record<string, unknown>
  hideOrgBadge?: boolean
}) {
  const truth = (asRecord(p.canonicalTruth).primaryStatusLabel
    ? asRecord(p.canonicalTruth)
    : {
        primaryStatus: p.primaryStatus,
        primaryStatusLabel: p.primaryStatusLabel,
        nextAction: p.nextAction,
        calculationExplanation: p.calculationExplanation,
        execution: asRecord(asRecord(p.canonicalTruth).execution),
        certification: asRecord(asRecord(p.canonicalTruth).certification),
        liveUseDisplay: asRecord(p.liveUse),
      }) as CanonicalTruthLike
  const primary = lenderPrimaryFromTruth(truth)
  const classLabel = String(
    truth.parameterClassLabel ?? p.parameterClassLabel ?? p.type ?? sourceTypeLabel(p.sourceType),
  )
  const certLabel = String(
    truth.certificationLabel ??
      asRecord(truth.liveUseDisplay).label ??
      p.certificationLabel ??
      '',
  )
  const next = primary.nextAction

  return (
    <div className="mt-1 flex flex-wrap gap-1" data-testid="dp1-badges">
      <Badge className="border-slate-200 bg-white text-slate-700" testId="dp-parameter-class">
        {classLabel}
      </Badge>
      <Badge
        className={
          primary.label === 'Calculation needs setup' || primary.label === 'Needs manual input'
            ? 'border-amber-200 bg-amber-50 text-amber-950'
            : primary.label.includes('Ready to test') ||
                primary.label === 'Can calculate when data is available' ||
                primary.label === 'Approved for live use'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
              : 'border-slate-200 bg-slate-50 text-slate-700'
        }
        testId="dp-primary-status"
      >
        {primary.label}
      </Badge>
      {certLabel && primary.label !== certLabel ? (
        <Badge className="border-slate-200 bg-slate-50 text-slate-600" testId="dp-live-use">
          {certLabel}
        </Badge>
      ) : null}
      {next ? (
        <Badge className="border-sky-200 bg-sky-50 text-sky-900" testId="dp-next-action">
          {next}
        </Badge>
      ) : null}
    </div>
  )
}

function FactRow({ label, value }: { label: string; value: unknown }) {
  const on = value === true
  const off = value === false
  return (
    <div className="flex items-center justify-between gap-2 border-b border-slate-50 py-1 text-xs">
      <span className="text-slate-600">{label}</span>
      <span
        className={
          on ? 'font-semibold text-emerald-800' : off ? 'text-slate-500' : 'text-slate-700'
        }
      >
        {value === true ? 'Yes' : value === false ? 'No' : String(value ?? '—')}
      </span>
    </div>
  )
}

function ParameterCard({
  p,
  onOpen,
  hideOrgBadge = true,
}: {
  p: Record<string, unknown>
  onOpen: (id: string) => void
  hideOrgBadge?: boolean
}) {
  const lineage = asRecord(p.lineage)
  const truth = asRecord(p.canonicalTruth)
  const primaryLabel = String(p.primaryStatusLabel ?? truth.primaryStatusLabel ?? '')
  const how = String(
    p.calculationExplanation ??
      truth.calculationExplanation ??
      lineage.howCalculated ??
      p.calculationSummary ??
      '',
  ).trim()
  const needsSetup =
    primaryLabel === 'Calculation needs setup' || primaryLabel === 'Needs manual input'
  const advanced = asRecord(p.advanced)
  const cap = capabilityFromParameter(p)
  const id = String(p.id ?? '')
  return (
    <li className="rounded border border-slate-100 px-2 py-1.5" data-testid="dp1-parameter-row">
      <button
        type="button"
        className="w-full text-left"
        onClick={() => id && onOpen(id)}
        data-testid="dp1-open-detail"
      >
        <div className="font-medium text-slate-900">{String(p.businessName)}</div>
        <div className="text-xs text-slate-500">{String(p.type)}</div>
        <ParameterBadges p={p} hideOrgBadge={hideOrgBadge} />
        {how ? (
          <p className="mt-1 text-xs text-slate-600" data-testid="dp-card-how">
            {needsSetup && !how.toLowerCase().includes('not set up')
              ? 'Calculation has not yet been configured'
              : how}
          </p>
        ) : needsSetup ? (
          <p className="mt-1 text-xs text-slate-600" data-testid="dp-card-how">
            Calculation has not yet been configured
          </p>
        ) : null}
      </button>
      {how && !needsSetup ? (
        <details className="mt-1 text-xs text-slate-600">
          <summary className="cursor-pointer font-medium text-sky-800">How is this calculated?</summary>
          <p className="mt-1 whitespace-pre-wrap">{how}</p>
          {asList(lineage.rawInputs).length > 0 ? (
            <p className="mt-1 text-slate-500">
              Inputs: {asList(lineage.rawInputs).map(String).join(', ')}
            </p>
          ) : null}
        </details>
      ) : null}
      <details className="mt-1 text-xs text-slate-500">
        <summary>Advanced / Technical Details</summary>
        <pre className="mt-1 whitespace-pre-wrap">
          {JSON.stringify(
            {
              id,
              providerFieldPath: p.providerFieldPath ?? advanced.providerFieldPath,
              binding: p.existingImplementationBinding ?? advanced.existingImplementationBinding,
              gate3: advanced.gate3,
              policyTestReady: advanced.policyTestReady,
              runtimeReady: advanced.runtimeReady,
              productionReady: advanced.productionReady,
              providerBound: advanced.providerBound,
              mappingAvailable: advanced.mappingAvailable,
              calculatorAvailable: advanced.calculatorAvailable,
              legacyOverallReadiness: advanced.legacyOverallReadiness ?? p.overallReadiness,
              catalogueProductionReady: cap.catalogueProductionReady,
              legacyParameterSupport: nestStatus(cap.parameterSupport ?? p.parameterSupport),
              canonicalTruth: truth.primaryStatusLabel ? truth : undefined,
            },
            null,
            2,
          )}
        </pre>
      </details>
    </li>
  )
}

function ParameterDetailPanel({
  detail,
  onClose,
}: {
  detail: Record<string, unknown> | null
  onClose: () => void
}) {
  if (!detail) return null
  if (detail.found === false) {
    return (
      <section
        className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm"
        data-testid="dp1-detail-panel"
      >
        <div className="flex items-start justify-between gap-2">
          <p>{String(detail.message ?? 'Parameter not found')}</p>
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
            Close
          </button>
        </div>
      </section>
    )
  }
  const parameter = asRecord(detail.parameter)
  const sections = asRecord(detail.sections ?? parameter.sections)
  const definition = asRecord(sections.definition)
  const lenderCap = asRecord(sections.lenderCapability)
  const source = asRecord(sections.source)
  const mapping = asRecord(sections.mappingCalculation)
  const consumers = asRecord(sections.consumers ?? parameter.consumers)
  const provenance = asRecord(sections.provenance)
  const provider = asRecord(source.provider ?? parameter.provider)
  const advanced = asRecord(parameter.advanced)
  const capability = asRecord(detail.capability ?? parameter.capability)
  const design = asRecord(capability.policyDesign ?? parameter.policyDesign)
  const live = asRecord(capability.liveUse ?? parameter.liveUse)
  const support = asRecord(capability.parameterSupport ?? parameter.parameterSupport)

  return (
    <section
      className="space-y-4 rounded-xl border border-slate-200 bg-white p-4"
      data-testid="dp1-detail-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">
            {String(definition.displayName ?? parameter.businessName)}
          </h2>
          <ParameterBadges p={parameter} hideOrgBadge={false} />
        </div>
        <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
          Close
        </button>
      </div>

      <div
        className="rounded-lg border border-slate-100 bg-slate-50 p-3"
        data-testid="dp-lender-capability"
      >
        <h3 className="text-sm font-semibold text-slate-900">Execution readiness</h3>
        <dl className="mt-2 grid gap-2 text-xs text-slate-700 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <dt className="text-slate-500">Primary status</dt>
            <dd className="font-semibold" data-testid="dp-detail-primary-status">
              {String(
                lenderCap.primaryStatusLabel ??
                  parameter.primaryStatusLabel ??
                  asRecord(parameter.canonicalTruth).primaryStatusLabel ??
                  '—',
              )}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Source</dt>
            <dd>
              {String(lenderCap.providerLabel ?? lenderCap.source ?? parameter.sourceFamily ?? '—')}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">BillionTech integration</dt>
            <dd data-testid="dp-detail-platform">
              {String(
                lenderCap.platformIntegration ??
                  platformIntegrationLabel(nestStatus(capability.platformIntegration)),
              )}
            </dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-slate-500">How is this calculated?</dt>
            <dd>
              {String(
                lenderCap.calculationExplanation ??
                  parameter.calculationExplanation ??
                  lenderCap.how ??
                  support.businessHow ??
                  support.how ??
                  '—',
              )}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Your organisation</dt>
            <dd data-testid="dp-detail-org">
              {String(
                lenderCap.yourOrganisation ??
                  lenderOrgLabel(nestStatus(capability.yourOrganisation)),
              )}
            </dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-slate-500">Live use</dt>
            <dd data-testid="dp-detail-live-use">
              {String(
                lenderCap.certificationLabel ??
                  lenderCap.liveUse ??
                  live.label ??
                  'Not approved for live use',
              )}
              {live.reason || lenderCap.liveUseReason ? (
                <span className="mt-0.5 block font-normal text-slate-500">
                  {String(lenderCap.liveUseReason ?? live.reason)}
                </span>
              ) : null}
            </dd>
          </div>
        </dl>
        <details className="mt-2 text-[11px] text-slate-500">
          <summary className="cursor-pointer">Advanced / Technical — legacy support metadata</summary>
          <dl className="mt-1 grid gap-1 sm:grid-cols-2">
            <div>
              <dt>Legacy parameter support</dt>
              <dd data-testid="dp-detail-support">
                {String(
                  lenderCap.parameterSupport ??
                    support.businessLabel ??
                    parameterSupportBusinessLabel(nestStatus(support)),
                )}
              </dd>
            </div>
            <div>
              <dt>Legacy policy design flag</dt>
              <dd data-testid="dp-detail-policy-design">
                {String(lenderCap.policyDesign ?? design.label ?? '—')}
              </dd>
            </div>
          </dl>
        </details>
        <p className="mt-2 text-[11px] text-slate-500">
          Primary status is from CanonicalParameterTruthProjection — not catalogue support lists.
        </p>
      </div>

      <SuggestCalculationWorkflow
        canonicalParameterId={String(definition.canonicalId ?? parameter.id ?? '')}
        businessName={String(definition.businessName ?? parameter.businessName ?? '')}
        primitives={asList(asRecord(sections.provenance).requiredPrimitives ?? parameter.requiredPrimitives).map(
          String,
        )}
        supportStatus={String(nestStatus(support) || support.status || '')}
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">Definition</h3>
          <dl className="mt-2 space-y-1 text-xs text-slate-700">
            <div>
              <dt className="text-slate-500">Canonical ID</dt>
              <dd className="font-mono">{String(definition.canonicalId ?? parameter.id)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Description / business meaning</dt>
              <dd>{String(definition.description ?? parameter.calculationSummary ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Datatype / schema</dt>
              <dd>{String(definition.datatype ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Unit</dt>
              <dd>{String(definition.unit ?? parameter.unit ?? '—')}</dd>
            </div>
          </dl>
        </div>

        <div>
          <h3 className="text-sm font-semibold text-slate-900">Source & acquisition</h3>
          <dl className="mt-2 space-y-1 text-xs text-slate-700">
            <div>
              <dt className="text-slate-500">Source family</dt>
              <dd>{String(source.sourceFamily ?? parameter.sourceFamily ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Source type / acquisition</dt>
              <dd>{sourceTypeLabel(source.sourceType ?? parameter.sourceType)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Workflow / acquisition</dt>
              <dd>
                {asList(asRecord(source.workflow).productionSteps).map(String).join(', ') ||
                  asList(asRecord(source.workflow).studioOnlySteps).map(String).join(', ') ||
                  asList(asRecord(source.workflow).integrations).map(String).join(', ') ||
                  '—'}
              </dd>
            </div>
          </dl>
        </div>

        <div>
          <h3 className="text-sm font-semibold text-slate-900">Mapping / Calculation</h3>
          <dl className="mt-2 space-y-1 text-xs text-slate-700">
            <div>
              <dt className="text-slate-500">Raw / source path(s)</dt>
              <dd className="break-all font-mono">
                {asList(mapping.rawSourcePaths).map(String).filter(Boolean).join(' · ') || '—'}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Calculator</dt>
              <dd data-testid="dp1-calculator">{String(mapping.calculator ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Derived / raw</dt>
              <dd>{String(mapping.derivedOrRaw ?? parameter.type ?? '—')}</dd>
            </div>
          </dl>
        </div>

        <div>
          <h3 className="text-sm font-semibold text-slate-900">Consumers</h3>
          <dl className="mt-2 space-y-1 text-xs text-slate-700" data-testid="dp1-consumers">
            <div>
              <dt className="text-slate-500">Scorecard</dt>
              <dd>{asList(consumers.scorecardLegacyKeys).map(String).join(', ') || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Runtime</dt>
              <dd>{asList(consumers.runtimeFactAliases).map(String).join(', ') || '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Workflow / integrations</dt>
              <dd>{asList(consumers.workflowOrIntegrations).map(String).join(', ') || '—'}</dd>
            </div>
          </dl>
        </div>

        <div className="lg:col-span-2">
          <h3 className="text-sm font-semibold text-slate-900">Provenance</h3>
          <p className="mt-2 text-xs text-slate-700" data-testid="dp1-provenance">
            {String(provenance.provenanceModel ?? '—')}
            {asList(provenance.requiredPrimitives).length
              ? ` · primitives: ${asList(provenance.requiredPrimitives).map(String).join(', ')}`
              : ''}
          </p>
        </div>
      </div>

      <details className="text-xs text-slate-500" data-testid="dp-advanced-technical">
        <summary className="cursor-pointer font-medium">Advanced / Technical Details</summary>
        <div className="mt-2 space-y-2 rounded border border-slate-100 bg-slate-50 p-2">
          <p className="text-[11px] text-slate-500">
            Engineering evidence (Policy Test / Runtime / Gate3 / providerBound / mapping). Not the primary
            lender status model.
          </p>
          <div data-testid="dp1-readiness-facts">
            <FactRow
              label="Policy Test Ready"
              value={advanced.policyTestReady ?? parameter.policyTestReady}
            />
            <FactRow label="Runtime Ready" value={advanced.runtimeReady ?? parameter.runtimeReady} />
            <FactRow
              label="Production Ready (catalogue)"
              value={advanced.productionReady ?? parameter.productionReady}
            />
            <FactRow
              label="Workflow Available"
              value={advanced.workflowAvailable ?? parameter.workflowAvailable}
            />
            <FactRow label="Provider Bound" value={advanced.providerBound ?? parameter.providerBound} />
            <FactRow
              label="Mapping Available"
              value={advanced.mappingAvailable ?? parameter.mappingAvailable}
            />
            <FactRow
              label="Calculator Available"
              value={advanced.calculatorAvailable ?? parameter.calculatorAvailable}
            />
            <FactRow
              label="Provenance Available"
              value={advanced.provenanceAvailable ?? parameter.provenanceAvailable}
            />
            <div className="py-1 text-xs">
              Legacy overall:{' '}
              {overallReadinessLabel(advanced.legacyOverallReadiness ?? parameter.overallReadiness)}
            </div>
            <div className="py-1 text-xs">Provider status: {providerStatusLabel(provider)}</div>
          </div>
          <pre className="max-h-60 overflow-auto whitespace-pre-wrap">
            {JSON.stringify(
              {
                gate3: advanced.gate3,
                legacyOverallReadinessReasons: advanced.legacyOverallReadinessReasons,
                readinessProjection: advanced.readinessProjection,
              },
              null,
              2,
            )}
          </pre>
        </div>
      </details>
    </section>
  )
}

function FilterBar({
  filters,
  setFilters,
  families,
  sourceTypes,
  readinessStates,
  supportStatuses,
}: {
  filters: Dp1ListFilters
  setFilters: (f: Dp1ListFilters) => void
  families: string[]
  sourceTypes: string[]
  readinessStates: string[]
  supportStatuses: string[]
}) {
  return (
    <div className="space-y-2" data-testid="dp1-filters">
      <div className="flex flex-wrap gap-2">
        <select
          className="rounded border border-slate-300 px-2 py-1.5 text-xs"
          value={filters.sourceFamily}
          onChange={(e) => setFilters({ ...filters, sourceFamily: e.target.value })}
          aria-label="Filter source family"
        >
          <option value="">Source (all)</option>
          {families.map((f) => (
            <option key={f} value={f}>
              {f}
            </option>
          ))}
        </select>
        <select
          className="rounded border border-slate-300 px-2 py-1.5 text-xs"
          value={filters.sourceType}
          onChange={(e) => setFilters({ ...filters, sourceType: e.target.value })}
          aria-label="Filter parameter class / source type"
        >
          <option value="">Parameter class / source type (all)</option>
          {sourceTypes.map((t) => (
            <option key={t} value={t}>
              {sourceTypeLabel(t)}
            </option>
          ))}
        </select>
        <select
          className="rounded border border-slate-300 px-2 py-1.5 text-xs"
          value={filters.overallReadiness}
          onChange={(e) => setFilters({ ...filters, overallReadiness: e.target.value })}
          aria-label="Filter execution readiness"
          data-testid="dp-filter-execution-readiness"
        >
          <option value="">Execution readiness (all)</option>
          {(readinessStates.length
            ? readinessStates
            : [
                'READY_TO_TEST',
                'CAN_CALCULATE_WHEN_DATA_AVAILABLE',
                'CALCULATION_NEEDS_SETUP',
                'NEEDS_MANUAL_INPUT',
                'APPROVED_FOR_LIVE_USE',
                'NOT_YET_SUPPORTED',
              ]
          ).map((s) => (
            <option key={s} value={s}>
              {overallReadinessLabel(s)}
            </option>
          ))}
        </select>
        <input
          className="min-w-[12rem] flex-1 rounded border border-slate-300 px-2 py-1.5 text-xs"
          placeholder="Filter list…"
          value={filters.q}
          onChange={(e) => setFilters({ ...filters, q: e.target.value })}
          aria-label="Filter list text"
        />
      </div>
      <details className="text-xs text-slate-600" data-testid="dp-legacy-filters-advanced">
        <summary className="cursor-pointer font-medium text-slate-700">
          Advanced / Technical — Legacy metadata
        </summary>
        <div className="mt-2 flex flex-wrap gap-2">
          <select
            className="rounded border border-slate-300 px-2 py-1.5 text-xs"
            value={filters.parameterSupport}
            onChange={(e) => setFilters({ ...filters, parameterSupport: e.target.value })}
            aria-label="Filter legacy parameter support"
          >
            <option value="">Legacy parameter support (all)</option>
            {supportStatuses.map((s) => (
              <option key={s} value={s}>
                {parameterSupportBusinessLabel(s)}
              </option>
            ))}
          </select>
          <select
            className="rounded border border-slate-300 px-2 py-1.5 text-xs"
            value={filters.productionReady}
            onChange={(e) =>
              setFilters({
                ...filters,
                productionReady: e.target.value as Dp1ListFilters['productionReady'],
              })
            }
            aria-label="Filter catalogue production ready"
          >
            <option value="">Catalogue Production Ready (all)</option>
            <option value="true">Catalogue Production Ready</option>
            <option value="false">Catalogue Not Production Ready</option>
          </select>
        </div>
        <p className="mt-1 text-[11px] text-slate-500">
          Legacy catalogue metadata is not execution or live-approval authority.
        </p>
      </details>
    </div>
  )
}

export function DataParametersPage() {
  const [tab, setTab] = useState<Tab>('by-source')
  const [overview, setOverview] = useState<Record<string, unknown> | null>(null)
  const [source, setSource] = useState('')
  const [sourceView, setSourceView] = useState<Record<string, unknown> | null>(null)
  const [q, setQ] = useState('')
  const [search, setSearch] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null)
  const [filters, setFilters] = useState<Dp1ListFilters>({
    sourceFamily: '',
    sourceType: '',
    overallReadiness: '',
    productionReady: '',
    parameterSupport: '',
    q: '',
  })

  useEffect(() => {
    setLoading(true)
    getDataParametersOverview()
      .then((data) => {
        setOverview(data)
        const summaries = asList(data.bySourceSummary).map(asRecord)
        const preferred =
          summaries.find((s) => String(s.source) === 'Bureau Retail' && Number(s.count ?? 0) > 0) ??
          summaries.find((s) => Number(s.count ?? 0) > 0)
        if (preferred?.source) setSource(String(preferred.source))
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load Data & Parameters'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!source) return
    getDataParametersBySource(source)
      .then(setSourceView)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load source'))
  }, [source])

  const summaries = useMemo(() => asList(overview?.bySourceSummary).map(asRecord), [overview])
  const sourceCapability = useMemo(
    () => asList(overview?.sourceCapabilitySummary).map(asRecord),
    [overview],
  )
  const gaps = asRecord(overview?.gapsManual)
  const totals = asRecord(overview?.totals)
  const sourceTypes = asList(overview?.sourceTypes).map(String)
  const readinessStates = asList(overview?.overallReadinessStates).map(String)
  const supportStatuses = asList(overview?.parameterSupportStatuses).map(String)
  const families = summaries.map((s) => String(s.source)).filter(Boolean)
  const drift = asList(overview?.knownCatalogueDrift).map(asRecord)

  const openDetail = async (id: string) => {
    try {
      setDetail(await getDataParametersDetail(id))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load parameter detail')
    }
  }

  const filterParams = (list: unknown[]) =>
    list.map(asRecord).filter((p) => matchesDp1Filters(p, filters))

  const runSearch = async () => {
    if (!q.trim()) return
    try {
      setSearch(await searchDataParameters(q.trim()))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Search failed')
    }
  }

  return (
    <div className="space-y-4" data-testid="data-parameters-page">
      <PageHeader
        title="Data & Parameters"
        description="Setup / capability catalogue: which sources BillionTech has integrated, which parameters those sources can produce, and whether your organisation has subscribed. Not application value availability."
      />
      <AdministrationWorkspaceNav />

      {error ? (
        <div className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
      ) : null}
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}

      {totals.registryCount != null ? (
        <p className="text-xs text-slate-600">
          {String(totals.registryCount)} parameters · capability semantics{' '}
          {overview?.capabilitySemantics ? 'on' : 'off'} · application data state excluded
        </p>
      ) : null}

      <ParameterDetailPanel detail={detail} onClose={() => setDetail(null)} />

      <div className="flex flex-wrap gap-2">
        {(
          [
            ['by-source', 'By Source'],
            ['by-parameter', 'By Parameter'],
            ['gaps', 'Coverage & Gaps'],
            ['diagnostics', 'System Diagnostics'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={`bt-btn bt-btn-sm ${tab === id ? 'bt-btn-primary' : 'bt-btn-secondary'}`}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'by-source' || tab === 'by-parameter' ? (
        <FilterBar
          filters={filters}
          setFilters={setFilters}
          families={families}
          sourceTypes={sourceTypes.length ? sourceTypes : ['PROVIDER', 'APPLICATION_INPUT', 'WORKFLOW', 'MANUAL', 'DERIVED', 'UNKNOWN']}
          readinessStates={
            readinessStates.length
              ? readinessStates
              : [
                  'PRODUCTION_READY',
                  'RUNTIME_READY_NONPROD',
                  'POLICY_TEST_ONLY',
                  'CATALOGUE_ONLY',
                  'READINESS_UNKNOWN',
                ]
          }
          supportStatuses={
            supportStatuses.length
              ? supportStatuses
              : [
                  'SUPPORTED_RAW',
                  'SUPPORTED_DERIVED',
                  'PROVIDER_DOES_NOT_SUPPORT',
                  'CALCULATION_NOT_IMPLEMENTED',
                  'SOURCE_NOT_INTEGRATED',
                  'NOT_APPLICABLE',
                ]
          }
        />
      ) : null}

      {tab === 'by-source' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          {sourceCapability.length > 0 ? (
            <div className="space-y-2" data-testid="dp-source-capability-summary">
              <h3 className="text-sm font-semibold text-slate-900">Sources</h3>
              <div className="grid gap-2 lg:grid-cols-2">
                {sourceCapability.map((s) => {
                  const counts = asRecord(s.parameterSupportCounts)
                  const canonical = asRecord(s.canonicalCounts ?? counts.canonical)
                  const facing = asRecord(s.lenderFacing)
                  const detail = asRecord(facing.expandableDetail)
                  const selected = source === s.source
                  return (
                    <div
                      key={String(s.source)}
                      className={`rounded border px-3 py-2 text-left text-xs ${
                        selected
                          ? 'border-slate-900 bg-slate-900 text-white'
                          : 'border-slate-200 bg-slate-50 text-slate-800'
                      }`}
                    >
                      <button
                        type="button"
                        className="w-full text-left"
                        onClick={() => setSource(String(s.source))}
                      >
                        <div className="font-semibold">{String(s.providerLabel ?? s.source)}</div>
                        <div className={selected ? 'text-slate-200' : 'text-slate-600'}>
                          {String(
                            facing.integrationLabel ??
                              `BillionTech integration: ${platformIntegrationLabel(s.platformIntegration)}`,
                          )}
                        </div>
                        {s.yourOrganisation && String(s.yourOrganisation) !== 'NOT_APPLICABLE' ? (
                          <div className={selected ? 'text-slate-200' : 'text-slate-600'}>
                            {String(
                              facing.organisationLabel ??
                                `Your organisation: ${lenderOrgLabel(s.yourOrganisation)}`,
                            )}
                          </div>
                        ) : null}
                        <div
                          className={`mt-1 ${selected ? 'text-slate-300' : 'text-slate-500'}`}
                          data-testid="dp-source-summary-line"
                        >
                          {String(
                            facing.summaryLine ??
                              (String(s.platformIntegration) === 'NOT_INTEGRATED'
                                ? 'Integration not yet available'
                                : `${Number(canonical.catalogueListed ?? counts.total ?? 0)} catalogue parameters · ${Number(canonical.readyToTest ?? 0)} ready to test · ${Number(canonical.setupRequired ?? 0)} setup required`),
                          )}
                        </div>
                      </button>
                      <details className={`mt-1 ${selected ? 'text-slate-300' : 'text-slate-500'}`}>
                        <summary className="cursor-pointer">More detail</summary>
                        <ul className="mt-1 list-disc pl-4">
                          <li>
                            Directly provided:{' '}
                            {String(detail.directlyProvided ?? canonical.directlyProvided ?? 0)}
                          </li>
                          <li>
                            Calculated:{' '}
                            {String(
                              detail.calculatedByBillionTech ?? canonical.calculated ?? 0,
                            )}
                          </li>
                          <li>Manual: {String(detail.manual ?? canonical.manual ?? 0)}</li>
                          <li>
                            Ready to test: {String(detail.readyToTest ?? canonical.readyToTest ?? 0)}
                          </li>
                          <li>
                            Setup required:{' '}
                            {String(detail.setupRequired ?? canonical.setupRequired ?? 0)}
                          </li>
                          <li>
                            Live approved:{' '}
                            {String(detail.liveApproved ?? canonical.liveApproved ?? 0)}
                          </li>
                        </ul>
                        <p className="mt-1 text-[10px] opacity-80">
                          Counts from CanonicalParameterTruthProjection — not list length.
                        </p>
                      </details>
                    </div>
                  )
                })}
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              {summaries.map((s) => (
                <button
                  key={String(s.source)}
                  type="button"
                  className={`rounded-full px-3 py-1 text-xs font-semibold ${
                    source === s.source ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
                  }`}
                  onClick={() => setSource(String(s.source))}
                >
                  {String(s.source)}
                </button>
              ))}
            </div>
          )}
          {sourceView ? (
            <div className="grid gap-4 lg:grid-cols-3">
              {(
                [
                  ['raw', 'Directly provided'],
                  ['derived', 'Calculated'],
                  ['manual', 'Application / manual'],
                ] as const
              ).map(([key, label]) => (
                <div key={key}>
                  <h3 className="text-sm font-semibold text-slate-900">
                    {label} ({filterParams(asList(sourceView[key])).length})
                  </h3>
                  <ul className="mt-2 space-y-2 text-sm">
                    {filterParams(asList(sourceView[key])).map((p, i) => (
                      <ParameterCard key={i} p={p} onOpen={(id) => void openDetail(id)} hideOrgBadge />
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}

      {tab === 'by-parameter' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap gap-2">
            <input
              className="min-w-[16rem] flex-1 rounded border border-slate-300 px-3 py-2 text-sm"
              placeholder="Search dpd, cibil, overdue, adb, gst turnover, foir, dscr…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') void runSearch()
              }}
            />
            <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => void runSearch()}>
              Search
            </button>
          </div>
          <ul className="space-y-2 text-sm">
            {filterParams(asList(search?.results)).map((p, i) => (
              <li key={i} className="rounded border border-slate-100 px-3 py-2" data-testid="dp1-parameter-row">
                <button type="button" className="w-full text-left" onClick={() => void openDetail(String(p.id))}>
                  <div className="font-medium">{String(p.businessName)}</div>
                  <div className="text-xs text-slate-500">
                    {String(p.evaluatedFrom ?? p.sourceFamily)} · {String(p.type)}
                    {p.definitionVersion != null ? ` · v${String(p.definitionVersion)}` : ''}
                  </div>
                  <ParameterBadges p={p} />
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {tab === 'gaps' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 text-sm" data-testid="coverage-gaps">
          <p className="text-xs text-slate-600">
            Coverage describes how parameters can be fulfilled. Intentional customer/application input is not a
            defect. Customer Provided is separate from Data Ready for Policy.
          </p>
          <h3 className="font-semibold">Customer / application input ({String(gaps.manualCount ?? 0)})</h3>
          <p className="text-xs text-slate-500">Direct input or document paths — not an integration gap.</p>
          <ul className="space-y-1">
            {asList(gaps.manualParameters).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i}>
                  <button
                    type="button"
                    className="text-left text-sky-800 hover:underline"
                    onClick={() => void openDetail(String(p.id))}
                  >
                    {String(p.businessName)} <span className="text-slate-500">({String(p.id)})</span>
                  </button>
                  <ParameterBadges p={p} />
                </li>
              )
            })}
          </ul>
          <h3 className="mt-4 font-semibold">
            Integration / fulfilment outstanding ({String(gaps.definedNotImplementedCount ?? 0)})
          </h3>
          <ul className="space-y-1">
            {asList(gaps.definedNotImplemented).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i}>
                  {String(p.businessName)} <span className="text-slate-500">({String(p.id)})</span>
                </li>
              )
            })}
          </ul>
          <h3 className="mt-4 font-semibold">
            Availability / binding gaps ({String(gaps.availabilityGapCount ?? 0)})
          </h3>
          <ul className="space-y-1">
            {asList(gaps.availabilityGaps).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i}>
                  {String(p.businessName)} <span className="text-slate-500">({String(p.id)})</span>
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}

      {tab === 'diagnostics' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700">
          <h3 className="text-sm font-semibold text-slate-900">System diagnostics</h3>
          <p>
            Catalogue authority: <code>{String(overview?.catalogueAuthority ?? '—')}</code>
            {overview?.inventoryVersion ? (
              <>
                {' '}
                · inventory <code>{String(overview.inventoryVersion)}</code>
              </>
            ) : null}
            {overview?.dp1 ? ' · DP-1 surface enabled' : null}
            {overview?.capabilitySemantics ? ' · capability semantics on' : null}
            {overview?.adminWriteEnabled === false ? ' · read-only' : null}
          </p>
          {sourceCapability.length > 0 ? (
            <div data-testid="dp-diagnostics-source-counts">
              <p className="font-semibold">Engineering source support counts</p>
              <ul className="mt-1 space-y-1">
                {sourceCapability.map((s) => {
                  const c = asRecord(s.parameterSupportCounts)
                  return (
                    <li key={String(s.source)}>
                      <code>{String(s.source)}</code> · platform={String(s.platformIntegration)} · org=
                      {String(s.yourOrganisation)} · raw={String(c.supportedRaw)} · derived=
                      {String(c.supportedDerived)} · noSupport={String(c.providerDoesNotSupport)} · calc=
                      {String(c.calculationNotImplemented)} · sourceNI={String(c.sourceNotIntegrated)} ·
                      total={String(c.total)}
                    </li>
                  )
                })}
              </ul>
            </div>
          ) : null}
          {drift.length > 0 ? (
            <div>
              <p className="font-semibold">Known catalogue drift</p>
              <ul className="mt-1 list-disc pl-4">
                {drift.map((d, i) => (
                  <li key={i}>
                    {d.canonicalParameterId ? `${String(d.canonicalParameterId)} — ` : ''}
                    {String(d.detail ?? d.code)}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p>No catalogue drift reported.</p>
          )}
        </section>
      ) : null}
    </div>
  )
}
