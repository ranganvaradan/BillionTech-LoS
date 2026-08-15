import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  fetchScorecardFactorPicker,
  linkPolicyScorecard,
  previewScorecardWeights,
} from '@/api/dp3PolicyGraph'
import { createScorecard, listScorecards, updateScorecard } from '@/api/scorecards'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection } from '@/components/creditIntelligence/CiSection'

type FactorRow = {
  canonicalParameterId: string
  rawWeight: string
  required: boolean
  bandPointsEarned: string
  bandNote: string
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

/**
 * Policy Studio Scorecard tab — create/link POLICY_WEIGHTED_V2 within Policy.
 * Factors limited to Policy parameter inventory (DP-3 factor picker).
 */
export function CiPolicyScorecardTab({
  documentId,
  policyName,
  loanProduct,
  borrowerType,
  linkedScorecardId,
  busy,
  setBusy,
  onError,
  onLinked,
}: {
  documentId: string
  policyName?: string
  loanProduct?: string
  borrowerType?: string
  linkedScorecardId?: string | null
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onLinked?: (scorecardId: string) => void
}) {
  const [mode, setMode] = useState<'NONE' | 'LINK' | 'CREATE'>(
    linkedScorecardId ? 'LINK' : 'NONE',
  )
  const [picker, setPicker] = useState<Array<Record<string, unknown>>>([])
  const [catalogue, setCatalogue] = useState<Array<{ id: string; name: string; scoringMode?: string }>>(
    [],
  )
  const [selectedId, setSelectedId] = useState(linkedScorecardId ?? '')
  const [factors, setFactors] = useState<FactorRow[]>([])
  const [weightPreview, setWeightPreview] = useState<Record<string, unknown> | null>(null)
  const [newName, setNewName] = useState(
    policyName ? `${policyName} — Scorecard` : 'Policy weighted scorecard',
  )
  const [msg, setMsg] = useState<string | null>(null)

  const loadPicker = useCallback(async () => {
    onError(null)
    try {
      const data = await fetchScorecardFactorPicker(documentId)
      const factorsRaw = asList(data.factors).map(asRecord)
      setPicker(factorsRaw)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load Policy factor picker')
    }
  }, [documentId, onError])

  const loadCatalogue = useCallback(async () => {
    try {
      const rows = await listScorecards()
      setCatalogue(
        rows.map((r) => ({
          id: r.id,
          name: r.name,
          scoringMode: String((r as { scoringMode?: string }).scoringMode ?? ''),
        })),
      )
    } catch {
      setCatalogue([])
    }
  }, [])

  useEffect(() => {
    void loadPicker()
    void loadCatalogue()
  }, [loadPicker, loadCatalogue])

  useEffect(() => {
    if (linkedScorecardId) {
      setSelectedId(linkedScorecardId)
      setMode('LINK')
    }
  }, [linkedScorecardId])

  const allowedIds = useMemo(
    () =>
      new Set(
        picker
          .map((p) => String(p.canonicalParameterId ?? p.parameterId ?? ''))
          .filter(Boolean),
      ),
    [picker],
  )

  const addFactor = (canonicalParameterId: string) => {
    if (!canonicalParameterId || factors.some((f) => f.canonicalParameterId === canonicalParameterId)) {
      return
    }
    if (!allowedIds.has(canonicalParameterId)) {
      onError('Scorecard factors must be parameters from this Policy only.')
      return
    }
    setFactors((prev) => [
      ...prev,
      {
        canonicalParameterId,
        rawWeight: '1',
        required: true,
        bandPointsEarned: '100',
        bandNote: '',
      },
    ])
    setWeightPreview(null)
  }

  const runWeightPreview = async () => {
    setBusy(true)
    onError(null)
    try {
      const body = {
        factors: factors.map((f) => ({
          canonicalParameterId: f.canonicalParameterId,
          rawWeight: Number(f.rawWeight) || 0,
          required: f.required,
          dataState: 'PRESENT',
          bandPointsEarned: Number(f.bandPointsEarned) || 100,
          bandPointsMax: 100,
        })),
      }
      const data = await previewScorecardWeights(body)
      setWeightPreview(data)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Weight preview failed')
    } finally {
      setBusy(false)
    }
  }

  const linkExisting = async () => {
    if (!selectedId) {
      onError('Select a scorecard to link')
      return
    }
    setBusy(true)
    onError(null)
    setMsg(null)
    try {
      await linkPolicyScorecard(documentId, selectedId)
      setMsg('Scorecard linked to this Policy Version (POLICY_WEIGHTED_V2).')
      onLinked?.(selectedId)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not link scorecard')
    } finally {
      setBusy(false)
    }
  }

  const createAndLink = async () => {
    if (factors.length === 0) {
      onError('Add at least one factor from this Policy’s parameter inventory.')
      return
    }
    for (const f of factors) {
      if (!allowedIds.has(f.canonicalParameterId)) {
        onError(`Factor not in Policy inventory: ${f.canonicalParameterId}`)
        return
      }
      if (Number(f.rawWeight) < 0) {
        onError('Weights cannot be negative.')
        return
      }
    }
    if (factors.every((f) => Number(f.rawWeight) === 0)) {
      onError('Weights cannot all be zero.')
      return
    }
    setBusy(true)
    onError(null)
    setMsg(null)
    try {
      const product = loanProduct && loanProduct !== 'ALL' ? loanProduct : 'BUSINESS_TERM_LOAN'
      const bt = borrowerType && borrowerType !== 'ALL' ? borrowerType : 'INDIVIDUAL'
      const created = await createScorecard({
        name: newName.trim() || 'Policy weighted scorecard',
        borrowerType: bt as 'INDIVIDUAL',
        loanProduct: product,
        version: 1,
        priority: 10,
        minAmount: null,
        maxAmount: null,
        geography: null,
        scorecardJson: {
          mode: 'POLICY_WEIGHTED_V2',
          rows: factors.map((f, i) => ({
            id: `f${i + 1}`,
            parameter: f.canonicalParameterId,
            canonicalParameterId: f.canonicalParameterId,
            source: 'POLICY',
            condition: f.bandNote || 'PRESENT',
            weight: Number(f.rawWeight) || 0,
            score: Number(f.bandPointsEarned) || 100,
            missingData: f.required ? 'REQUIRED' : 'OPTIONAL_SKIP',
          })),
        },
        thresholdsJson: { approveMinPercent: 60, manualMinPercent: 40 },
        hardRulesJson: { rules: [] },
        active: false,
        status: 'DRAFT',
        safetyJson: { missingDataPoliciesConfirmed: true, factorPolicies: {} },
      })
      await linkPolicyScorecard(documentId, created.id)
      setSelectedId(created.id)
      setMode('LINK')
      setMsg('Scorecard created and linked to this Policy.')
      onLinked?.(created.id)
      await loadCatalogue()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not create scorecard')
    } finally {
      setBusy(false)
    }
  }

  const persistFactorsToLinked = async () => {
    if (!selectedId) return
    setBusy(true)
    onError(null)
    try {
      const rows = await listScorecards()
      const existing = rows.find((r) => r.id === selectedId)
      if (!existing) throw new Error('Linked scorecard not found')
      await updateScorecard(selectedId, {
        name: existing.name,
        borrowerType: existing.borrowerType as 'INDIVIDUAL',
        loanProduct: existing.loanProduct,
        version: existing.version,
        priority: existing.priority,
        minAmount: existing.minAmount,
        maxAmount: existing.maxAmount,
        geography: existing.geography,
        scorecardJson: {
          ...existing.scorecardJson,
          mode: 'POLICY_WEIGHTED_V2',
          rows: factors.map((f, i) => ({
            id: `f${i + 1}`,
            parameter: f.canonicalParameterId,
            canonicalParameterId: f.canonicalParameterId,
            source: 'POLICY',
            condition: f.bandNote || 'PRESENT',
            weight: Number(f.rawWeight) || 0,
            score: Number(f.bandPointsEarned) || 100,
            missingData: f.required ? 'REQUIRED' : 'OPTIONAL_SKIP',
          })),
        },
        thresholdsJson: existing.thresholdsJson,
        hardRulesJson: existing.hardRulesJson,
        active: existing.active,
        status: existing.status,
        safetyJson: existing.safetyJson,
      })
      setMsg('Scorecard factors updated. Hard Policy rules remain separate on the Rules tab.')
      await runWeightPreview()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not update scorecard')
    } finally {
      setBusy(false)
    }
  }

  const previewWeights = asList(weightPreview?.weights ?? weightPreview?.factorWeights).map(asRecord)
  const normSum = previewWeights.reduce((s, w) => s + Number(w.normalizedWeight ?? 0), 0)

  return (
    <div className="space-y-4" data-testid="policy-scorecard-tab">
      <CiExecutiveSummary title="Scorecard (optional)">
        <p className="text-sm text-slate-700">
          Scorecard is a Policy component — not a separate decision engine. Hard rules stay on the
          Rules tab; scorecard factors score only parameters already in this Policy.
        </p>
      </CiExecutiveSummary>

      <CiSection title="Scorecard linkage">
        <div className="flex flex-wrap gap-2">
          {(
            [
              ['NONE', 'No scorecard'],
              ['LINK', 'Link existing'],
              ['CREATE', 'Create new'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              className={`rounded-full px-3 py-1.5 text-sm font-medium ${
                mode === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
              onClick={() => setMode(id)}
            >
              {label}
            </button>
          ))}
        </div>

        {mode === 'NONE' ? (
          <p className="mt-3 text-sm text-slate-600">
            This Policy will underwrite with hard rules only. You can add a scorecard later.
          </p>
        ) : null}

        {mode === 'LINK' ? (
          <div className="mt-3 space-y-2">
            <select
              className="bt-input max-w-xl"
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value)}
              data-testid="link-scorecard-select"
            >
              <option value="">Select scorecard…</option>
              {catalogue.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.scoringMode ? ` · ${c.scoringMode}` : ''}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy || !selectedId}
              onClick={() => void linkExisting()}
            >
              Link to this Policy
            </button>
          </div>
        ) : null}

        {mode === 'CREATE' ? (
          <div className="mt-3 space-y-2">
            <label className="block text-xs">
              <span className="font-medium text-slate-700">Scorecard name</span>
              <input
                className="bt-input mt-1 max-w-xl"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
              />
            </label>
          </div>
        ) : null}
      </CiSection>

      {mode !== 'NONE' ? (
        <CiSection
          title="Factors (Policy parameters only)"
          description="Raw weights do not need to total 100 — they are normalized for scoring. Hard rules are configured separately."
        >
          <div className="mb-2 flex flex-wrap gap-2">
            <select
              className="bt-input max-w-md"
              defaultValue=""
              onChange={(e) => {
                addFactor(e.target.value)
                e.target.value = ''
              }}
              data-testid="add-scorecard-factor"
            >
              <option value="">Add factor from Policy inventory…</option>
              {picker.map((p) => {
                const id = String(p.canonicalParameterId ?? p.parameterId ?? '')
                const label = String(p.businessName ?? id)
                return (
                  <option key={id} value={id} disabled={factors.some((f) => f.canonicalParameterId === id)}>
                    {label}
                  </option>
                )
              })}
            </select>
          </div>

          {factors.length === 0 ? (
            <p className="text-sm text-slate-500">No factors yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-xs">
                <thead className="text-slate-500">
                  <tr>
                    <th className="py-1 pr-2">Canonical parameter</th>
                    <th className="py-1 pr-2">Raw weight</th>
                    <th className="py-1 pr-2">Normalized</th>
                    <th className="py-1 pr-2">Required</th>
                    <th className="py-1 pr-2">Band points</th>
                    <th className="py-1 pr-2">Band / note</th>
                    <th className="py-1 pr-2" />
                  </tr>
                </thead>
                <tbody>
                  {factors.map((f, idx) => {
                    const prev = previewWeights.find(
                      (w) => String(w.canonicalParameterId) === f.canonicalParameterId,
                    )
                    const norm = prev?.normalizedWeight
                    return (
                      <tr key={f.canonicalParameterId} className="border-t border-slate-100">
                        <td className="py-1 pr-2 font-mono text-[11px]">{f.canonicalParameterId}</td>
                        <td className="py-1 pr-2">
                          <input
                            className="bt-input w-20"
                            value={f.rawWeight}
                            onChange={(e) => {
                              const v = e.target.value
                              setFactors((rows) =>
                                rows.map((r, i) => (i === idx ? { ...r, rawWeight: v } : r)),
                              )
                              setWeightPreview(null)
                            }}
                          />
                        </td>
                        <td className="py-1 pr-2">
                          {norm != null ? `${Number(norm).toFixed(1)}%` : '—'}
                        </td>
                        <td className="py-1 pr-2">
                          <input
                            type="checkbox"
                            checked={f.required}
                            onChange={(e) =>
                              setFactors((rows) =>
                                rows.map((r, i) =>
                                  i === idx ? { ...r, required: e.target.checked } : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-1 pr-2">
                          <input
                            className="bt-input w-20"
                            value={f.bandPointsEarned}
                            onChange={(e) =>
                              setFactors((rows) =>
                                rows.map((r, i) =>
                                  i === idx ? { ...r, bandPointsEarned: e.target.value } : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-1 pr-2">
                          <input
                            className="bt-input min-w-[10rem]"
                            placeholder="e.g. ≥750 → 100"
                            value={f.bandNote}
                            onChange={(e) =>
                              setFactors((rows) =>
                                rows.map((r, i) =>
                                  i === idx ? { ...r, bandNote: e.target.value } : r,
                                ),
                              )
                            }
                          />
                        </td>
                        <td className="py-1 pr-2">
                          <button
                            type="button"
                            className="text-rose-700 hover:underline"
                            onClick={() =>
                              setFactors((rows) => rows.filter((_, i) => i !== idx))
                            }
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy || factors.length === 0}
              onClick={() => void runWeightPreview()}
            >
              Preview normalization
            </button>
            {mode === 'CREATE' ? (
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={busy || factors.length === 0}
                onClick={() => void createAndLink()}
                data-testid="create-link-scorecard"
              >
                Create &amp; link scorecard
              </button>
            ) : null}
            {mode === 'LINK' && selectedId ? (
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={busy || factors.length === 0}
                onClick={() => void persistFactorsToLinked()}
              >
                Save factors to linked scorecard
              </button>
            ) : null}
          </div>
          {weightPreview ? (
            <p className="mt-2 text-xs text-slate-600" data-testid="weight-preview-sum">
              Normalized weights sum ≈ {normSum.toFixed(1)}% (example 5/3/2 → 50%/30%/20%).
              Required missing factors fail closed; NOT_APPLICABLE factors are excluded and weights
              renormalized.
            </p>
          ) : null}
        </CiSection>
      ) : null}

      {msg ? <p className="text-sm text-emerald-800">{msg}</p> : null}
    </div>
  )
}
