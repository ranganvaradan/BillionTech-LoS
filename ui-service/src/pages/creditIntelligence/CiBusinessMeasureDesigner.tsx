import { useEffect, useState } from 'react'
import {
  approveBusinessMeasure,
  confirmBusinessMeasure,
  openBusinessMeasureDesigner,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiSection } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function listToLines(v: unknown): string {
  if (Array.isArray(v)) return v.map(String).join('\n')
  return v == null ? '' : String(v)
}

function linesToList(s: string): string[] {
  return s
    .split('\n')
    .map((x) => x.trim())
    .filter(Boolean)
}

export function CiBusinessMeasureDesigner({
  documentId,
  dataElementCode,
  busy,
  setBusy,
  onError,
  onComplete,
  onClose,
}: {
  documentId: string
  dataElementCode: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onComplete: (session: Record<string, unknown>) => void
  onClose: () => void
}) {
  const [loading, setLoading] = useState(true)
  const [designer, setDesigner] = useState<Record<string, unknown> | null>(null)
  const [metricName, setMetricName] = useState('')
  const [meaning, setMeaning] = useState('')
  const [sourceData, setSourceData] = useState('')
  const [period, setPeriod] = useState('Previous 3 months')
  const [calcType, setCalcType] = useState('FILTERED_TRANSACTION_AGGREGATION')
  const [calculation, setCalculation] = useState('')
  const [includeText, setIncludeText] = useState('')
  const [excludeText, setExcludeText] = useState('')
  const [missingBehaviour, setMissingBehaviour] = useState('DATA_INSUFFICIENT')
  const [governance, setGovernance] = useState('APPROVED')
  const [classification, setClassification] = useState('BUSINESS_MEASURE')
  const [resolutionMode, setResolutionMode] = useState<'MEASURE' | 'APPLICATION' | 'MANUAL'>('MEASURE')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      onError(null)
      try {
        const data = await openBusinessMeasureDesigner(documentId, dataElementCode)
        if (cancelled) return
        setDesigner(data)
        const proposal = asRecord(data.aiProposedDefinition)
        setMetricName(String(proposal.businessMeasureName ?? data.businessName ?? dataElementCode))
        setMeaning(String(proposal.businessMeaning ?? ''))
        setSourceData(String(proposal.sourceData ?? data.primarySource ?? ''))
        setPeriod(String(proposal.period ?? 'Previous 3 months'))
        setCalcType(String(proposal.calculationType ?? 'FILTERED_TRANSACTION_AGGREGATION'))
        setCalculation(String(proposal.calculation ?? ''))
        setIncludeText(listToLines(proposal.include))
        setExcludeText(listToLines(proposal.exclude))
        setMissingBehaviour(String(proposal.missingDataBehaviour ?? 'DATA_INSUFFICIENT'))
        setClassification(String(data.classification ?? 'BUSINESS_MEASURE'))
        if (String(data.classification) === 'APPLICATION_INPUT') {
          setResolutionMode('APPLICATION')
        }
      } catch (e) {
        onError(e instanceof ApiError ? e.message : 'Could not open Business Measure Designer')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [documentId, dataElementCode, onError])

  const affected = asList(designer?.affectedRules)
  const stages = asRecord(designer?.stages)
  const proposal = asRecord(designer?.aiProposedDefinition)
  const supported = asList(designer?.supportedCalculationTypes).map(String)

  const submit = async () => {
    setBusy(true)
    onError(null)
    try {
      const body: Record<string, unknown> = {
        dataElementCode,
        metricName,
        businessMeaning: meaning,
        sourceData,
        period,
        calculationType: calcType,
        calculation,
        include: linesToList(includeText),
        exclude: linesToList(excludeText),
        missingDataBehaviour: missingBehaviour,
        classification,
        governanceStatus: governance,
        humanConfirmed: true,
        resolvedBy: 'credit_manager',
        aiProposed: true,
      }
      if (resolutionMode === 'APPLICATION') {
        body.classification = 'APPLICATION_INPUT'
        body.fieldName = metricName
        body.dataType = 'DECIMAL'
        body.required = true
      }
      if (resolutionMode === 'MANUAL') {
        body.resolutionMode = 'MANUAL_VERIFICATION'
        body.whatMustBeVerified = meaning || metricName
        body.evidenceRequired = 'Credit appraisal note / supporting document'
        body.responsibleRole = 'Credit Manager'
        body.outcomeBehaviour = 'PASS / FAIL / REFER'
      }
      let session = await confirmBusinessMeasure(documentId, body)
      const result = asRecord(session.measureDesignerResult)
      if (result.measureId && governance === 'APPROVED' && resolutionMode === 'MEASURE' && !result.metricCreated) {
        session = await approveBusinessMeasure(documentId, String(result.measureId), {
          resolvedBy: 'credit_manager',
        })
      }
      onComplete(session)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not save business measure')
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return <p className="text-sm text-slate-600">Opening Business Measure Designer…</p>
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-auto bg-slate-900/40 p-4">
      <div className="my-6 w-full max-w-3xl rounded-2xl border border-slate-200 bg-white shadow-xl">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Define Business Measure
            </div>
            <h2 className="mt-1 text-lg font-semibold text-slate-900">{metricName || dataElementCode}</h2>
            <p className="mt-1 text-sm text-slate-600">
              Classification: <strong>{classification}</strong>
              {affected.length > 0 ? ` · Used by ${affected.length} policy rule(s)` : ''}
            </p>
          </div>
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="space-y-4 px-5 py-4">
          <div className="grid gap-3 sm:grid-cols-4 text-sm">
            {(
              [
                ['Source Data', stages.sourceData],
                ['Business Definition', stages.businessDefinition],
                ['Business Measure', stages.businessMeasure],
                ['Rule', stages.ruleExecutability],
              ] as [string, unknown][]
            ).map(([label, val]) => (
              <div key={label} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                <div className="text-[11px] font-semibold uppercase text-slate-500">{label}</div>
                <div className="mt-1 font-medium text-slate-800">{String(val ?? '—')}</div>
              </div>
            ))}
          </div>

          <CiSection title="AI proposed definition" description="Propose only — Credit Head must confirm. Not auto-executable.">
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-amber-950">
              {String(designer?.aiLabel ?? 'AI PROPOSED DEFINITION')}
            </div>
            <p className="mt-2 text-sm text-slate-700">{String(proposal.businessMeaning ?? '')}</p>
            {proposal.sourceClause ? (
              <pre className="mt-2 max-h-28 overflow-auto whitespace-pre-wrap rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-600">
                {String(proposal.sourceClause)}
              </pre>
            ) : null}
            {asList(proposal.assumptions).length > 0 ? (
              <ul className="mt-2 list-disc pl-5 text-xs text-slate-600">
                {asList(proposal.assumptions).map((a, i) => (
                  <li key={i}>{String(a)}</li>
                ))}
              </ul>
            ) : null}
          </CiSection>

          <div className="flex flex-wrap gap-2">
            {(
              [
                ['MEASURE', 'Business measure'],
                ['APPLICATION', 'Application input'],
                ['MANUAL', 'Manual verification'],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                type="button"
                onClick={() => setResolutionMode(id)}
                className={`rounded-full px-3 py-1 text-xs font-semibold ${
                  resolutionMode === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              <span className="font-semibold text-slate-700">Business measure name</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                value={metricName}
                onChange={(e) => setMetricName(e.target.value)}
              />
            </label>
            <label className="text-sm">
              <span className="font-semibold text-slate-700">Source data</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                value={sourceData}
                onChange={(e) => setSourceData(e.target.value)}
              />
            </label>
            <label className="sm:col-span-2 text-sm">
              <span className="font-semibold text-slate-700">Business meaning</span>
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                rows={3}
                value={meaning}
                onChange={(e) => setMeaning(e.target.value)}
              />
            </label>
            {resolutionMode === 'MEASURE' ? (
              <>
                <label className="text-sm">
                  <span className="font-semibold text-slate-700">Period</span>
                  <input
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                    value={period}
                    onChange={(e) => setPeriod(e.target.value)}
                  />
                </label>
                <label className="text-sm">
                  <span className="font-semibold text-slate-700">Calculation type</span>
                  <select
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                    value={calcType}
                    onChange={(e) => setCalcType(e.target.value)}
                  >
                    {(supported.length > 0 ? supported : [calcType]).map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="sm:col-span-2 text-sm">
                  <span className="font-semibold text-slate-700">Calculation</span>
                  <input
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                    value={calculation}
                    onChange={(e) => setCalculation(e.target.value)}
                  />
                </label>
                <label className="text-sm">
                  <span className="font-semibold text-slate-700">Include (one per line)</span>
                  <textarea
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                    rows={4}
                    value={includeText}
                    onChange={(e) => setIncludeText(e.target.value)}
                  />
                </label>
                <label className="text-sm">
                  <span className="font-semibold text-slate-700">Exclude (one per line)</span>
                  <textarea
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                    rows={4}
                    value={excludeText}
                    onChange={(e) => setExcludeText(e.target.value)}
                  />
                </label>
              </>
            ) : null}
            <label className="text-sm">
              <span className="font-semibold text-slate-700">Missing-data behaviour</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                value={missingBehaviour}
                onChange={(e) => setMissingBehaviour(e.target.value)}
              />
            </label>
            <label className="text-sm">
              <span className="font-semibold text-slate-700">Governance</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5"
                value={governance}
                onChange={(e) => setGovernance(e.target.value)}
              >
                <option value="PROPOSED">PROPOSED</option>
                <option value="REVIEWED">REVIEWED</option>
                <option value="APPROVED">APPROVED</option>
              </select>
            </label>
          </div>

          {affected.length > 0 ? (
            <p className="text-sm text-slate-700">
              This definition is used by {affected.length} policy rule
              {affected.length === 1 ? '' : 's'}:{' '}
              {affected
                .slice(0, 5)
                .map((r) => String(asRecord(r).ruleName ?? asRecord(r).systemRuleId))
                .join(', ')}
              {affected.length > 5 ? '…' : ''}
            </p>
          ) : null}

          <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-3">
            <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose} disabled={busy}>
              Cancel
            </button>
            <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => void submit()} disabled={busy}>
              {busy ? 'Saving…' : 'Confirm definition'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
