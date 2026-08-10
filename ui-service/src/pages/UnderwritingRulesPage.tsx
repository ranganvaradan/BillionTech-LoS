import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  activateUnderwritingRule,
  createUnderwritingRule,
  deactivateUnderwritingRule,
  deleteUnderwritingRule,
  listUnderwritingRules,
  updateUnderwritingRule,
  type HardRuleRow,
  type LimitSizingRow,
  type UnderwritingRuleSetRequest,
  type UnderwritingRuleSetResponse,
} from '@/api/underwritingRules'
import { ApiError } from '@/api/http'
import type { ScorecardParameterDef } from '@/api/scorecards'
import { DependencyConditionsEditor } from '@/components/scorecard/DependencyConditionsEditor'
import { EditorModal } from '@/components/scorecard/EditorModal'
import { FormulaEditor } from '@/components/scorecard/FormulaEditor'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import { UnderwritingPolicyMapView } from '@/components/credit/UnderwritingPolicyMapView'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { AmountInputSplit } from '@/components/ui/AmountInputHint'
import {
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'
import {
  defaultParameterForSource,
  paramDef,
  parametersForSourceWithCurrent,
  SCORECARD_SOURCES,
  scorecardSourceOptionsForLoanProduct,
} from '@/lib/credit/scorecardConfig'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]
const DECISIONS = ['APPROVE', 'REJECT', 'MANUAL_REVIEW'] as const
const SCF_INVOICE_PRODUCT = 'BUSINESS_WC_INVOICE_DISCOUNTING'

const SCORECARD_PARAMS = [
  { value: 'GST_INCOME', label: 'GST income' },
  { value: 'BANK_STATEMENT_INCOME', label: 'Bank statement income' },
  { value: 'EMI_OBLIGATION', label: 'EMI / monthly obligation' },
  { value: 'AVERAGE_BANK_BALANCE', label: 'Average bank balance' },
  { value: 'OBLIGATION_RATIO', label: 'Obligation ratio' },
  { value: 'MONTHLY_INCOME', label: 'Monthly income' },
  { value: 'BUREAU_SCORE', label: 'Bureau score' },
] as const

type ScoreRow = {
  id: string
  parameter: string
  weight: string
  mode: 'GTE' | 'LTE'
  approveAt: string
  manualAt: string
}

type LimitSizingFormRow = {
  key: string
  turnoverParameter: string
  turnoverLimitPercent: string
  standardTicketCap: string
  maxDeviationCap: string
  standardCapMode: 'MIN_OF_BOTH' | 'TURNOVER_PERCENT' | 'FIXED'
  maxDeviationMode: 'FIXED' | 'TURNOVER_PERCENT'
  maxDeviationPercent: string
  dependsOn?: HardRuleRow['dependsOn']
  sanctionCapEnabled: boolean
  camRecommendedCapEnabled: boolean
}

function percentLabel(raw: string): string {
  const n = Number(raw)
  if (!Number.isFinite(n)) return ''
  const pct = n <= 1 ? n * 100 : n
  return `${Math.round(pct * 100) / 100}%`
}

function paramShortLabel(code: string): string {
  const hit = SCORECARD_SOURCES.flatMap((s) => s.parameters).find((p) => p.value === code)
  return hit?.label ?? code.replace(/_/g, ' ')
}

function limitPolicyPlainEnglish(row: LimitSizingFormRow): string[] {
  const param = paramShortLabel(row.turnoverParameter || 'ANNUAL_GST_TURNOVER')
  const pct = percentLabel(row.turnoverLimitPercent)
  const lines: string[] = []

  if (row.standardCapMode === 'FIXED') {
    lines.push(
      row.standardTicketCap.trim()
        ? `1. Standard eligible limit = fixed ₹${row.standardTicketCap} (ignores turnover %).`
        : '1. Standard eligible limit = fixed ticket cap (enter amount below).',
    )
  } else if (row.standardCapMode === 'TURNOVER_PERCENT') {
    lines.push(
      pct
        ? `1. Standard eligible limit = ${pct} of ${param}.`
        : `1. Standard eligible limit = a % of ${param} (enter % below).`,
    )
  } else {
    lines.push(
      pct && row.standardTicketCap.trim()
        ? `1. Standard eligible limit = whichever is smaller: ${pct} of ${param}, or ₹${row.standardTicketCap}.`
        : `1. Standard eligible limit = min(% of ${param}, fixed ticket cap).`,
    )
  }

  if (row.maxDeviationMode === 'TURNOVER_PERCENT') {
    const maxPct = percentLabel(row.maxDeviationPercent)
    lines.push(
      maxPct
        ? `2. Absolute ceiling = ${maxPct} of ${param}. Requested above this is over-cap.`
        : `2. Absolute ceiling = a % of ${param} (enter max deviation %).`,
    )
  } else {
    lines.push(
      row.maxDeviationCap.trim()
        ? `2. Absolute ceiling = ₹${row.maxDeviationCap}. Requested above this is over-cap.`
        : '2. Absolute ceiling = fixed max deviation cap (enter amount below).',
    )
  }

  lines.push(
    '3. Between standard limit and absolute ceiling → special deviation band (usually hard-rule MANUAL_REVIEW).',
  )
  lines.push(
    '4. Hard rules decide reject / manual review. Optional checkboxes below can auto-reduce sanction / CAM to the standard limit.',
  )
  if ((row.dependsOn?.conditions?.length ?? 0) > 0) {
    lines.push(
      `5. This policy runs only when its ${row.dependsOn!.conditions!.length} dependency condition(s) match.`,
    )
  } else {
    lines.push('5. No dependencies → this policy applies whenever the rule set matches the application.')
  }
  return lines
}

function newHardRule(): HardRuleRow {
  return {
    id: `uh-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    parameter: 'BUREAU_SCORE',
    source: 'BUREAU',
    condition: 'LT:650',
    decision: 'REJECT',
  }
}

function newLimitSizingRow(partial?: Partial<LimitSizingFormRow>): LimitSizingFormRow {
  return {
    key: `ls-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    turnoverParameter: 'ANNUAL_GST_TURNOVER',
    turnoverLimitPercent: '',
    standardTicketCap: '',
    maxDeviationCap: '',
    standardCapMode: 'MIN_OF_BOTH',
    maxDeviationMode: 'FIXED',
    maxDeviationPercent: '',
    dependsOn: undefined,
    sanctionCapEnabled: false,
    camRecommendedCapEnabled: false,
    ...partial,
  }
}

function scfSuggestedLimitSizingRow(): LimitSizingFormRow {
  return newLimitSizingRow({
    turnoverParameter: 'ANNUAL_GST_TURNOVER',
    turnoverLimitPercent: '0.25',
    standardTicketCap: '5000000',
    maxDeviationCap: '10000000',
    standardCapMode: 'MIN_OF_BOTH',
    maxDeviationMode: 'FIXED',
    sanctionCapEnabled: false,
    camRecommendedCapEnabled: false,
  })
}

function limitSizingParameterOptions(): { value: string; label: string }[] {
  const seen = new Set<string>()
  const out: { value: string; label: string }[] = []
  for (const source of SCORECARD_SOURCES) {
    for (const p of source.parameters) {
      if (p.type !== 'number' || seen.has(p.value)) continue
      // Skip computed SCF outputs — those are produced by limit sizing, not inputs.
      if (p.value.startsWith('SCF_')) continue
      seen.add(p.value)
      out.push({ value: p.value, label: `${p.label} (${source.label})` })
    }
  }
  return out.sort((a, b) => a.label.localeCompare(b.label))
}

function parseLimitSizingForms(raw: unknown): LimitSizingFormRow[] {
  const toForm = (ls: Record<string, unknown>): LimitSizingFormRow => {
    const modeRaw = String(ls.standardCapMode ?? 'MIN_OF_BOTH').toUpperCase()
    const standardCapMode =
      modeRaw === 'TURNOVER_PERCENT' || modeRaw === 'FIXED' ? modeRaw : 'MIN_OF_BOTH'
    const maxModeRaw = String(ls.maxDeviationMode ?? 'FIXED').toUpperCase()
    const maxDeviationMode = maxModeRaw === 'TURNOVER_PERCENT' ? 'TURNOVER_PERCENT' : 'FIXED'
    const dependsOnRaw = ls.dependsOn
    const dependsOn =
      dependsOnRaw &&
      typeof dependsOnRaw === 'object' &&
      Array.isArray((dependsOnRaw as { conditions?: unknown }).conditions) &&
      ((dependsOnRaw as { conditions: unknown[] }).conditions?.length ?? 0) > 0
        ? (dependsOnRaw as HardRuleRow['dependsOn'])
        : undefined
    return newLimitSizingRow({
      turnoverParameter: String(ls.turnoverParameter ?? 'ANNUAL_GST_TURNOVER'),
      turnoverLimitPercent: ls.turnoverLimitPercent != null ? String(ls.turnoverLimitPercent) : '',
      standardTicketCap: ls.standardTicketCap != null ? String(ls.standardTicketCap) : '',
      maxDeviationCap: ls.maxDeviationCap != null ? String(ls.maxDeviationCap) : '',
      standardCapMode,
      maxDeviationMode,
      maxDeviationPercent: ls.maxDeviationPercent != null ? String(ls.maxDeviationPercent) : '',
      dependsOn,
      sanctionCapEnabled: ls.sanctionCapEnabled === true,
      camRecommendedCapEnabled: ls.camRecommendedCapEnabled === true,
    })
  }
  if (Array.isArray(raw)) {
    return (raw as Record<string, unknown>[])
      .filter((row) => row && typeof row === 'object' && row.enabled !== false)
      .map(toForm)
  }
  if (raw && typeof raw === 'object' && (raw as Record<string, unknown>).enabled === true) {
    return [toForm(raw as Record<string, unknown>)]
  }
  return []
}

function limitSizingRowComplete(r: LimitSizingFormRow): boolean {
  if (r.standardCapMode === 'FIXED') {
    if (!r.standardTicketCap.trim()) return false
  } else if (r.standardCapMode === 'TURNOVER_PERCENT') {
    if (!r.turnoverLimitPercent.trim()) return false
  } else if (!r.turnoverLimitPercent.trim() || !r.standardTicketCap.trim()) {
    return false
  }
  if (r.maxDeviationMode === 'TURNOVER_PERCENT') {
    return Boolean(r.maxDeviationPercent.trim())
  }
  return Boolean(r.maxDeviationCap.trim())
}

function toLimitSizingPayload(rows: LimitSizingFormRow[]): LimitSizingRow | LimitSizingRow[] | undefined {
  const completed = rows
    .filter(limitSizingRowComplete)
    .map(
      (r): LimitSizingRow => ({
        enabled: true,
        turnoverParameter: r.turnoverParameter.trim() || 'ANNUAL_GST_TURNOVER',
        turnoverLimitPercent: r.turnoverLimitPercent.trim() ? Number(r.turnoverLimitPercent) : undefined,
        standardTicketCap: r.standardTicketCap.trim() ? Number(r.standardTicketCap) : undefined,
        maxDeviationCap: r.maxDeviationCap.trim() ? Number(r.maxDeviationCap) : undefined,
        standardCapMode: r.standardCapMode,
        maxDeviationMode: r.maxDeviationMode,
        maxDeviationPercent: r.maxDeviationPercent.trim() ? Number(r.maxDeviationPercent) : undefined,
        ...(r.dependsOn?.conditions?.length ? { dependsOn: r.dependsOn } : {}),
        sanctionCapEnabled: r.sanctionCapEnabled,
        camRecommendedCapEnabled: r.camRecommendedCapEnabled,
      }),
    )
  if (completed.length === 0) return undefined
  if (completed.length === 1) return completed[0]
  return completed
}


export function UnderwritingRulesPage() {
  const [rows, setRows] = useState<UnderwritingRuleSetResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UnderwritingRuleSetResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [listSearch, setListSearch] = useState('')

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [minTenure, setMinTenure] = useState('')
  const [maxTenure, setMaxTenure] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [priority, setPriority] = useState('50')
  const [minBureau, setMinBureau] = useState('650')
  const [maxLoan, setMaxLoan] = useState('')
  const [requireKyc, setRequireKyc] = useState(true)
  const [decision, setDecision] = useState<(typeof DECISIONS)[number]>('MANUAL_REVIEW')
  const [reasonsText, setReasonsText] = useState('')
  const [scorecardRows, setScorecardRows] = useState<ScoreRow[]>([])
  const [hardRules, setHardRules] = useState<HardRuleRow[]>([])
  const [parameterDefs, setParameterDefs] = useState<Record<string, ScorecardParameterDef>>({})
  const [limitSizingEnabled, setLimitSizingEnabled] = useState(false)
  const [limitSizingRows, setLimitSizingRows] = useState<LimitSizingFormRow[]>([newLimitSizingRow()])
  const [formulaRuleId, setFormulaRuleId] = useState<string | null>(null)
  const [dependencyRuleId, setDependencyRuleId] = useState<string | null>(null)
  const [limitSizingDepKey, setLimitSizingDepKey] = useState<string | null>(null)
  const [expandedHardRuleIds, setExpandedHardRuleIds] = useState<Set<string>>(() => new Set())
  const [expandedScoreRowIds, setExpandedScoreRowIds] = useState<Set<string>>(() => new Set())
  const [mapOpen, setMapOpen] = useState(false)

  const sourceOptions = useMemo(
    () => scorecardSourceOptionsForLoanProduct(loanProduct),
    [loanProduct],
  )
  const turnoverParameterOptions = useMemo(() => limitSizingParameterOptions(), [])
  const isScfInvoiceProduct = loanProduct === SCF_INVOICE_PRODUCT

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const rules = await listUnderwritingRules()
      setRows(rules)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load
    void load()
  }, [load])

  const filteredRows = useMemo(() => {
    const items = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return items
    return items.filter((r) => {
      const borrowerLabel = (BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType).toLowerCase()
      const productLabel = loanProductLabel(r.loanProduct).toLowerCase()
      return (
        r.name.toLowerCase().includes(q)
        || r.borrowerType.toLowerCase().includes(q)
        || borrowerLabel.includes(q)
        || r.loanProduct.toLowerCase().includes(q)
        || productLabel.includes(q)
        || String(r.priority).includes(q)
      )
    })
  }, [rows, listSearch])

  function applyRule(r: UnderwritingRuleSetResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    setMinTenure(r.minTenureMonths != null ? String(r.minTenureMonths) : '')
    setMaxTenure(r.maxTenureMonths != null ? String(r.maxTenureMonths) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setPriority(String(r.priority))
    const j = r.rulesJson || {}
    setMinBureau(j.minBureauScore != null ? String(j.minBureauScore) : '650')
    setMaxLoan(j.maxLoanAmount != null ? String(j.maxLoanAmount) : '')
    setRequireKyc(j.requireKycSuccess !== false)
    const d = typeof j.decision === 'string' ? j.decision.toUpperCase() : 'MANUAL_REVIEW'
    setDecision(DECISIONS.includes(d as (typeof DECISIONS)[number]) ? (d as (typeof DECISIONS)[number]) : 'MANUAL_REVIEW')
    const rs = j.reasons
    setReasonsText(Array.isArray(rs) ? rs.map(String).join('\n') : '')
    const sc = (j as { scorecardRules?: unknown }).scorecardRules
    const defsRaw = (j as { parameterDefs?: Record<string, ScorecardParameterDef> }).parameterDefs
    setParameterDefs(defsRaw && typeof defsRaw === 'object' ? defsRaw : {})
    if (Array.isArray(sc) && sc.length > 0) {
      setScorecardRows(
        (sc as Record<string, unknown>[]).map((row) => ({
          id: `us-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          parameter: String(row.parameter ?? 'OBLIGATION_RATIO'),
          weight: String(row.weight ?? 10),
          mode: (String(row.mode ?? 'GTE').toUpperCase() === 'LTE' ? 'LTE' : 'GTE') as 'GTE' | 'LTE',
          approveAt: row.approveAt != null ? String(row.approveAt) : '',
          manualAt: row.manualAt != null ? String(row.manualAt) : '',
        })),
      )
    } else {
      setScorecardRows([])
    }
    setExpandedScoreRowIds(new Set())
    const hard = (j as { hardRules?: unknown }).hardRules
    if (Array.isArray(hard) && hard.length) {
      setHardRules(
        (hard as Record<string, unknown>[]).map((row, idx) => ({
          id: String(row.id ?? `hard-${idx}`),
          parameter: String(row.parameter ?? 'BUREAU_SCORE'),
          source: String(row.source ?? 'BUREAU'),
          condition: String(row.condition ?? 'LT:650'),
          decision: String(row.decision) === 'MANUAL_REVIEW' ? 'MANUAL_REVIEW' : 'REJECT',
          message: row.message != null ? String(row.message) : undefined,
          dependsOn: row.dependsOn && typeof row.dependsOn === 'object' ? (row.dependsOn as HardRuleRow['dependsOn']) : undefined,
          formula:
            typeof row.parameter === 'string' && defsRaw && typeof defsRaw === 'object'
              ? defsRaw[String(row.parameter)]?.formula
              : undefined,
        })),
      )
    } else {
      setHardRules([])
    }
    setExpandedHardRuleIds(new Set())
    const ls = (j as { limitSizing?: unknown }).limitSizing
    const parsed = parseLimitSizingForms(ls)
    if (parsed.length > 0) {
      setLimitSizingEnabled(true)
      setLimitSizingRows(parsed)
    } else {
      setLimitSizingEnabled(false)
      setLimitSizingRows([newLimitSizingRow()])
    }
    setActionError(null)
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setName('New rule set')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
    setMinAmount('')
    setMaxAmount('')
    setMinTenure('')
    setMaxTenure('')
    setGeoState('')
    setGeoCity('')
    setPriority('50')
    setMinBureau('650')
    setMaxLoan('')
    setRequireKyc(true)
    setDecision('MANUAL_REVIEW')
    setReasonsText('Policy check')
    setScorecardRows([])
    const initialHardRule = newHardRule()
    setHardRules([initialHardRule])
    setExpandedScoreRowIds(new Set())
    setExpandedHardRuleIds(new Set(initialHardRule.id ? [initialHardRule.id] : []))
    setParameterDefs({})
    setLimitSizingEnabled(false)
    setLimitSizingRows([newLimitSizingRow()])
    setActionError(null)
  }

  function buildRequest(): UnderwritingRuleSetRequest {
    const scRules = scorecardRows
      .filter((r) => r.parameter && r.weight.trim() && (r.approveAt.trim() || r.manualAt.trim()))
      .map((r) => {
        const w = Number.parseInt(r.weight, 10)
        return {
          parameter: r.parameter,
          weight: Number.isNaN(w) ? 0 : w,
          mode: r.mode,
          approveAt: Number(r.approveAt) || 0,
          manualAt: Number(r.manualAt) || 0,
        }
      })
    const reasons = reasonsText
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    const geo: Record<string, unknown> | null =
      geoState.trim() || geoCity.trim()
        ? { ...(geoState.trim() ? { state: geoState.trim() } : {}), ...(geoCity.trim() ? { city: geoCity.trim() } : {}) }
        : null
    const limitSizing = limitSizingEnabled ? toLimitSizingPayload(limitSizingRows) : undefined
    return {
      name: name.trim() || 'Rule set',
      borrowerType,
      loanProduct: loanProduct.trim(),
      minAmount: minAmount.trim() ? Number(minAmount) : null,
      maxAmount: maxAmount.trim() ? Number(maxAmount) : null,
      geography: geo && Object.keys(geo).length ? geo : null,
      minTenureMonths: minTenure.trim() ? Number.parseInt(minTenure, 10) : null,
      maxTenureMonths: maxTenure.trim() ? Number.parseInt(maxTenure, 10) : null,
      priority: Number.parseInt(priority, 10) || 0,
      rulesJson: {
        minBureauScore: minBureau.trim() ? Number.parseInt(minBureau, 10) : null,
        maxLoanAmount: maxLoan.trim() ? Number(maxLoan) : null,
        requireKycSuccess: requireKyc,
        decision,
        reasons,
        hardRules: hardRules.map((h) => ({
          ...(h.id ? { id: h.id } : {}),
          parameter: h.parameter,
          source: h.source,
          condition: h.condition,
          decision: h.decision,
          ...(h.message?.trim() ? { message: h.message.trim() } : {}),
          ...(h.dependsOn?.conditions?.length ? { dependsOn: h.dependsOn } : {}),
        })),
        ...(Object.keys(parameterDefs).length ? { parameterDefs } : {}),
        ...(limitSizing ? { limitSizing } : {}),
        scorecardRules: scRules,
      },
    }
  }

  async function onSave() {
    setActionError(null)
    setSaving(true)
    try {
      const body = buildRequest()
      if (isCreating) {
        const c = await createUnderwritingRule(body)
        setRows((prev) => (prev ? [c, ...prev] : [c]))
        setIsCreating(false)
        applyRule(c)
      } else if (selected) {
        const u = await updateUnderwritingRule(selected.id, body)
        setRows((prev) => (prev ? prev.map((x) => (x.id === u.id ? u : x)) : [u]))
        applyRule(u)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const activeFormulaRule = hardRules.find((r) => r.id === formulaRuleId) ?? null
  const activeDependencyRule = hardRules.find((r) => r.id === dependencyRuleId) ?? null
  const activeLimitSizingDep = limitSizingRows.find((r) => r.key === limitSizingDepKey) ?? null

  async function onDelete() {
    if (!selected || selected.active) return
    if (!globalThis.confirm('Delete this inactive rule set?')) return
    setActionError(null)
    try {
      await deleteUnderwritingRule(selected.id)
      setRows((prev) => (prev ? prev.filter((x) => x.id !== selected.id) : []))
      setSelected(null)
      setIsCreating(false)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Delete failed')
    }
  }

  async function onActivate() {
    if (!selected) return
    setToggling(true)
    try {
      await activateUnderwritingRule(selected.id)
      const fresh = await listUnderwritingRules()
      setRows(fresh)
      const u = fresh.find((x) => x.id === selected.id)
      if (u) applyRule(u)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Activate failed')
    } finally {
      setToggling(false)
    }
  }

  async function onDeactivate() {
    if (!selected) return
    setToggling(true)
    try {
      await deactivateUnderwritingRule(selected.id)
      const fresh = await listUnderwritingRules()
      setRows(fresh)
      const u = fresh.find((x) => x.id === selected.id)
      if (u) applyRule(u)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Deactivate failed')
    } finally {
      setToggling(false)
    }
  }

  const showForm = selected !== null || isCreating

  return (
    <div>
      <PageHeader
        title="Live Underwriting Rules"
        description="Current LOS production configuration. Multiple active rules may match the same application; each is evaluated and results are aggregated (any reject → reject; any manual review → manual review; all approve → approve). Higher priority is still used for ordering."
      />
      <AdministrationWorkspaceNav />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Rule sets"
            count={filteredRows.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search rule sets…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                New rule set
              </button>
            }
            empty={
              filteredRows.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? 'No rule sets yet.' : 'No rule sets match your search.'}
                </div>
              ) : undefined
            }
          >
            {filteredRows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => applyRule(r)}
                avatar={r.name}
                title={r.name}
                subtitle={`Priority ${r.priority}`}
                meta={
                  r.active ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                }
                tags={
                  <>
                    <span className="bt-tag">{BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType}</span>
                    <span className="bt-tag">{loanProductLabel(r.loanProduct)}</span>
                  </>
                }
              />
            ))}
          </MasterListPanel>
          <div>
            {showForm ? (
              <DetailPanel
                title={isCreating ? 'New rule set' : name}
                description="Define match criteria and decision rules. Multiple active sets may apply; results are aggregated."
                badge={
                  !isCreating && selected ? (
                    selected.active ? (
                      <span className="bt-badge bt-badge-green">Active</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">Inactive</span>
                    )
                  ) : undefined
                }
              >
                {actionError ? (
                  <p className="bt-alert bt-alert-warning mb-4">{actionError}</p>
                ) : null}
                <DetailSection title="Match criteria">
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="sm:col-span-2 text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Name</span>
                    <input
                      className="bt-input w-full"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Borrower type</span>
                    <select
                      className="w-full rounded border border-slate-300 bg-white px-2 py-1.5"
                      value={borrowerType}
                      onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}
                    >
                      {BORROWER_TYPES.map((b) => (
                        <option key={b} value={b}>
                          {BORROWER_TYPE_LABELS[b]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Loan product</span>
                    <select
                      className="w-full rounded border border-slate-300 bg-white px-2 py-1.5"
                      value={loanProduct}
                      onChange={(e) => setLoanProduct(e.target.value)}
                    >
                      {LOAN_PRODUCT_CODES.map((c) => (
                        <option key={c} value={c}>
                          {LOAN_PRODUCT_LABELS[c]}
                        </option>
                      ))}
                      {loanProduct && !isLoanProductCode(loanProduct) ? (
                        <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy)</option>
                      ) : null}
                    </select>
                  </label>
                  <label className="text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-0.5 block text-xs text-slate-500">Min amount</span>
                    <AmountInputSplit
                      value={minAmount}
                      onChange={setMinAmount}
                      aria-label="Minimum amount"
                    />
                  </label>
                  <label className="text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-0.5 block text-xs text-slate-500">Max amount</span>
                    <AmountInputSplit
                      value={maxAmount}
                      onChange={setMaxAmount}
                      aria-label="Maximum amount"
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Min tenure (months)</span>
                    <input
                      className="bt-input w-full"
                      value={minTenure}
                      onChange={(e) => setMinTenure(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Max tenure (months)</span>
                    <input
                      className="bt-input w-full"
                      value={maxTenure}
                      onChange={(e) => setMaxTenure(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Geography — state (optional)</span>
                    <input
                      className="bt-input w-full"
                      value={geoState}
                      onChange={(e) => setGeoState(e.target.value)}
                      placeholder="e.g. KA"
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Geography — city (optional)</span>
                    <input
                      className="bt-input w-full"
                      value={geoCity}
                      onChange={(e) => setGeoCity(e.target.value)}
                    />
                  </label>
                  <label className="text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-0.5 block text-xs text-slate-500">Priority (higher = first)</span>
                    <input
                      className="w-full max-w-xs rounded border border-slate-300 px-2 py-1.5"
                      value={priority}
                      onChange={(e) => setPriority(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                </div>
                </DetailSection>

                <DetailSection title="Policy rules">
                <div className="mb-4 rounded-lg border border-sky-200 bg-sky-50/80 p-3 text-sm text-slate-800">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium text-sky-950">Limit policy (optional)</p>
                    <label className="flex items-center gap-2 text-xs text-slate-700">
                      <input
                        type="checkbox"
                        checked={limitSizingEnabled}
                        onChange={(e) => {
                          const on = e.target.checked
                          setLimitSizingEnabled(on)
                          if (on && limitSizingRows.length === 0) {
                            setLimitSizingRows([
                              isScfInvoiceProduct ? scfSuggestedLimitSizingRow() : newLimitSizingRow(),
                            ])
                          }
                        }}
                      />
                      Enable limit policy
                    </label>
                  </div>
                  <p className="mt-1 text-xs text-slate-600">
                    Answers: <strong>how much funding is this case eligible for?</strong> It calculates a{' '}
                    <em>standard limit</em> and an <em>absolute ceiling</em>. Hard rules then decide pass / review /
                    reject using those amounts — this block itself does not reject.
                    {isScfInvoiceProduct ? (
                      <>
                        {' '}
                        SCF starter: 25% of GST turnover, standard ₹50L, ceiling ₹1Cr.
                      </>
                    ) : null}
                  </p>
                  {limitSizingEnabled ? (
                    <div className="mt-3 space-y-3">
                      {isScfInvoiceProduct ? (
                        <button
                          type="button"
                          className="text-xs font-medium text-sky-800 underline"
                          onClick={() =>
                            setLimitSizingRows((prev) =>
                              prev.length === 1 && !prev[0]?.turnoverLimitPercent.trim()
                                ? [scfSuggestedLimitSizingRow()]
                                : [...prev, scfSuggestedLimitSizingRow()],
                            )
                          }
                        >
                          Apply SCF starter (25% GST · ₹50L · ₹1Cr)
                        </button>
                      ) : null}
                      {limitSizingRows.map((row, idx) => {
                        const paramOptions = (() => {
                          const opts = [...turnoverParameterOptions]
                          if (
                            row.turnoverParameter &&
                            !opts.some((o) => o.value === row.turnoverParameter)
                          ) {
                            opts.unshift({
                              value: row.turnoverParameter,
                              label: `${row.turnoverParameter} (current)`,
                            })
                          }
                          return opts
                        })()
                        return (
                          <div
                            key={row.key}
                            className="rounded border border-sky-100 bg-white/70 p-3"
                          >
                            <div className="mb-2 flex items-center justify-between gap-2">
                              <p className="text-xs font-medium text-slate-700">
                                Limit policy{limitSizingRows.length > 1 ? ` ${idx + 1}` : ''}
                              </p>
                              {limitSizingRows.length > 1 ? (
                                <button
                                  type="button"
                                  className="text-xs text-rose-700"
                                  onClick={() =>
                                    setLimitSizingRows((prev) => prev.filter((_, i) => i !== idx))
                                  }
                                >
                                  Remove
                                </button>
                              ) : null}
                            </div>
                            <div className="mb-3 rounded-md border border-sky-100 bg-sky-50/80 px-3 py-2 text-[11px] text-slate-700">
                              <p className="font-semibold text-sky-950">How this policy will work</p>
                              <ol className="mt-1 list-decimal space-y-0.5 pl-4">
                                {limitPolicyPlainEnglish(row).map((line) => (
                                  <li key={line}>{line.replace(/^\d+\.\s*/, '')}</li>
                                ))}
                              </ol>
                              <p className="mt-2 text-[10px] text-slate-500">
                                Example: GST ₹2 Cr, 25%, ticket ₹50L → standard = ₹50L. Requested ₹60L → special
                                deviation until the ₹1 Cr ceiling.
                              </p>
                            </div>
                            <div className="grid gap-2 sm:grid-cols-2">
                              <label className="text-xs text-slate-700 sm:col-span-2">
                                Step 1 — How to set the standard eligible limit
                                <select
                                  className="mt-0.5 bt-input w-full"
                                  value={row.standardCapMode}
                                  onChange={(e) =>
                                    setLimitSizingRows((prev) =>
                                      prev.map((x, i) =>
                                        i === idx
                                          ? {
                                              ...x,
                                              standardCapMode: e.target.value as LimitSizingFormRow['standardCapMode'],
                                            }
                                          : x,
                                      ),
                                    )
                                  }
                                >
                                  <option value="MIN_OF_BOTH">
                                    Smaller of: turnover % OR fixed ₹ cap (recommended)
                                  </option>
                                  <option value="TURNOVER_PERCENT">Only turnover % (no fixed ₹ cap)</option>
                                  <option value="FIXED">Only fixed ₹ amount</option>
                                </select>
                              </label>
                              <label className="text-xs text-slate-700">
                                Measure from (turnover figure)
                                <select
                                  className="mt-0.5 bt-input w-full"
                                  value={row.turnoverParameter}
                                  onChange={(e) =>
                                    setLimitSizingRows((prev) =>
                                      prev.map((x, i) =>
                                        i === idx
                                          ? { ...x, turnoverParameter: e.target.value }
                                          : x,
                                      ),
                                    )
                                  }
                                >
                                  {paramOptions.map((o) => (
                                    <option key={o.value} value={o.value}>
                                      {o.label}
                                    </option>
                                  ))}
                                </select>
                              </label>
                              {row.standardCapMode !== 'FIXED' ? (
                                <label className="text-xs text-slate-700">
                                  Turnover share (0.25 = 25%
                                  {row.turnoverLimitPercent.trim()
                                    ? ` → ${percentLabel(row.turnoverLimitPercent)}`
                                    : ''}
                                  )
                                  <input
                                    className="mt-0.5 bt-input w-full"
                                    inputMode="decimal"
                                    placeholder="0.25"
                                    value={row.turnoverLimitPercent}
                                    onChange={(e) =>
                                      setLimitSizingRows((prev) =>
                                        prev.map((x, i) =>
                                          i === idx
                                            ? { ...x, turnoverLimitPercent: e.target.value }
                                            : x,
                                        ),
                                      )
                                    }
                                  />
                                </label>
                              ) : null}
                              {row.standardCapMode !== 'TURNOVER_PERCENT' ? (
                                <label className="text-xs text-slate-700 sm:col-span-2">
                                  Fixed standard cap (₹) — typical 5000000
                                  <div className="mt-0.5">
                                    <AmountInputSplit
                                      value={row.standardTicketCap}
                                      onChange={(v) =>
                                        setLimitSizingRows((prev) =>
                                          prev.map((x, i) =>
                                            i === idx ? { ...x, standardTicketCap: v } : x,
                                          ),
                                        )
                                      }
                                      aria-label="Fixed standard cap"
                                    />
                                  </div>
                                </label>
                              ) : null}
                              <label className="text-xs text-slate-700 sm:col-span-2">
                                Step 2 — Absolute ceiling (never above this)
                                <select
                                  className="mt-0.5 bt-input w-full"
                                  value={row.maxDeviationMode}
                                  onChange={(e) =>
                                    setLimitSizingRows((prev) =>
                                      prev.map((x, i) =>
                                        i === idx
                                          ? {
                                              ...x,
                                              maxDeviationMode: e.target
                                                .value as LimitSizingFormRow['maxDeviationMode'],
                                            }
                                          : x,
                                      ),
                                    )
                                  }
                                >
                                  <option value="FIXED">Fixed ₹ ceiling</option>
                                  <option value="TURNOVER_PERCENT">Ceiling as % of same turnover figure</option>
                                </select>
                              </label>
                              {row.maxDeviationMode === 'FIXED' ? (
                                <label className="text-xs text-slate-700 sm:col-span-2">
                                  Ceiling amount (₹) — typical 10000000
                                  <div className="mt-0.5">
                                    <AmountInputSplit
                                      value={row.maxDeviationCap}
                                      onChange={(v) =>
                                        setLimitSizingRows((prev) =>
                                          prev.map((x, i) =>
                                            i === idx ? { ...x, maxDeviationCap: v } : x,
                                          ),
                                        )
                                      }
                                      aria-label="Ceiling amount"
                                    />
                                  </div>
                                </label>
                              ) : (
                                <label className="text-xs text-slate-700">
                                  Ceiling share (0.5 = 50%
                                  {row.maxDeviationPercent.trim()
                                    ? ` → ${percentLabel(row.maxDeviationPercent)}`
                                    : ''}
                                  )
                                  <input
                                    className="mt-0.5 bt-input w-full"
                                    inputMode="decimal"
                                    placeholder="0.5"
                                    value={row.maxDeviationPercent}
                                    onChange={(e) =>
                                      setLimitSizingRows((prev) =>
                                        prev.map((x, i) =>
                                          i === idx
                                            ? { ...x, maxDeviationPercent: e.target.value }
                                            : x,
                                        ),
                                      )
                                    }
                                  />
                                </label>
                              )}
                              <div className="flex flex-wrap items-center gap-2 sm:col-span-2">
                                <button
                                  type="button"
                                  className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                  onClick={() => setLimitSizingDepKey(row.key)}
                                >
                                  Step 3 — Dependencies
                                  {(row.dependsOn?.conditions?.length ?? 0) > 0
                                    ? ` (${row.dependsOn!.conditions!.length})`
                                    : ' (none)'}
                                </button>
                                <span className="text-[11px] text-slate-500">
                                  Empty = always apply. Add conditions to apply only when they match.
                                </span>
                              </div>
                              <label className="flex items-center gap-2 text-xs text-slate-700 sm:col-span-2">
                                <input
                                  type="checkbox"
                                  checked={row.sanctionCapEnabled}
                                  onChange={(e) =>
                                    setLimitSizingRows((prev) =>
                                      prev.map((x, i) =>
                                        i === idx
                                          ? { ...x, sanctionCapEnabled: e.target.checked }
                                          : x,
                                      ),
                                    )
                                  }
                                />
                                Step 4 (optional) — Auto-reduce sanctioned amount to standard limit
                              </label>
                              <label className="flex items-center gap-2 text-xs text-slate-700 sm:col-span-2">
                                <input
                                  type="checkbox"
                                  checked={row.camRecommendedCapEnabled}
                                  onChange={(e) =>
                                    setLimitSizingRows((prev) =>
                                      prev.map((x, i) =>
                                        i === idx
                                          ? {
                                              ...x,
                                              camRecommendedCapEnabled: e.target.checked,
                                            }
                                          : x,
                                      ),
                                    )
                                  }
                                />
                                Step 4 (optional) — Auto-reduce CAM recommended amount to standard limit
                              </label>
                            </div>
                          </div>
                        )
                      })}
                      <button
                        type="button"
                        className="text-xs font-medium text-sky-800"
                        onClick={() =>
                          setLimitSizingRows((prev) => [
                            ...prev,
                            isScfInvoiceProduct
                              ? scfSuggestedLimitSizingRow()
                              : newLimitSizingRow(),
                          ])
                        }
                      >
                        + Add another limit policy
                      </button>
                      {limitSizingRows.length > 1 ? (
                        <p className="text-[11px] text-slate-500">
                          When multiple policies are saved, the first complete enabled row is used at
                          underwriting runtime (same as a single policy).
                        </p>
                      ) : null}
                    </div>
                  ) : null}
                </div>
                <div className="rounded border border-slate-200 bg-slate-50/80 p-3">
                  <p className="mb-2 text-xs font-medium text-slate-600">Policy (rulesJson)</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <label className="text-sm text-slate-700">
                      <span className="mb-0.5 block text-xs text-slate-500">Min bureau score</span>
                      <input
                        className="bt-input w-full"
                        value={minBureau}
                        onChange={(e) => setMinBureau(e.target.value.replace(/\D/g, ''))}
                      />
                    </label>
                    <label className="text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-0.5 block text-xs text-slate-500">Max loan amount (cap)</span>
                      <AmountInputSplit
                        value={maxLoan}
                        onChange={setMaxLoan}
                        aria-label="Max loan amount"
                      />
                    </label>
                    <label className="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
                      <input
                        type="checkbox"
                        checked={requireKyc}
                        onChange={(e) => setRequireKyc(e.target.checked)}
                      />
                      Require KYC PASS
                    </label>
                    <label className="text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-0.5 block text-xs text-slate-500">Decision (if policy checks pass)</span>
                      <select
                        className="w-full max-w-sm rounded border border-slate-300 bg-white px-2 py-1.5"
                        value={decision}
                        onChange={(e) => setDecision(e.target.value as (typeof DECISIONS)[number])}
                      >
                        {DECISIONS.map((d) => (
                          <option key={d} value={d}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-0.5 block text-xs text-slate-500">Reasons (one per line)</span>
                      <textarea
                        className="h-20 bt-input w-full font-mono text-xs"
                        value={reasonsText}
                        onChange={(e) => setReasonsText(e.target.value)}
                      />
                    </label>
                  </div>
                </div>
                <div className="rounded border border-rose-200 bg-rose-50/40 p-3">
                  <div className="mb-2">
                    <div>
                      <p className="text-xs font-medium text-slate-700">Hard rules</p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        Same design as Scorecards hard rules. Evaluated before scorecard weighting — force reject or
                        manual review.
                      </p>
                    </div>
                  </div>
                  {hardRules.length === 0 ? (
                    <p className="text-sm text-slate-500">No hard rules configured.</p>
                  ) : (
                    <div className="space-y-3">
                      {hardRules.map((rule, idx) => {
                        const pDef = paramDef(rule.source, rule.parameter)
                        const paramOptions = parametersForSourceWithCurrent(rule.source, rule.parameter)
                        const sources = [...sourceOptions]
                        const isComputed = rule.source === 'COMPUTED'
                        const dependencyCount = rule.dependsOn?.conditions?.length ?? 0
                        const ruleKey = rule.id || `hard-${idx}`
                        const expanded = expandedHardRuleIds.has(ruleKey)
                        if (rule.source && !sources.some((s) => s.value === rule.source)) {
                          sources.push({ value: rule.source, label: rule.source })
                        }
                        return (
                          <div
                            key={ruleKey}
                            className={[
                              'overflow-hidden rounded-xl border bg-white shadow-sm transition-shadow',
                              expanded
                                ? 'border-amber-300 shadow-md ring-1 ring-amber-100'
                                : 'border-slate-200 hover:border-slate-300',
                            ].join(' ')}
                          >
                            <button
                              type="button"
                              className="flex w-full items-center gap-3 p-3 text-left"
                              aria-expanded={expanded}
                              onClick={() =>
                                setExpandedHardRuleIds((prev) => {
                                  const next = new Set(prev)
                                  if (expanded) next.delete(ruleKey)
                                  else next.add(ruleKey)
                                  return next
                                })
                              }
                            >
                              <span
                                className={[
                                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-bold',
                                  expanded ? 'bg-amber-100 text-amber-900' : 'bg-slate-100 text-slate-600',
                                ].join(' ')}
                                aria-hidden="true"
                              >
                                {idx + 1}
                              </span>
                              <span className="min-w-0 flex-1">
                                <span className="block truncate text-sm font-semibold text-slate-900">
                                  {pDef?.label ?? rule.parameter}
                                </span>
                                <span className="mt-0.5 block truncate text-xs text-slate-500">
                                  {rule.source} · {rule.parameter}
                                  {rule.condition ? ` · ${rule.condition}` : ''}
                                  {dependencyCount > 0 ? ` · ${dependencyCount} dep.` : ''}
                                </span>
                              </span>
                              <span className="flex shrink-0 items-center gap-2">
                                <span
                                  className={`bt-badge ${rule.decision === 'REJECT' ? 'bt-badge-red' : 'bt-badge-amber'}`}
                                >
                                  {rule.decision === 'MANUAL_REVIEW' ? 'Manual review' : 'Reject'}
                                </span>
                                <span
                                  className={`text-slate-400 transition-transform ${expanded ? 'rotate-90' : ''}`}
                                  aria-hidden="true"
                                >
                                  ›
                                </span>
                              </span>
                            </button>
                            {expanded ? (
                            <div className="flex flex-wrap items-start gap-2 border-t border-slate-200 p-3">
                            <select
                              className="bt-input bt-input-sm"
                              value={rule.source}
                              aria-label="Hard rule source"
                              onChange={(e) => {
                                const source = e.target.value
                                const parameter = defaultParameterForSource(source)
                                setHardRules((prev) =>
                                  prev.map((x, i) =>
                                    i === idx
                                      ? {
                                          ...x,
                                          source,
                                          parameter,
                                          condition: defaultConditionForParam(paramDef(source, parameter)),
                                        }
                                      : x,
                                  ),
                                )
                              }}
                            >
                              {sources.map((s) => (
                                <option key={s.value} value={s.value}>
                                  {s.label}
                                </option>
                              ))}
                            </select>
                            {isComputed ? (
                              <div className="min-w-[14rem] rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                                <div className="mb-1 text-[11px] font-medium text-slate-500">Computed parameter</div>
                                <div className="font-mono text-xs text-slate-800">{rule.parameter || 'Unnamed computed parameter'}</div>
                                <button
                                  type="button"
                                  className="mt-2 rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                  onClick={() => setFormulaRuleId(rule.id ?? null)}
                                >
                                  Edit formula
                                </button>
                              </div>
                            ) : (
                              <select
                                className="bt-input bt-input-sm min-w-[12rem]"
                                value={rule.parameter}
                                aria-label="Hard rule parameter"
                                onChange={(e) => {
                                  const parameter = e.target.value
                                  setHardRules((prev) =>
                                    prev.map((x, i) =>
                                      i === idx
                                        ? {
                                            ...x,
                                            parameter,
                                            condition: defaultConditionForParam(paramDef(rule.source, parameter)),
                                          }
                                        : x,
                                    ),
                                  )
                                }}
                              >
                                {paramOptions.map((p) => (
                                  <option key={p.value} value={p.value}>
                                    {p.label}
                                  </option>
                                ))}
                              </select>
                            )}
                            <ScorecardConditionEditor
                              value={rule.condition}
                              onChange={(condition) =>
                                setHardRules((prev) => prev.map((x, i) => (i === idx ? { ...x, condition } : x)))
                              }
                              paramDef={pDef}
                              loanProduct={loanProduct}
                            />
                            <select
                              className="bt-input bt-input-sm"
                              value={rule.decision}
                              aria-label="Hard rule decision"
                              onChange={(e) =>
                                setHardRules((prev) =>
                                  prev.map((x, i) =>
                                    i === idx
                                      ? { ...x, decision: e.target.value as HardRuleRow['decision'] }
                                      : x,
                                  ),
                                )
                              }
                            >
                              <option value="REJECT">Reject</option>
                              <option value="MANUAL_REVIEW">Manual review</option>
                            </select>
                            <input
                              className="bt-input bt-input-sm min-w-[10rem] flex-1"
                              value={rule.message ?? ''}
                              onChange={(e) =>
                                setHardRules((prev) =>
                                  prev.map((x, i) => (i === idx ? { ...x, message: e.target.value } : x)),
                                )
                              }
                              placeholder="Optional message"
                            />
                            <button
                              type="button"
                              className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                              onClick={() => setDependencyRuleId(rule.id ?? null)}
                            >
                              Dependencies{dependencyCount > 0 ? ` (${dependencyCount})` : ''}
                            </button>
                            <button
                              type="button"
                              className="bt-btn-icon text-rose-600"
                              aria-label="Remove hard rule"
                              onClick={() => setHardRules((prev) => prev.filter((_, i) => i !== idx))}
                            >
                              ×
                            </button>
                            </div>
                            ) : null}
                          </div>
                        )
                      })}
                    </div>
                  )}
                  <button
                    type="button"
                    className="bt-btn bt-btn-ghost bt-btn-sm mt-3"
                    onClick={() => {
                      const rule = newHardRule()
                      setHardRules((prev) => [...prev, rule])
                      if (rule.id) setExpandedHardRuleIds((prev) => new Set(prev).add(rule.id!))
                    }}
                  >
                    + Add hard rule
                  </button>
                </div>
                <div className="rounded border border-indigo-200 bg-indigo-50/50 p-3">
                  <p className="mb-2 text-xs font-medium text-slate-700">Scorecard (optional, overrides policy decision if non-empty)</p>
                  <p className="mb-2 text-xs text-slate-500">
                    Per row: <strong>parameter</strong> from effective scorecard, <strong>weight</strong>, <strong>mode</strong>{' '}
                    (GTE = higher is better, LTE = lower is better, e.g. ratio), and numeric{' '}
                    <strong>approve</strong> / <strong>manual</strong> thresholds. Highest weighted bucket (approve, manual, reject)
                    wins.
                  </p>
                  <div className="mb-2 space-y-2">
                    {scorecardRows.map((row, idx) => (
                      <div
                        key={row.id}
                        className={[
                          'overflow-hidden rounded-xl border bg-white shadow-sm transition-shadow',
                          expandedScoreRowIds.has(row.id)
                            ? 'border-indigo-300 shadow-md ring-1 ring-indigo-100'
                            : 'border-indigo-100 hover:border-indigo-200',
                        ].join(' ')}
                      >
                        <button
                          type="button"
                          className="flex w-full items-center gap-3 p-3 text-left"
                          aria-expanded={expandedScoreRowIds.has(row.id)}
                          onClick={() =>
                            setExpandedScoreRowIds((prev) => {
                              const next = new Set(prev)
                              if (next.has(row.id)) next.delete(row.id)
                              else next.add(row.id)
                              return next
                            })
                          }
                        >
                          <span
                            className={[
                              'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-bold',
                              expandedScoreRowIds.has(row.id)
                                ? 'bg-indigo-100 text-indigo-900'
                                : 'bg-slate-100 text-slate-600',
                            ].join(' ')}
                            aria-hidden="true"
                          >
                            {idx + 1}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-semibold text-slate-900">
                              {SCORECARD_PARAMS.find((option) => option.value === row.parameter)?.label ??
                                row.parameter}
                            </span>
                            <span className="mt-0.5 block truncate text-xs text-slate-500">
                              {row.parameter} · {row.mode}
                            </span>
                          </span>
                          <span className="hidden shrink-0 items-center gap-2 sm:flex">
                            <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-center">
                              <span className="block text-[9px] font-semibold uppercase tracking-wide text-slate-500">
                                Weight
                              </span>
                              <span className="block text-sm font-semibold tabular-nums text-slate-900">
                                {row.weight || '—'}
                              </span>
                            </span>
                            <span className="rounded-md border border-emerald-200 bg-emerald-50/80 px-2.5 py-1 text-center">
                              <span className="block text-[9px] font-semibold uppercase tracking-wide text-emerald-700/80">
                                Approve
                              </span>
                              <span className="block text-sm font-semibold tabular-nums text-emerald-900">
                                {row.approveAt || '—'}
                              </span>
                            </span>
                          </span>
                          <span
                            className={`shrink-0 text-slate-400 transition-transform ${expandedScoreRowIds.has(row.id) ? 'rotate-90' : ''}`}
                            aria-hidden="true"
                          >
                            ›
                          </span>
                        </button>
                        {expandedScoreRowIds.has(row.id) ? (
                        <div className="grid gap-1 border-t border-indigo-100 p-2 sm:grid-cols-2 lg:grid-cols-5">
                        <label className="text-xs sm:col-span-2">
                          Parameter
                          <select
                            className="mt-0.5 w-full rounded border border-slate-300 bg-white px-1 py-1"
                            value={row.parameter}
                            onChange={(e) => {
                              const v = e.target.value
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, parameter: v } : x)))
                            }}
                          >
                            {SCORECARD_PARAMS.map((o) => (
                              <option key={o.value} value={o.value}>
                                {o.label}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="text-xs">
                          Weight
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.weight}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, weight: e.target.value } : x)))
                            }
                          />
                        </label>
                        <label className="text-xs">
                          Mode
                          <select
                            className="mt-0.5 w-full rounded border border-slate-300 bg-white px-1 py-1"
                            value={row.mode}
                            onChange={(e) =>
                              setScorecardRows((p) =>
                                p.map((x, i) =>
                                  i === idx ? { ...x, mode: e.target.value as 'GTE' | 'LTE' } : x,
                                ),
                              )
                            }
                          >
                            <option value="GTE">GTE (≥ = better)</option>
                            <option value="LTE">LTE (≤ = better)</option>
                          </select>
                        </label>
                        <label className="text-xs">
                          Approve at
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.approveAt}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, approveAt: e.target.value } : x)))
                            }
                          />
                        </label>
                        <label className="text-xs">
                          Manual at
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.manualAt}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, manualAt: e.target.value } : x)))
                            }
                          />
                        </label>
                        <div className="sm:col-span-2 flex items-end lg:col-span-1">
                          <button
                            type="button"
                            className="text-xs text-rose-700 underline"
                            onClick={() => setScorecardRows((p) => p.filter((_, i) => i !== idx))}
                          >
                            Remove row
                          </button>
                        </div>
                        </div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="rounded border border-indigo-400 bg-indigo-50 px-2 py-1 text-xs text-indigo-900"
                    onClick={() =>
                      {
                        const row: ScoreRow = {
                          id: `us-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                          parameter: 'OBLIGATION_RATIO',
                          weight: '20',
                          mode: 'LTE',
                          approveAt: '0.4',
                          manualAt: '0.55',
                        }
                        setScorecardRows((p) => [...p, row])
                        setExpandedScoreRowIds((prev) => new Set(prev).add(row.id))
                      }
                    }
                  >
                    + Add scorecard row
                  </button>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="bt-btn bt-btn-ghost"
                    onClick={() => setMapOpen(true)}
                  >
                    View mapping
                  </button>
                  <button
                    type="button"
                    onClick={() => void onSave()}
                    disabled={saving}
                    className="bt-btn bt-btn-primary disabled:opacity-50"
                  >
                    {saving ? 'Saving…' : isCreating ? 'Create' : 'Save'}
                  </button>
                  {isCreating ? (
                    <button
                      type="button"
                      onClick={() => {
                        setIsCreating(false)
                        setSelected(null)
                      }}
                      className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
                    >
                      Cancel
                    </button>
                  ) : null}
                  {!isCreating && selected && (
                    <>
                      {!selected.active ? (
                        <button
                          type="button"
                          disabled={toggling}
                          onClick={() => void onActivate()}
                          className="rounded-md border border-emerald-600 bg-emerald-50 px-3 py-1.5 text-sm text-emerald-900"
                        >
                          Activate
                        </button>
                      ) : (
                        <button
                          type="button"
                          disabled={toggling}
                          onClick={() => void onDeactivate()}
                          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
                        >
                          Deactivate
                        </button>
                      )}
                      {!selected.active ? (
                        <button
                          type="button"
                          onClick={() => void onDelete()}
                          className="rounded-md border border-rose-300 bg-rose-50 px-3 py-1.5 text-sm text-rose-900"
                        >
                          Delete
                        </button>
                      ) : null}
                    </>
                  )}
                </div>
                {!isCreating && selected ? (
                  <p className="mt-3 text-xs text-slate-500">Id: {selected.id}</p>
                ) : null}
                </DetailSection>
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a rule set"
                description="Choose a rule set from the list to edit its match criteria and policy rules, or create a new one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    New rule set
                  </button>
                }
              />
            )}
          </div>
        </MasterDetailLayout>
      )}
      <EditorModal
        open={mapOpen}
        title="Rule set mapping"
        description="Visual, read-only view of match scope, hard rules, scoring rows, and how decisions flow."
        onClose={() => setMapOpen(false)}
      >
        <UnderwritingPolicyMapView
          kind="rule"
          scope={{
            name,
            borrowerType,
            loanProduct,
            priority,
            active: selected?.active,
            minAmount: minAmount || null,
            maxAmount: maxAmount || null,
            geography: [geoState, geoCity].filter(Boolean).join(' / ') || undefined,
          }}
          hardRules={hardRules.map((h) => ({
            ...h,
            formula: h.formula ?? (h.parameter ? parameterDefs[h.parameter]?.formula : undefined),
          }))}
          legacyPolicy={{
            minBureau,
            maxLoan,
            requireKyc,
            decision,
            scorecardRows,
          }}
        />
      </EditorModal>
      <EditorModal
        open={activeFormulaRule != null}
        title="Edit Computed Formula"
        description="Define the formula that produces this advanced underwriting parameter."
        onClose={() => setFormulaRuleId(null)}
      >
        {activeFormulaRule ? (
          <div className="space-y-4">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Computed parameter code</span>
              <input
                className="bt-input w-full font-mono text-sm"
                value={activeFormulaRule.parameter}
                onChange={(e) => {
                  const parameter = e.target.value.trim()
                  setHardRules((prev) => prev.map((r) => (r.id === activeFormulaRule.id ? { ...r, parameter } : r)))
                }}
                placeholder="e.g. PAT_TURNOVER_RATIO"
              />
            </label>
            <FormulaEditor
              formula={
                activeFormulaRule.formula ?? parameterDefs[activeFormulaRule.parameter]?.formula ?? {
                  expression: '',
                  operands: [
                    { parameter: '', source: 'BUREAU' },
                    { parameter: '', source: 'BUREAU' },
                  ],
                }
              }
              onChange={(formula) => {
                setHardRules((prev) => prev.map((r) => (r.id === activeFormulaRule.id ? { ...r, formula } : r)))
                if (activeFormulaRule.parameter.trim()) {
                  setParameterDefs((prev) => ({
                    ...prev,
                    [activeFormulaRule.parameter.trim()]: { inputType: 'formula', formula },
                  }))
                }
              }}
              loanProduct={loanProduct}
            />
          </div>
        ) : null}
      </EditorModal>
      <EditorModal
        open={activeDependencyRule != null}
        title="Dependency Conditions"
        description="Only run this hard rule when the prerequisite conditions below match."
        onClose={() => setDependencyRuleId(null)}
      >
        {activeDependencyRule ? (
          <DependencyConditionsEditor
            value={activeDependencyRule.dependsOn}
            onChange={(dependsOn) =>
              setHardRules((prev) => prev.map((r) => (r.id === activeDependencyRule.id ? { ...r, dependsOn } : r)))
            }
            loanProduct={loanProduct}
          />
        ) : null}
      </EditorModal>
      <EditorModal
        open={activeLimitSizingDep != null}
        title="Limit policy dependencies"
        description="Only apply this limit sizing policy when the prerequisite conditions below match."
        onClose={() => setLimitSizingDepKey(null)}
      >
        {activeLimitSizingDep ? (
          <DependencyConditionsEditor
            value={activeLimitSizingDep.dependsOn}
            onChange={(dependsOn) =>
              setLimitSizingRows((prev) =>
                prev.map((r) => (r.key === activeLimitSizingDep.key ? { ...r, dependsOn } : r)),
              )
            }
            loanProduct={loanProduct}
          />
        ) : null}
      </EditorModal>
    </div>
  )
}
