import { AmountInputHint } from '@/components/ui/AmountInputHint'
import { useEffect, useMemo, useState } from 'react'
import { saveScorecardInputs, type ManualCreditInputsPayload } from '@/api/applications'
import { ErrorState } from '@/components/ErrorState'
import {
  buildCustomMetricsFromRequirements,
  buildScorecardMetricsPayload,
  readScorecardManualInputValue,
} from '@/components/scorecard/ScorecardMetricsManualSection'
import { messageForKycAction } from '@/api/kycErrorMessage'
import type { ScorecardParamDef } from '@/lib/credit/scorecardConfig'
import {
  requirementToParamDef,
  type ScorecardInputRequirement,
} from '@/lib/credit/scorecardInputRequirements'
import type { ApplicationResponse } from '@/types/application'

function yesNoFromStored(raw: string): string {
  const v = raw.trim().toUpperCase()
  if (v === 'Y' || v === 'YES' || v === '1' || v === 'TRUE') return 'Y'
  if (v === 'N' || v === 'NO' || v === '0' || v === 'FALSE') return 'N'
  return raw
}

function FieldInput({
  param,
  value,
  onChange,
}: {
  param: ScorecardParamDef
  value: string
  onChange: (v: string) => void
}) {
  if (param.type === 'yesno') {
    return (
      <select className="bt-input w-full text-sm" value={value || ''} onChange={(e) => onChange(e.target.value)}>
        <option value="">— Select —</option>
        <option value="Y">Yes</option>
        <option value="N">No</option>
      </select>
    )
  }
  if (param.type === 'enum' && param.enumOptions?.length) {
    return (
      <select className="bt-input w-full text-sm" value={value || ''} onChange={(e) => onChange(e.target.value)}>
        <option value="">— Select —</option>
        {param.enumOptions.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    )
  }
  if (param.type === 'text') {
    return (
      <input
        type="text"
        className="bt-input w-full text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    )
  }
  return (
    <div>
      <input
        type="number"
        className="bt-input w-full text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        step="any"
      />
      <AmountInputHint value={value} />
    </div>
  )
}

type Props = {
  applicationId: string
  app: ApplicationResponse
  matchedScorecardName: string
  matchedScorecardVersion: number
  requirements: ScorecardInputRequirement[]
  onRefetch: () => void | Promise<void>
  focusParameter?: string | null
}

/** OTHER / GST scorecard fields collected before underwriting — not in Manual credit inputs. */
export function ScorecardPreRunInputsPanel({
  applicationId,
  app,
  matchedScorecardName,
  matchedScorecardVersion,
  requirements,
  onRefetch,
  focusParameter,
}: Props) {
  const manual = (
    app.creditControlView as { creditControl?: { manual?: Record<string, unknown> } } | undefined
  )?.creditControl?.manual

  const visibleRequirements = useMemo(() => requirements, [requirements])

  const initialValues = useMemo(() => {
    const out: Record<string, string> = {}
    for (const req of visibleRequirements) {
      const def = requirementToParamDef(req)
      const raw = readScorecardManualInputValue(manual, req.manualKey)
      out[req.manualKey] = def.type === 'yesno' ? yesNoFromStored(raw) : raw
    }
    return out
  }, [manual, visibleRequirements])

  const [values, setValues] = useState<Record<string, string>>(initialValues)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [okMsg, setOkMsg] = useState<string | null>(null)

  useEffect(() => {
    setValues(initialValues)
  }, [initialValues])

  useEffect(() => {
    if (!focusParameter) return
    const req = visibleRequirements.find((r) => r.parameter === focusParameter)
    if (!req) return
    window.requestAnimationFrame(() => {
      document.getElementById(`scorecard-input-${req.manualKey}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }, [focusParameter, visibleRequirements])

  if (visibleRequirements.length === 0) {
    return null
  }

  async function onSave() {
    setErr(null)
    setOkMsg(null)
    for (const req of visibleRequirements) {
      if (!values[req.manualKey]?.trim()) {
        setErr(`Enter a value for ${req.label}.`)
        document.getElementById(`scorecard-input-${req.manualKey}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        return
      }
    }
    setSaving(true)
    try {
      const customMetrics = buildCustomMetricsFromRequirements(values, visibleRequirements)
      const payload = buildScorecardMetricsPayload(values, customMetrics) as ManualCreditInputsPayload
      await saveScorecardInputs(applicationId, payload)
      await onRefetch()
      setOkMsg('Scorecard inputs saved. You can start or re-run underwriting.')
    } catch (e) {
      setErr(messageForKycAction(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div id="scorecard-pre-run-inputs" className="bt-section-card bt-section-card--warning scroll-mt-24 p-4 text-sm text-amber-950">
      <h3 className="text-sm font-semibold">Scorecard inputs required before underwriting</h3>
      <p className="mt-1 text-xs text-amber-900/90">
        Matched policy: <strong>{matchedScorecardName}</strong>
        {matchedScorecardVersion ? <> (v{matchedScorecardVersion})</> : null}. Enter the fields below, then save. GST /
        ITR / bank-statement fields come from document OCR extract (or safe defaults) — not this form.
      </p>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {visibleRequirements.map((req) => {
          const def = requirementToParamDef(req)
          const ready = req.ready || Boolean(values[req.manualKey]?.trim())
          return (
            <label
              key={`${req.source}:${req.parameter}`}
              id={`scorecard-input-${req.manualKey}`}
              className="block scroll-mt-28 rounded-md border border-amber-200/80 bg-white/70 p-3 text-xs"
            >
              <span className="mb-1 flex items-center justify-between gap-2 font-medium text-slate-800">
                {req.label}
                <span className={ready ? 'text-emerald-700' : 'text-amber-800'}>{ready ? 'Ready' : 'Required'}</span>
              </span>
              <span className="mb-2 block text-[10px] uppercase tracking-wide text-slate-500">{req.source}</span>
              <FieldInput
                param={def}
                value={values[req.manualKey] ?? ''}
                onChange={(v) => setValues((prev) => ({ ...prev, [req.manualKey]: v }))}
              />
            </label>
          )
        })}
      </div>

      {err ? <div className="mt-3"><ErrorState message={err} /></div> : null}
      {okMsg ? (
        <div className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-900">
          {okMsg}
        </div>
      ) : null}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          disabled={saving}
          onClick={() => void onSave()}
          className="rounded-md bg-amber-950 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save scorecard inputs'}
        </button>
        <span className="text-xs text-amber-900/80">Saved values are used on the next underwriting run.</span>
      </div>
    </div>
  )
}
