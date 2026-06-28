import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  activateAnchorRatingTemplate,
  createAnchorRatingTemplate,
  deleteAnchorRatingTemplate,
  listAnchorRatingTemplates,
  updateAnchorRatingTemplate,
  type AnchorRatingBand,
  type AnchorRatingOption,
  type AnchorRatingQuestion,
  type AnchorRatingTemplateConfig,
  type AnchorRatingTemplateResponse,
} from '@/api/anchorRatingTemplates'
import { ApiError } from '@/api/http'
import { ANCHOR_DUE_DILIGENCE_QUESTIONS } from '@/lib/anchorDueDiligenceChecklist'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import {
  BtAlert,
  DetailActions,
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  FormField,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'

function defaultConfigFromFallback(): AnchorRatingTemplateConfig {
  const questions: AnchorRatingQuestion[] = ANCHOR_DUE_DILIGENCE_QUESTIONS.map((q) => ({
    key: q.key,
    label: q.label,
    hint: q.hint,
    options: q.options.map((o, i) => ({
      value: o.value,
      label: o.label,
      score: defaultScores[q.key]?.[o.value] ?? Math.max(0, 10 - i),
    })),
  }))
  const ratingBands: AnchorRatingBand[] = [
    { rating: 'A', minScore: 80, maxScore: 100, label: 'A — Strong' },
    { rating: 'B', minScore: 65, maxScore: 79, label: 'B — Satisfactory' },
    { rating: 'C', minScore: 50, maxScore: 64, label: 'C — Refer / manual review' },
    { rating: 'D', minScore: 0, maxScore: 49, label: 'D — Not acceptable' },
  ]
  return { questions, ratingBands }
}

const defaultScores: Record<string, Record<string, number>> = {
  externalCreditRating: {
    AAA: 17, AA: 17, A: 15, BBB: 12, BB: 8, B: 5, C: 0, D: 0, NOT_RATED: 6,
  },
  financialPerformance: { STRONG: 15, SATISFACTORY: 10, WEAK: 4, DISTRESSED: 0 },
  businessVintage: { GTE_10: 12, Y5_9: 9, Y3_4: 5, LT_3: 0 },
  gstCompliance: { FULL: 12, MINOR_DELAYS: 7, SIGNIFICANT_GAPS: 0 },
  industryRisk: { LOW: 10, MEDIUM: 6, HIGH: 2 },
  adverseNewsFlow: { NONE: 12, MINOR: 6, MATERIAL: 0 },
  legalLitigation: { NONE: 10, RESOLVED: 6, ONGOING_MATERIAL: 0 },
  managementTrackRecord: { STRONG: 12, ADEQUATE: 8, CONCERNS: 2 },
}

let rid = 0
function newOption(): AnchorRatingOption {
  rid += 1
  return { value: `OPTION_${rid}`, label: 'New option', score: 0 }
}

function newQuestion(): AnchorRatingQuestion {
  rid += 1
  return { key: `question_${rid}`, label: 'New question', options: [newOption()] }
}

function newBand(): AnchorRatingBand {
  return { rating: 'X', minScore: 0, maxScore: 49, label: 'New band' }
}

function normalizeConfig(raw: AnchorRatingTemplateConfig | undefined): AnchorRatingTemplateConfig {
  if (!raw?.questions?.length) return defaultConfigFromFallback()
  return {
    questions: raw.questions.map((q) => ({
      key: q.key,
      label: q.label,
      hint: q.hint,
      options: (q.options ?? []).map((o) => ({
        value: o.value,
        label: o.label,
        score: Number(o.score) || 0,
      })),
    })),
    ratingBands: (raw.ratingBands ?? []).map((b) => ({
      rating: b.rating,
      minScore: Number(b.minScore) || 0,
      maxScore: Number(b.maxScore) || 0,
      label: b.label,
    })),
  }
}

function validateConfig(config: AnchorRatingTemplateConfig): string | null {
  if (config.questions.length === 0) return 'Add at least one question.'
  for (const q of config.questions) {
    if (!q.key.trim() || !q.label.trim()) return 'Each question needs a key and label.'
    if (q.options.length === 0) return `Question "${q.label || q.key}" needs at least one answer option.`
    for (const o of q.options) {
      if (!o.value.trim() || !o.label.trim()) return 'Each answer option needs a value and label.'
    }
  }
  if (config.ratingBands.length === 0) return 'Add at least one rating band.'
  return null
}

export function AnchorRatingTemplatesPage() {
  const [rows, setRows] = useState<AnchorRatingTemplateResponse[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AnchorRatingTemplateResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [listSearch, setListSearch] = useState('')
  const [name, setName] = useState('')
  const [version, setVersion] = useState(1)
  const [config, setConfig] = useState<AnchorRatingTemplateConfig>(defaultConfigFromFallback())

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const data = await listAnchorRatingTemplates()
      setRows(data)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load templates')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filteredRows = useMemo(() => {
    const list = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return list
    return list.filter((r) => r.name.toLowerCase().includes(q))
  }, [rows, listSearch])

  function apply(row: AnchorRatingTemplateResponse) {
    setSelected(row)
    setIsCreating(false)
    setName(row.name)
    setVersion(row.version)
    setConfig(normalizeConfig(row.configJson))
    setActionError(null)
  }

  function startCreate() {
    setSelected(null)
    setIsCreating(true)
    setName('New anchor rating template')
    setVersion(1)
    setConfig(defaultConfigFromFallback())
    setActionError(null)
  }

  function removeQuestion(qIdx: number) {
    const q = config.questions[qIdx]
    if (!q) return
    const label = q.label || q.key || `Question ${qIdx + 1}`
    if (!globalThis.confirm(`Remove question "${label}" from this template?`)) return
    setConfig((c) => ({ ...c, questions: c.questions.filter((_, i) => i !== qIdx) }))
  }

  function removeOption(qIdx: number, oIdx: number) {
    const q = config.questions[qIdx]
    if (!q || q.options.length <= 1) return
    setConfig((c) => ({
      ...c,
      questions: c.questions.map((item, i) =>
        i === qIdx ? { ...item, options: item.options.filter((_, j) => j !== oIdx) } : item,
      ),
    }))
  }

  function removeBand(idx: number) {
    if (config.ratingBands.length <= 1) return
    const band = config.ratingBands[idx]
    const label = band?.label || band?.rating || `Band ${idx + 1}`
    if (!globalThis.confirm(`Remove rating band "${label}"?`)) return
    setConfig((c) => ({ ...c, ratingBands: c.ratingBands.filter((_, i) => i !== idx) }))
  }

  function updateQuestion(idx: number, patch: Partial<AnchorRatingQuestion>) {
    setConfig((c) => ({
      ...c,
      questions: c.questions.map((q, i) => (i === idx ? { ...q, ...patch } : q)),
    }))
  }

  function updateOption(qIdx: number, oIdx: number, patch: Partial<AnchorRatingOption>) {
    setConfig((c) => ({
      ...c,
      questions: c.questions.map((q, i) =>
        i === qIdx
          ? { ...q, options: q.options.map((o, j) => (j === oIdx ? { ...o, ...patch } : o)) }
          : q,
      ),
    }))
  }

  async function onSave() {
    const validation = validateConfig(config)
    if (validation) {
      setActionError(validation)
      return
    }
    setSaving(true)
    setActionError(null)
    try {
      const payload = {
        name: name.trim(),
        version,
        active: selected?.active ?? false,
        configJson: config,
      }
      if (isCreating) {
        const created = await createAnchorRatingTemplate(payload)
        await load()
        apply(created)
        setIsCreating(false)
      } else if (selected) {
        const updated = await updateAnchorRatingTemplate(selected.id, payload)
        await load()
        apply(updated)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onActivate() {
    if (!selected) return
    setSaving(true)
    setActionError(null)
    try {
      const updated = await activateAnchorRatingTemplate(selected.id)
      await load()
      apply(updated)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Activate failed')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete() {
    if (!selected || selected.active) return
    if (!globalThis.confirm(`Delete template "${selected.name}"?`)) return
    setSaving(true)
    setActionError(null)
    try {
      await deleteAnchorRatingTemplate(selected.id)
      setSelected(null)
      setIsCreating(false)
      await load()
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Delete failed')
    } finally {
      setSaving(false)
    }
  }

  const showForm = selected !== null || isCreating
  const isActive = Boolean(selected?.active)

  return (
    <div>
      <PageHeader
        title="Anchor rating templates"
        description="Configure due-diligence questions, answer scores, and rating bands for invoice discounting anchor onboarding. Only the active template drives the anchor rating checklist."
      />

      {loading ? <LoadingState label="Loading anchor rating templates…" /> : null}
      {loadError ? <ErrorState message={loadError} /> : null}

      {rows && !loading ? (
        <MasterDetailLayout>
          <MasterListPanel
            title="Templates"
            count={filteredRows.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search templates…"
            action={
              <button type="button" onClick={startCreate} className="bt-btn bt-btn-primary bt-btn-sm">
                New template
              </button>
            }
          >
            {filteredRows.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-slate-500">No templates match your search.</p>
            ) : (
              filteredRows.map((row) => (
                <MasterListItem
                  key={row.id}
                  active={selected?.id === row.id && !isCreating}
                  onClick={() => apply(row)}
                  avatar={row.name}
                  title={row.name}
                  subtitle={`Version ${row.version} · ${row.configJson?.questions?.length ?? 0} questions`}
                  meta={
                    row.active ? (
                      <span className="bt-badge bt-badge-green">Active</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">Inactive</span>
                    )
                  }
                />
              ))
            )}
          </MasterListPanel>

          {showForm ? (
            <DetailPanel
              title={isCreating ? 'New template' : name}
              description="Define checklist questions with scored answers and score-to-rating bands. Activate when ready — only one template can be active."
              badge={
                !isCreating && selected ? (
                  isActive ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                ) : undefined
              }
              footer={
                <DetailActions>
                  <button type="button" disabled={saving} onClick={() => void onSave()} className="bt-btn bt-btn-primary">
                    {saving ? 'Saving…' : 'Save template'}
                  </button>
                  {selected && !isActive ? (
                    <button type="button" disabled={saving} onClick={() => void onActivate()} className="bt-btn bt-btn-secondary">
                      Activate
                    </button>
                  ) : null}
                  {selected && !isActive ? (
                    <button type="button" disabled={saving} onClick={() => void onDelete()} className="bt-btn bt-btn-danger">
                      Delete
                    </button>
                  ) : null}
                </DetailActions>
              }
            >
              {actionError ? <BtAlert tone="error" className="mb-4">{actionError}</BtAlert> : null}

              <DetailSection title="Template details">
                <div className="bt-form-grid bt-form-grid-2">
                  <FormField label="Template name">
                    <input className="bt-input w-full" value={name} onChange={(e) => setName(e.target.value)} />
                  </FormField>
                  <FormField label="Version" hint="Increment when you publish material scoring changes.">
                    <input
                      type="number"
                      min={1}
                      className="bt-input w-full"
                      value={version}
                      onChange={(e) => setVersion(parseInt(e.target.value, 10) || 1)}
                    />
                  </FormField>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <span className="bt-tag">{config.questions.length} questions</span>
                  <span className="bt-tag">{config.ratingBands.length} rating bands</span>
                </div>
              </DetailSection>

              <DetailSection
                title="Checklist questions"
                description="Each question appears on the anchor rating checklist. Answer options carry point scores used to compute the final rating."
              >
                <div className="mb-3 flex justify-end">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => setConfig((c) => ({ ...c, questions: [...c.questions, newQuestion()] }))}
                  >
                    Add question
                  </button>
                </div>

                {config.questions.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50/80 px-4 py-8 text-center text-sm text-slate-500">
                    No questions yet. Add at least one question for this template to be usable.
                  </div>
                ) : (
                  <div className="space-y-4">
                    {config.questions.map((q, qIdx) => (
                      <div key={`${q.key}-${qIdx}`} className="bt-config-card">
                        <div className="bt-config-card-header">
                          <div>
                            <span className="bt-config-card-kicker">Question {qIdx + 1}</span>
                            <h4 className="bt-config-card-title">{q.label || 'Untitled question'}</h4>
                            <p className="bt-config-card-subtitle font-mono text-xs">{q.key || 'no-key'}</p>
                          </div>
                          <button
                            type="button"
                            className="bt-btn bt-btn-danger bt-btn-sm"
                            onClick={() => removeQuestion(qIdx)}
                          >
                            Remove question
                          </button>
                        </div>

                        <div className="bt-form-grid bt-form-grid-2">
                          <FormField label="Question key" hint="Stable identifier stored with answers.">
                            <input
                              className="bt-input w-full font-mono text-xs"
                              value={q.key}
                              onChange={(e) => updateQuestion(qIdx, { key: e.target.value })}
                            />
                          </FormField>
                          <FormField label="Question label">
                            <input
                              className="bt-input w-full"
                              value={q.label}
                              onChange={(e) => updateQuestion(qIdx, { label: e.target.value })}
                            />
                          </FormField>
                          <FormField label="Hint (optional)" className="bt-form-grid-span-2">
                            <input
                              className="bt-input w-full"
                              value={q.hint ?? ''}
                              onChange={(e) => updateQuestion(qIdx, { hint: e.target.value })}
                            />
                          </FormField>
                        </div>

                        <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200">
                          <table className="bt-table w-full text-sm">
                            <thead>
                              <tr>
                                <th>Value</th>
                                <th>Display label</th>
                                <th className="text-right w-24">Score</th>
                                <th className="w-20" />
                              </tr>
                            </thead>
                            <tbody>
                              {q.options.map((o, oIdx) => (
                                <tr key={`${o.value}-${oIdx}`}>
                                  <td>
                                    <input
                                      className="bt-input w-full font-mono text-xs"
                                      value={o.value}
                                      onChange={(e) => updateOption(qIdx, oIdx, { value: e.target.value })}
                                    />
                                  </td>
                                  <td>
                                    <input
                                      className="bt-input w-full"
                                      value={o.label}
                                      onChange={(e) => updateOption(qIdx, oIdx, { label: e.target.value })}
                                    />
                                  </td>
                                  <td className="text-right">
                                    <input
                                      type="number"
                                      className="bt-input w-20 ml-auto text-right"
                                      value={o.score}
                                      onChange={(e) =>
                                        updateOption(qIdx, oIdx, { score: parseInt(e.target.value, 10) || 0 })
                                      }
                                    />
                                  </td>
                                  <td className="text-right">
                                    <button
                                      type="button"
                                      className="text-xs font-medium text-slate-500 hover:text-red-600 disabled:opacity-40"
                                      disabled={q.options.length <= 1}
                                      title={q.options.length <= 1 ? 'At least one option is required' : 'Remove option'}
                                      onClick={() => removeOption(qIdx, oIdx)}
                                    >
                                      Remove
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                        <button
                          type="button"
                          className="mt-2 text-xs font-semibold text-[var(--bt-orange)] hover:underline"
                          onClick={() => updateQuestion(qIdx, { options: [...q.options, newOption()] })}
                        >
                          + Add answer option
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </DetailSection>

              <DetailSection
                title="Rating bands"
                description="Total checklist score maps to a letter rating. Bands are evaluated from highest min score downward."
              >
                <div className="mb-3 flex justify-end">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => setConfig((c) => ({ ...c, ratingBands: [...c.ratingBands, newBand()] }))}
                  >
                    Add band
                  </button>
                </div>
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                  <table className="bt-table w-full text-sm">
                    <thead>
                      <tr>
                        <th className="w-20">Rating</th>
                        <th>Label</th>
                        <th className="text-right w-28">Min score</th>
                        <th className="text-right w-28">Max score</th>
                        <th className="w-20" />
                      </tr>
                    </thead>
                    <tbody>
                      {config.ratingBands.map((b, idx) => (
                        <tr key={idx}>
                          <td>
                            <input
                              className="bt-input w-full"
                              value={b.rating}
                              onChange={(e) =>
                                setConfig((c) => ({
                                  ...c,
                                  ratingBands: c.ratingBands.map((x, i) =>
                                    i === idx ? { ...x, rating: e.target.value } : x,
                                  ),
                                }))
                              }
                            />
                          </td>
                          <td>
                            <input
                              className="bt-input w-full"
                              value={b.label}
                              onChange={(e) =>
                                setConfig((c) => ({
                                  ...c,
                                  ratingBands: c.ratingBands.map((x, i) =>
                                    i === idx ? { ...x, label: e.target.value } : x,
                                  ),
                                }))
                              }
                            />
                          </td>
                          <td className="text-right">
                            <input
                              type="number"
                              className="bt-input w-24 ml-auto text-right"
                              value={b.minScore}
                              onChange={(e) =>
                                setConfig((c) => ({
                                  ...c,
                                  ratingBands: c.ratingBands.map((x, i) =>
                                    i === idx ? { ...x, minScore: parseInt(e.target.value, 10) || 0 } : x,
                                  ),
                                }))
                              }
                            />
                          </td>
                          <td className="text-right">
                            <input
                              type="number"
                              className="bt-input w-24 ml-auto text-right"
                              value={b.maxScore}
                              onChange={(e) =>
                                setConfig((c) => ({
                                  ...c,
                                  ratingBands: c.ratingBands.map((x, i) =>
                                    i === idx ? { ...x, maxScore: parseInt(e.target.value, 10) || 0 } : x,
                                  ),
                                }))
                              }
                            />
                          </td>
                          <td className="text-right">
                            <button
                              type="button"
                              className="text-xs font-medium text-slate-500 hover:text-red-600 disabled:opacity-40"
                              disabled={config.ratingBands.length <= 1}
                              onClick={() => removeBand(idx)}
                            >
                              Remove
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </DetailSection>
            </DetailPanel>
          ) : (
            <DetailEmptyState
              title="Select a template"
              description="Choose a template from the list or create a new one to edit questions, scores, and rating bands."
              action={
                <button type="button" onClick={startCreate} className="bt-btn bt-btn-primary">
                  New template
                </button>
              }
            />
          )}
        </MasterDetailLayout>
      ) : null}
    </div>
  )
}
