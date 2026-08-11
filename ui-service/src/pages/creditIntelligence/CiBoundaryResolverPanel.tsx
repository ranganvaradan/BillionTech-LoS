import { useMemo, useState } from 'react'
import { resolvePolicyAmbiguity } from '@/api/creditIntelligence'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

/**
 * POLICY-RULE-EDITOR-ROUNDTRIP-P0 — fix uncovered txn=100 without rewriting the compound rule.
 */
export function CiBoundaryResolverPanel({
  open,
  onClose,
  documentId,
  ambiguityId,
  ruleName,
  busy,
  setBusy,
  onError,
  onSession,
}: {
  open: boolean
  onClose: () => void
  documentId: string
  ambiguityId: string
  ruleName?: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSession: (data: Record<string, unknown>) => void
}) {
  const [choice, setChoice] = useState<'treat_100_as_ratio_branch' | 'treat_100_as_count_branch' | ''>('')
  const [previewNote, setPreviewNote] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const previewText = useMemo(() => {
    if (choice === 'treat_100_as_ratio_branch') {
      return 'transactions ≥ 100 → return ratio ≤ 5%; transactions < 100 → return count ≤ 5'
    }
    if (choice === 'treat_100_as_count_branch') {
      return 'transactions > 100 → return ratio ≤ 5%; transactions ≤ 100 → return count ≤ 5'
    }
    return null
  }, [choice])

  if (!open) return null

  const confirm = async () => {
    if (!choice || !ambiguityId) return
    setBusy(true)
    setError(null)
    onError(null)
    try {
      const data = await resolvePolicyAmbiguity(documentId, ambiguityId, {
        uiAction: 'ACCEPT_RECOMMENDATION',
        resolvedOption: choice,
        notes: 'Boundary defined via Define boundary resolver (operator-only patch)',
      })
      onSession(data as Record<string, unknown>)
      onClose()
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Could not save boundary'
      setError(msg)
      onError(msg)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 sm:items-center" data-testid="boundary-resolver-panel">
      <div className="w-full max-w-lg rounded-xl border border-slate-200 bg-white p-4 shadow-xl">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h3 className="text-base font-semibold text-slate-900">Define boundary</h3>
            <p className="text-sm text-slate-600">{ruleName ?? 'Inward cheque / ECS / ENACH returns'}</p>
          </div>
          <button type="button" className="text-sm text-slate-500" onClick={onClose} disabled={busy}>
            Close
          </button>
        </div>
        <p className="mt-3 text-sm text-slate-800">
          What should happen when transaction count is exactly <strong>100</strong>?
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Only the comparison operator changes. Branches, parameters, and period stay the same.
        </p>
        <div className="mt-3 space-y-2">
          <label className="flex cursor-pointer gap-2 rounded border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
            <input
              type="radio"
              name="boundary-choice"
              checked={choice === 'treat_100_as_ratio_branch'}
              disabled={busy}
              onChange={() => {
                setChoice('treat_100_as_ratio_branch')
                setPreviewNote('Include 100 in the ratio branch')
              }}
              data-testid="boundary-choice-ratio"
            />
            <span>
              <span className="font-medium">Include 100 in the ratio branch</span>
              <span className="mt-0.5 block text-xs text-slate-600">
                transactions ≥ 100 → return ratio ≤ 5%; transactions &lt; 100 → return count ≤ 5
              </span>
            </span>
          </label>
          <label className="flex cursor-pointer gap-2 rounded border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
            <input
              type="radio"
              name="boundary-choice"
              checked={choice === 'treat_100_as_count_branch'}
              disabled={busy}
              onChange={() => {
                setChoice('treat_100_as_count_branch')
                setPreviewNote('Include 100 in the count branch')
              }}
              data-testid="boundary-choice-count"
            />
            <span>
              <span className="font-medium">Include 100 in the count branch</span>
              <span className="mt-0.5 block text-xs text-slate-600">
                transactions &gt; 100 → return ratio ≤ 5%; transactions ≤ 100 → return count ≤ 5
              </span>
            </span>
          </label>
        </div>
        {previewText ? (
          <div className="mt-3 rounded border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-950" data-testid="boundary-preview">
            <p className="font-semibold">Preview impact</p>
            <p className="mt-1">{previewNote}</p>
            <p className="mt-0.5">{previewText}</p>
          </div>
        ) : null}
        {error ? <p className="mt-2 text-xs text-rose-700">{error}</p> : null}
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm"
            disabled={busy || !choice}
            onClick={() => void confirm()}
            data-testid="boundary-confirm"
          >
            Confirm boundary
          </button>
        </div>
      </div>
    </div>
  )
}

export function boundaryFromBlockers(blockers: unknown[]): {
  ambiguityId: string
  ruleId?: string
  ruleName?: string
} | null {
  for (const raw of blockers) {
    const b = asRecord(raw)
    if (b.defineBoundary && b.ambiguityId) {
      return {
        ambiguityId: String(b.ambiguityId),
        ruleId: b.ruleId ? String(b.ruleId) : undefined,
        ruleName: b.ruleName ? String(b.ruleName) : undefined,
      }
    }
    const reason = String(b.reason ?? b.message ?? '')
    if (reason.includes('transaction count = 100') && b.ambiguityId) {
      return {
        ambiguityId: String(b.ambiguityId),
        ruleId: b.ruleId ? String(b.ruleId) : undefined,
        ruleName: b.ruleName ? String(b.ruleName) : undefined,
      }
    }
  }
  return null
}

export function findOpenBoundaryAmbiguity(ambiguities: unknown[]): string | null {
  for (const raw of ambiguities) {
    const a = asRecord(raw)
    const status = String(a.resolutionStatus ?? a.status ?? '')
    if (status !== 'OPEN' && status !== 'CLARIFICATION_REQUESTED') continue
    const phrase = String(a.phrase ?? a.unclearTerm ?? '').toLowerCase()
    const type = String(a.ambiguityType ?? a.type ?? '')
    if (phrase.includes('exactly 100') || phrase.includes('100 transaction') || type.includes('BOUNDARY')) {
      return String(a.id ?? a.ambiguityId ?? '')
    }
  }
  return null
}

export { asList }
