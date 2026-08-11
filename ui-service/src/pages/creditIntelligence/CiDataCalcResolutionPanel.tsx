import { useMemo, useState } from 'react'
import type { ReviewRuleBody } from '@/api/creditIntelligence'
import { previewDataCalculation, proposeParameterDefinition } from '@/api/creditIntelligence'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function formatInrInput(n: string): string {
  const digits = n.replace(/[^\d]/g, '')
  if (!digits) return ''
  try {
    return Number(digits).toLocaleString('en-IN')
  } catch {
    return digits
  }
}

function parseAmount(raw: string): string | null {
  const digits = raw.replace(/[^\d]/g, '')
  return digits ? digits : null
}

export type DataCalcResolveKind =
  | 'THRESHOLD'
  | 'CLASSIFICATION'
  | 'CALCULATION'
  | 'ADJUSTMENT'
  | 'MANUAL'

/**
 * POLICY-DATA-RESOLUTION-UX-1 / POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 —
 * typed Resolve/Define/Configure forms for Data & Calculations.
 * Policy-scoped only. Does not mutate GACAT.
 */
export function CiDataCalcResolutionPanel({
  open,
  onClose,
  kind,
  parameterId,
  title,
  ruleId,
  documentId,
  busy,
  onResolve,
}: {
  open: boolean
  onClose: () => void
  kind: DataCalcResolveKind
  parameterId: string
  title: string
  ruleId: string
  documentId?: string | null
  busy: boolean
  onResolve: (ruleId: string, body: ReviewRuleBody) => Promise<void>
}) {
  const [amount, setAmount] = useState('')
  const [notes, setNotes] = useState('')
  const [method, setMethod] = useState('MANUAL_INSTITUTIONAL_CLASSIFICATION')
  const [multiple, setMultiple] = useState('10')
  const [manualLabel, setManualLabel] = useState(title)
  const [manualType, setManualType] = useState('Money')
  const [manualActor, setManualActor] = useState('Credit Analyst')
  const [captureStage, setCaptureStage] = useState('CAM_UNDERWRITING_REVIEW')
  const [describe, setDescribe] = useState('')
  const [proposal, setProposal] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [proposing, setProposing] = useState(false)
  const [periodMonths, setPeriodMonths] = useState('3')
  const [emiIdentification, setEmiIdentification] = useState('EXISTING_EMI_CLASSIFIER')
  const [bounceIdentification, setBounceIdentification] = useState('EXISTING_BOUNCE_RETURN_CLASSIFIER')
  const [excludeDuplicates, setExcludeDuplicates] = useState(true)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [previewBusy, setPreviewBusy] = useState(false)
  const [preview, setPreview] = useState<Record<string, unknown> | null>(null)

  const isEmiBounce =
    parameterId === 'banking.emi_bounce_count_3m' || title.toLowerCase().includes('emi bounce')

  const heading = useMemo(() => {
    switch (kind) {
      case 'THRESHOLD':
        return 'Define threshold'
      case 'CLASSIFICATION':
        return 'Define relationship'
      case 'CALCULATION':
        return 'Configure calculation'
      case 'ADJUSTMENT':
        return 'Configure policy adjustment'
      case 'MANUAL':
        return 'Use manual input'
      default:
        return 'Resolve'
    }
  }, [kind])

  if (!open) return null

  const save = async () => {
    setError(null)
    try {
      if (kind === 'THRESHOLD') {
        const amt = parseAmount(amount)
        if (!amt) {
          setError('Enter a positive amount in ₹')
          return
        }
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_THRESHOLD',
          dataItemId: parameterId,
          parameterId,
          amountInr: amt,
          notes: notes || undefined,
          reason: `Large credit threshold ₹${formatInrInput(amt)} (policy draft)`,
        })
      } else if (kind === 'CLASSIFICATION') {
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_CLASSIFICATION',
          dataItemId: parameterId,
          parameterId,
          identificationMethod: method,
          notes: notes || undefined,
          reason: 'Intercompany / merchant-group definition (policy draft)',
        })
      } else if (kind === 'CALCULATION') {
        if (!isEmiBounce) {
          setError(
            'No executable calculation binding exists for this item yet. Use Keep as policy requirement or Ignore for automation instead of Save.',
          )
          return
        }
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_CALCULATION',
          dataItemId: parameterId,
          parameterId,
          periodMonths: Number(periodMonths) || 3,
          emiIdentification,
          bounceIdentification,
          excludeDuplicates,
          confirmExecutable: true,
          saveMode: 'SAVE_EXECUTABLE',
          reason: `EMI Bounce Count configured — ${periodMonths}m · ${emiIdentification} + ${bounceIdentification}`,
        })
      } else if (kind === 'ADJUSTMENT') {
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_ADJUSTMENT',
          dataItemId: parameterId,
          parameterId,
          multiple: multiple || '10',
          reason: `ADB bulk exclusion ${multiple}× recorded (execution still needs configuration)`,
        })
      } else if (kind === 'MANUAL') {
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_MANUAL',
          dataItemId: parameterId,
          parameterId,
          manualInputLabel: manualLabel,
          manualInputType: manualType,
          requiredActor: manualActor,
          captureStage,
          reason: 'Manual input definition (policy draft)',
        })
      }
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save resolution')
    }
  }

  const runPreview = async () => {
    if (!documentId) {
      setError('Open a policy document to run calculation preview')
      return
    }
    setPreviewBusy(true)
    setError(null)
    try {
      const res = await previewDataCalculation(documentId, {
        dataItemId: parameterId || 'banking.emi_bounce_count_3m',
        parameterId: parameterId || 'banking.emi_bounce_count_3m',
        periodMonths: Number(periodMonths) || 3,
        emiIdentification,
        bounceIdentification,
        excludeDuplicates,
      })
      setPreview(asRecord(res.preview ?? res))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Preview failed')
    } finally {
      setPreviewBusy(false)
    }
  }

  const runPropose = async () => {
    if (!describe.trim()) return
    setProposing(true)
    setError(null)
    try {
      const p = await proposeParameterDefinition({
        term: title,
        description: describe,
      })
      setProposal(asRecord(p.proposal ?? p))
      const suggested = asRecord(p.proposal ?? p)
      const amt = suggested.amountInr ?? suggested.threshold ?? suggested.value
      if (amt != null && kind === 'THRESHOLD') setAmount(formatInrInput(String(amt)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Proposal failed')
    } finally {
      setProposing(false)
    }
  }

  const previewEmi = preview ? Number(preview.emiCandidates ?? 0) : null
  const previewMatched = preview ? Number(preview.matchedBouncedEmiEvents ?? preview.emiBounceCount ?? 0) : null
  const previewCount = preview?.emiBounceCount ?? preview?.v
  const previewOutcome = preview ? String(preview.outcome ?? '') : ''

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 sm:items-center" data-testid="data-calc-resolution-panel">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-slate-200 bg-white p-4 shadow-xl">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h3 className="text-base font-semibold text-slate-900">{heading}</h3>
            <p className="text-sm text-slate-600">{title}</p>
            <p className="mt-1 text-xs text-slate-500">
              Policy-version scoped · does not change global Data &amp; Parameters · allowCanonicalAuthority=false
            </p>
          </div>
          <button type="button" className="text-sm text-slate-500" onClick={onClose} disabled={busy}>
            Close
          </button>
        </div>

        {kind === 'THRESHOLD' ? (
          <div className="mt-4 space-y-3 text-sm">
            <p>Define the amount that qualifies as a large credit for this policy version.</p>
            <label className="block">
              <span className="text-slate-600">Threshold (₹)</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                inputMode="numeric"
                value={amount}
                disabled={busy}
                onChange={(e) => setAmount(formatInrInput(e.target.value))}
                data-testid="large-credit-threshold"
                placeholder="5,00,000"
              />
            </label>
            <label className="block">
              <span className="text-slate-600">Notes (optional)</span>
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                rows={2}
                value={notes}
                disabled={busy}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
            <div className="rounded border border-slate-100 bg-slate-50 px-2 py-2 text-xs text-slate-700">
              <p className="font-medium">Describe in plain English (optional proposal)</p>
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
                rows={2}
                value={describe}
                disabled={busy || proposing}
                onChange={(e) => setDescribe(e.target.value)}
                placeholder='e.g. "credits of ₹5 lakh or more"'
              />
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm mt-2"
                disabled={busy || proposing || !describe.trim()}
                onClick={() => void runPropose()}
              >
                {proposing ? 'Proposing…' : 'Suggest from description'}
              </button>
              {proposal ? (
                <p className="mt-2 text-slate-600">proposal only — review amount before save.</p>
              ) : null}
            </div>
            <p className="rounded border border-emerald-200 bg-emerald-50 px-2 py-2 text-xs text-emerald-950">
              Saving stores a policy-scoped threshold. Status becomes Ready for analyst information (non-blocking).
            </p>
          </div>
        ) : null}

        {kind === 'CLASSIFICATION' ? (
          <div className="mt-4 space-y-3 text-sm">
            <p>How should intercompany / merchant-group relationships be identified?</p>
            <p className="text-xs text-slate-500">
              Only mechanisms actually available are offered. There is no production merchant-group master today.
            </p>
            <label className="block">
              <span className="text-slate-600">Identification method</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={method}
                disabled={busy}
                onChange={(e) => setMethod(e.target.value)}
                data-testid="intercompany-method"
              >
                <option value="MANUAL_INSTITUTIONAL_CLASSIFICATION">Manual institutional classification</option>
                <option value="NARRATION_PATTERN_HINT">Narration pattern hint (not production-bound)</option>
                <option value="RELATED_PARTY_LIST_NOT_CONFIGURED">Related-party / merchant-group master (not configured)</option>
              </select>
            </label>
            <label className="block">
              <span className="text-slate-600">Notes (optional)</span>
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                rows={2}
                value={notes}
                disabled={busy}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
            <p className="rounded border border-amber-200 bg-amber-50 px-2 py-2 text-xs text-amber-950">
              Business definition can be saved. Status remains Needs configuration / Needs implementation until a
              production classifier exists — not Ready.
            </p>
          </div>
        ) : null}

        {kind === 'CALCULATION' && isEmiBounce ? (
          <div className="mt-4 space-y-3 text-sm text-slate-700" data-testid="emi-bounce-config">
            <div className="rounded border border-slate-100 bg-slate-50 px-3 py-2 text-xs space-y-1">
              <p>
                <span className="font-semibold">Metric:</span> EMI Bounce Count
              </p>
              <p>
                <span className="font-semibold">Source:</span> Bank Statement
              </p>
              <p>
                <span className="font-semibold">Output:</span> NUMERIC / COUNT
              </p>
              <p>
                <span className="font-semibold">Calculation:</span> Count EMI repayment events with matched
                return/bounce events during the period
              </p>
              <p>
                <span className="font-semibold">Missing data:</span> DATA_INSUFFICIENT if bank transactions or
                classification unavailable (never invent 0)
              </p>
            </div>

            <label className="block">
              <span className="text-slate-600">Period</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={periodMonths}
                disabled={busy}
                onChange={(e) => setPeriodMonths(e.target.value)}
                data-testid="emi-bounce-period"
              >
                <option value="1">Last 1 month</option>
                <option value="3">Last 3 months (policy default)</option>
                <option value="6">Last 6 months</option>
                <option value="12">Last 12 months</option>
              </select>
            </label>

            <label className="block">
              <span className="text-slate-600">EMI identification (base population)</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={emiIdentification}
                disabled={busy}
                onChange={(e) => setEmiIdentification(e.target.value)}
                data-testid="emi-bounce-emi-id"
              >
                <option value="EXISTING_EMI_CLASSIFIER">Existing EMI classifier</option>
                <option value="NARRATION_EMI_DEBIT">Narration contains EMI (debit)</option>
              </select>
            </label>

            <label className="block">
              <span className="text-slate-600">Bounce / return match</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={bounceIdentification}
                disabled={busy}
                onChange={(e) => setBounceIdentification(e.target.value)}
                data-testid="emi-bounce-bounce-id"
              >
                <option value="EXISTING_BOUNCE_RETURN_CLASSIFIER">
                  Existing bounce/return classifier + EMI narration
                </option>
                <option value="NARRATION_RETURN_WITH_EMI">Return/bounce narration with EMI</option>
              </select>
            </label>

            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={excludeDuplicates}
                disabled={busy}
                onChange={(e) => setExcludeDuplicates(e.target.checked)}
                data-testid="emi-bounce-dedupe"
              />
              Exclude duplicate / reversal rows (existing bank duplicate semantics)
            </label>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={busy || previewBusy || !documentId}
                onClick={() => void runPreview()}
                data-testid="emi-bounce-preview"
              >
                {previewBusy ? 'Running…' : 'Test on sample / staging data'}
              </button>
              <button
                type="button"
                className="text-xs text-slate-500 underline"
                onClick={() => setShowAdvanced((v) => !v)}
              >
                {showAdvanced ? 'Hide advanced' : 'Show advanced IDs'}
              </button>
            </div>

            {showAdvanced ? (
              <p className="rounded border border-slate-200 bg-white px-2 py-2 font-mono text-[11px] text-slate-600">
                canonicalId=banking.emi_bounce_count_3m · binding=EmiBounceCountCalculator.V1
              </p>
            ) : null}

            {preview ? (
              <div
                className="rounded border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-950"
                data-testid="emi-bounce-preview-result"
              >
                <p className="font-semibold">Preview result</p>
                <p className="mt-1">EMI candidates: {previewEmi}</p>
                <p>Matched bounced EMI events: {previewMatched}</p>
                <p>
                  EMI Bounce Count:{' '}
                  {previewOutcome === 'DATA_INSUFFICIENT' ? 'DATA_INSUFFICIENT' : String(previewCount ?? '—')}
                </p>
                {preview.reason ? <p className="mt-1 text-amber-900">{String(preview.reason)}</p> : null}
              </div>
            ) : null}

            <p className="rounded border border-emerald-200 bg-emerald-50 px-2 py-2 text-xs text-emerald-950">
              Saving enables the executable binding for this policy version. Status becomes Ready. Policy Test uses
              the same calculator path.
            </p>
          </div>
        ) : null}

        {kind === 'CALCULATION' && !isEmiBounce ? (
          <div className="mt-4 space-y-2 text-sm text-slate-700">
            <p className="font-medium">Calculation not implemented</p>
            <p className="text-xs text-slate-600">
              There is no executable calculation binding for <span className="font-mono">{parameterId || title}</span>{' '}
              yet. Saving a fake configuration is not allowed.
            </p>
            <p className="rounded border border-amber-200 bg-amber-50 px-2 py-2 text-xs text-amber-950">
              Status: Needs implementation. Use Keep as policy requirement or Ignore for automation, or close this
              dialog.
            </p>
          </div>
        ) : null}

        {kind === 'ADJUSTMENT' ? (
          <div className="mt-4 space-y-3 text-sm">
            <p>Exclude bulk deposits above a multiple of average deposits from ADB.</p>
            <label className="block">
              <span className="text-slate-600">Multiple of average deposits</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                inputMode="numeric"
                value={multiple}
                disabled={busy}
                onChange={(e) => setMultiple(e.target.value.replace(/[^\d.]/g, ''))}
                data-testid="adb-bulk-multiple"
              />
            </label>
            <p className="rounded border border-amber-200 bg-amber-50 px-2 py-2 text-xs text-amber-950">
              Policy definition can be saved; this is a design proposal. Executable calculator remains Needs
              configuration until average-deposit baseline wiring is production-bound — not Ready.
            </p>
          </div>
        ) : null}

        {kind === 'MANUAL' ? (
          <div className="mt-4 grid gap-3 text-sm">
            <label>
              <span className="text-slate-600">Label</span>
              <input className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={manualLabel} disabled={busy} onChange={(e) => setManualLabel(e.target.value)} />
            </label>
            <label>
              <span className="text-slate-600">Type</span>
              <select className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={manualType} disabled={busy} onChange={(e) => setManualType(e.target.value)}>
                <option>Money</option>
                <option>Integer</option>
                <option>Percent</option>
                <option>Text</option>
              </select>
            </label>
            <label>
              <span className="text-slate-600">Actor</span>
              <input className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={manualActor} disabled={busy} onChange={(e) => setManualActor(e.target.value)} />
            </label>
            <label>
              <span className="text-slate-600">Capture stage</span>
              <select className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={captureStage} disabled={busy} onChange={(e) => setCaptureStage(e.target.value)}>
                <option value="APPLICATION">Application</option>
                <option value="CAM_UNDERWRITING_REVIEW">CAM / underwriting review</option>
                <option value="CREDIT_ANALYST_CAPTURE">Credit analyst capture</option>
                <option value="UNSPECIFIED">Unspecified (not ready)</option>
              </select>
            </label>
          </div>
        ) : null}

        {error ? (
          <p className="mt-3 rounded border border-rose-200 bg-rose-50 px-2 py-2 text-xs text-rose-900" data-testid="data-calc-resolve-error">
            {error}
          </p>
        ) : null}

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          {kind === 'CALCULATION' && !isEmiBounce ? null : (
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy}
              onClick={() => void save()}
              data-testid="data-calc-resolve-save"
            >
              {kind === 'CALCULATION' ? 'Save configuration' : 'Save definition'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export function resolveKindForGroup(parameterId: string, missingAction?: string, isAdjustment?: boolean): DataCalcResolveKind {
  if (isAdjustment || parameterId.includes('bulk') || missingAction === 'CONFIRM') return 'ADJUSTMENT'
  if (parameterId === 'banking.large_credit_transactions') return 'THRESHOLD'
  if (parameterId === 'banking.intercompany_transactions') return 'CLASSIFICATION'
  if (parameterId === 'banking.emi_bounce_count_3m') return 'CALCULATION'
  if (missingAction === 'CONFIGURE') return 'CALCULATION'
  return 'THRESHOLD'
}
