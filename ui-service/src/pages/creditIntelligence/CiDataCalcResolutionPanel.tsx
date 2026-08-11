import { useMemo, useState } from 'react'
import type { ReviewRuleBody } from '@/api/creditIntelligence'
import { proposeParameterDefinition } from '@/api/creditIntelligence'

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
 * POLICY-DATA-RESOLUTION-UX-1 — typed Resolve/Define forms for Data & Calculations.
 * Policy-scoped only. Does not mutate GACAT.
 */
export function CiDataCalcResolutionPanel({
  open,
  onClose,
  kind,
  parameterId,
  title,
  ruleId,
  busy,
  onResolve,
}: {
  open: boolean
  onClose: () => void
  kind: DataCalcResolveKind
  parameterId: string
  title: string
  ruleId: string
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
        await onResolve(ruleId, {
          uiAction: 'RESOLVE_DATA_CALCULATION',
          dataItemId: parameterId,
          parameterId,
          reason: 'EMI bounce calculation configuration recorded — not production-ready',
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

        {error ? (
          <div className="mt-3 rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
        ) : null}

        {kind === 'THRESHOLD' ? (
          <div className="mt-4 space-y-3">
            <label className="block text-sm">
              <span className="text-slate-600">Definition type</span>
              <input className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value="Absolute transaction amount" disabled />
            </label>
            <label className="block text-sm">
              <span className="text-slate-600">Threshold (₹)</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                inputMode="numeric"
                placeholder="5,00,000"
                value={amount}
                disabled={busy}
                onChange={(e) => setAmount(formatInrInput(e.target.value))}
                data-testid="large-credit-threshold"
              />
            </label>
            <label className="block text-sm">
              <span className="text-slate-600">Period</span>
              <input className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value="Last 3 months" disabled />
            </label>
            <label className="block text-sm">
              <span className="text-slate-600">Notes (optional)</span>
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                rows={2}
                value={notes}
                disabled={busy}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
            <details className="rounded border border-slate-200 px-3 py-2 text-sm">
              <summary className="cursor-pointer font-medium text-slate-800">Describe what you mean (proposal only)</summary>
              <textarea
                className="mt-2 w-full rounded border border-slate-300 px-3 py-2"
                rows={2}
                placeholder="Large credit means any individual credit above five lakh rupees"
                value={describe}
                onChange={(e) => setDescribe(e.target.value)}
              />
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm mt-2"
                disabled={proposing || busy}
                onClick={() => void runPropose()}
              >
                Propose
              </button>
              {proposal ? (
                <p className="mt-2 text-xs text-slate-600">
                  Proposal: {String(proposal.plainEnglish ?? proposal.proposedCalculation ?? JSON.stringify(proposal).slice(0, 160))}
                  {' — confirm by saving the threshold above (not auto-saved).'}
                </p>
              ) : null}
            </details>
            <p className="text-xs text-slate-500">
              Preview: Large credit = Transaction amount &gt;= ₹{amount || '—'}
            </p>
          </div>
        ) : null}

        {kind === 'CLASSIFICATION' ? (
          <div className="mt-4 space-y-3 text-sm">
            <p className="text-slate-700">
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
              Business definition can be saved separately from execution readiness. Expect status:
              Needs configuration until a production classifier exists.
            </p>
          </div>
        ) : null}

        {kind === 'CALCULATION' ? (
          <div className="mt-4 space-y-2 text-sm text-slate-700">
            <p className="font-medium">EMI bounce count — calculation configuration</p>
            <ul className="list-disc pl-5 text-xs">
              <li>EMI classifier — available</li>
              <li>Bounce/return classifier — available</li>
              <li>Executable combined metric — not implemented / not production-bound</li>
            </ul>
            <p className="rounded border border-amber-200 bg-amber-50 px-2 py-2 text-xs text-amber-950">
              Saving records configuration state only. This item cannot become Ready from ingredients alone.
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
              Policy definition can be saved; executable calculator remains Needs configuration until average-deposit baseline wiring is production-bound.
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

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm"
            disabled={busy}
            onClick={() => void save()}
            data-testid="data-calc-resolve-save"
          >
            Save definition
          </button>
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
