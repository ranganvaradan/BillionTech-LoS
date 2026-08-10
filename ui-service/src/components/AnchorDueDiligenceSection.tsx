import { useCallback, useEffect, useMemo, useState } from 'react'
import { getKycOutcome } from '@/api/kyc'
import {
  approveAnchorManualUnderwritingFlow,
  completeAnchorUnderwritingFlow,
  getAnchorDueDiligenceFlow,
  rejectAnchorManualUnderwritingFlow,
  saveAnchorDueDiligenceFlow,
} from '@/api/flow'
import { downloadDocumentAsBlob, listDocuments, uploadDocument } from '@/api/documents'
import { ApiError } from '@/api/http'
import { ErrorState } from '@/components/ErrorState'
import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { loanProductLabel } from '@/catalog/loanProducts'
import { formatMoney } from '@/lib/format'
import {
  anchorRatingLabel,
  ANCHOR_DUE_DILIGENCE_QUESTIONS,
  creditRatingTone,
  mapQuestionsFromApi,
  type DueDiligenceQuestion,
} from '@/lib/anchorDueDiligenceChecklist'
import { readAnchorDueDiligence } from '@/lib/invoiceDiscountingFlow'
import type { ApplicationResponse } from '@/types/application'
import type { DocumentResponse } from '@/types/document'

const inputCls = 'bt-input w-full text-sm disabled:cursor-not-allowed disabled:bg-slate-50'
const labelCls = 'bt-label'

function anchorDdDocumentType(questionKey: string): string {
  return `ANCHOR_DD_${questionKey}`
}

function statusChip(status: string): { label: string; cls: string } {
  const u = status.toUpperCase()
  if (u === 'SANCTIONED') return { label: status, cls: 'bt-section-card__chip bt-section-card__chip--success' }
  if (u === 'SANCTION_PENDING') return { label: status, cls: 'bt-section-card__chip bt-section-card__chip--info' }
  if (u === 'REJECTED') return { label: status, cls: 'bt-section-card__chip bt-section-card__chip--danger' }
  if (u === 'UNDERWRITING') return { label: status, cls: 'bt-section-card__chip bt-section-card__chip--warning' }
  return { label: status, cls: 'bt-section-card__chip' }
}

function QuestionComment({
  questionKey,
  comment,
  disabled,
  onSave,
  onRemove,
}: {
  questionKey: string
  comment: string
  disabled: boolean
  onSave: (key: string, value: string) => void
  onRemove: (key: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(comment)
  const hasComment = Boolean(comment.trim())

  useEffect(() => {
    if (!editing) setDraft(comment)
  }, [comment, editing])

  if (!editing && hasComment) {
    return (
      <div className="mt-3 rounded-lg border border-slate-200/90 bg-slate-50 px-3.5 py-3">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Team note</p>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-700 whitespace-pre-wrap">{comment}</p>
          </div>
          {!disabled ? (
            <div className="flex shrink-0 gap-2">
              <button
                type="button"
                className="text-xs font-medium text-blue-600 hover:text-blue-800"
                onClick={() => {
                  setDraft(comment)
                  setEditing(true)
                }}
              >
                Edit
              </button>
              <button
                type="button"
                className="text-xs font-medium text-slate-500 hover:text-red-600"
                onClick={() => onRemove(questionKey)}
              >
                Remove
              </button>
            </div>
          ) : null}
        </div>
      </div>
    )
  }

  if (!editing && !hasComment) {
    if (disabled) return null
    return (
      <button
        type="button"
        className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-blue-600"
        onClick={() => setEditing(true)}
      >
        <span aria-hidden>+</span> Add team note (optional)
      </button>
    )
  }

  return (
    <div className="mt-3 rounded-lg border border-dashed border-slate-200 bg-white px-3.5 py-3">
      <label className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
        Team note <span className="font-normal normal-case text-slate-400">(optional — not scored)</span>
      </label>
      <textarea
        className={`${inputCls} mt-2 min-h-[72px] resize-y`}
        value={draft}
        disabled={disabled}
        placeholder="Why this response was chosen, context for reviewers…"
        onChange={(e) => setDraft(e.target.value)}
      />
      <div className="mt-2 flex flex-wrap gap-2">
        <button
          type="button"
          disabled={disabled}
          className="bt-btn bt-btn-secondary bt-btn-sm"
          onClick={() => {
            const trimmed = draft.trim()
            if (trimmed) onSave(questionKey, trimmed)
            else onRemove(questionKey)
            setEditing(false)
          }}
        >
          Save note
        </button>
        <button
          type="button"
          disabled={disabled}
          className="bt-btn bt-btn-secondary bt-btn-sm !bg-transparent !border-transparent text-slate-500"
          onClick={() => {
            setDraft(comment)
            setEditing(false)
          }}
        >
          Cancel
        </button>
      </div>
    </div>
  )
}

export function AnchorDueDiligenceSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void
}) {
  const stored = readAnchorDueDiligence(app)
  const [questions, setQuestions] = useState<DueDiligenceQuestion[]>(ANCHOR_DUE_DILIGENCE_QUESTIONS)
  const [ratingBands, setRatingBands] = useState<{ rating: string; label: string }[]>([])
  const [answers, setAnswers] = useState<Record<string, string>>(() => stored.answers ?? {})
  const [comments, setComments] = useState<Record<string, string>>(() => stored.comments ?? {})
  const [creditRating, setCreditRating] = useState(stored.creditRating ?? '')
  const [score, setScore] = useState<number | null>(stored.score ?? null)
  const [kycPass, setKycPass] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [savedMsg, setSavedMsg] = useState<string | null>(null)
  const [docsByQuestion, setDocsByQuestion] = useState<Record<string, DocumentResponse[]>>({})
  const [uploadBusyKey, setUploadBusyKey] = useState<string | null>(null)

  const answeredCount = useMemo(
    () => questions.filter((q) => Boolean(answers[q.key]?.trim())).length,
    [answers, questions],
  )
  const totalQuestions = questions.length
  const progressPct = totalQuestions > 0 ? Math.round((answeredCount / totalQuestions) * 100) : 0

  const applyBlock = useCallback((block: Record<string, unknown>) => {
    if (block.questions) setQuestions(mapQuestionsFromApi(block.questions))
    if (Array.isArray(block.ratingBands)) {
      setRatingBands(
        (block.ratingBands as Record<string, unknown>[]).map((b) => ({
          rating: String(b.rating ?? ''),
          label: String(b.label ?? b.rating ?? ''),
        })),
      )
    }
    if (block.answers && typeof block.answers === 'object') {
      setAnswers(block.answers as Record<string, string>)
    }
    if (block.comments && typeof block.comments === 'object') {
      setComments(block.comments as Record<string, string>)
    }
    if (block.creditRating != null) setCreditRating(String(block.creditRating))
    if (block.score != null) setScore(Number(block.score))
  }, [])

  const refreshDocs = useCallback(async () => {
    try {
      const docs = await listDocuments(applicationId)
      const byQ: Record<string, DocumentResponse[]> = {}
      for (const d of docs) {
        const t = d.documentType ?? ''
        if (!t.startsWith('ANCHOR_DD_')) continue
        const key = t.slice('ANCHOR_DD_'.length)
        if (!byQ[key]) byQ[key] = []
        byQ[key].push(d)
      }
      setDocsByQuestion(byQ)
    } catch {
      /* optional */
    }
  }, [applicationId])

  const loadKyc = useCallback(async () => {
    try {
      const o = await getKycOutcome(applicationId)
      setKycPass(String((o as { outcome?: unknown }).outcome ?? '').toUpperCase() === 'PASS')
    } catch {
      setKycPass(false)
    }
  }, [applicationId])

  const refreshDd = useCallback(async () => {
    try {
      const block = await getAnchorDueDiligenceFlow(applicationId)
      applyBlock(block)
    } catch {
      /* use app snapshot */
    }
  }, [applicationId, applyBlock])

  useEffect(() => {
    void loadKyc()
    void refreshDd()
    void refreshDocs()
  }, [loadKyc, refreshDd, refreshDocs, app.updatedAt])

  async function onUploadQuestionDoc(questionKey: string, file: File | null) {
    if (!file) return
    setUploadBusyKey(questionKey)
    setError(null)
    try {
      await uploadDocument(applicationId, file, anchorDdDocumentType(questionKey))
      await refreshDocs()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Document upload failed')
    } finally {
      setUploadBusyKey(null)
    }
  }

  async function onViewDoc(doc: DocumentResponse) {
    try {
      const { blob, suggestedFileName } = await downloadDocumentAsBlob(doc.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.target = '_blank'
      a.rel = 'noopener noreferrer'
      a.download = suggestedFileName || doc.fileName || 'document'
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not open document')
    }
  }

  async function persistChecklist() {
    return saveAnchorDueDiligenceFlow(applicationId, { answers, comments })
  }

  async function onSave() {
    setBusy(true)
    setError(null)
    setSavedMsg(null)
    try {
      const block = await persistChecklist()
      applyBlock(block)
      setSavedMsg('Anchor rating updated from checklist responses.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save checklist')
    } finally {
      setBusy(false)
    }
  }

  async function onCompleteUnderwriting() {
    setBusy(true)
    setError(null)
    try {
      await persistChecklist()
      await completeAnchorUnderwritingFlow(applicationId)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Underwriting could not be completed')
    } finally {
      setBusy(false)
    }
  }

  async function onManualResolve(approve: boolean) {
    setBusy(true)
    setError(null)
    try {
      if (approve) {
        await approveAnchorManualUnderwritingFlow(applicationId)
      } else {
        await rejectAnchorManualUnderwritingFlow(applicationId)
      }
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const pendingManual = app.status === 'UNDERWRITING' && app.creditDecision === 'MANUAL_REVIEW'
  const decisionDone =
    app.status === 'SANCTION_PENDING' ||
    app.status === 'SANCTIONED' ||
    app.status === 'REJECTED' ||
    (Boolean(app.creditDecision) && app.creditDecision !== 'MANUAL_REVIEW')

  const status = statusChip(app.status)
  const ratingTone = creditRatingTone(creditRating)

  return (
    <div className="space-y-5">
      <div className="bt-section-card bt-section-card--hero p-4 text-sm text-slate-800">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-slate-900">Anchor rating</h2>
            <p className="mt-1 text-xs text-slate-600">
              Due diligence checklist for anchor onboarding — invoice discounting.
            </p>
          </div>
          <span className={status.cls}>{status.label}</span>
        </div>
        <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-xs">
          <div>
            <dt className="text-slate-500">Application</dt>
            <dd className="font-mono font-medium text-slate-900">{app.applicationNumber}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Product</dt>
            <dd className="font-medium text-slate-900">{loanProductLabel(app.loanProduct)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Requested limit</dt>
            <dd className="font-medium tabular-nums text-slate-900">{formatMoney(app.requestedAmount)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">KYC</dt>
            <dd className="font-medium text-slate-900">
              {kycPass === null ? 'Checking…' : kycPass ? 'Pass' : 'Incomplete / failed'}
            </dd>
          </div>
        </dl>
      </div>

      {(creditRating || score != null) && (
        <AppSectionCard
          tone={ratingTone === 'default' ? 'violet' : ratingTone}
          title="Computed anchor rating"
          badge={
            creditRating ? (
              <span className={`bt-section-card__chip bt-section-card__chip--${ratingTone === 'default' ? 'info' : ratingTone}`}>
                Rating {creditRating}
              </span>
            ) : undefined
          }
        >
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-lg border border-white/60 bg-white/70 p-3">
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Rating</dt>
              <dd className="mt-1 text-lg font-semibold text-slate-900">
                {anchorRatingLabel(creditRating, ratingBands)}
              </dd>
            </div>
            <div className="rounded-lg border border-white/60 bg-white/70 p-3">
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Score</dt>
              <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-900">{score ?? '—'}</dd>
            </div>
            <div className="rounded-lg border border-white/60 bg-white/70 p-3">
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Decision</dt>
              <dd className="mt-1 text-lg font-semibold text-slate-900">{app.creditDecision ?? '—'}</dd>
            </div>
          </div>
        </AppSectionCard>
      )}

      <AppSectionCard
        tone="default"
        title="Due diligence checklist"
        badge={
          <span className="bt-section-card__chip">
            {answeredCount}/{totalQuestions} answered
          </span>
        }
        actions={
          !decisionDone ? (
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={busy || decisionDone || kycPass !== true}
                onClick={() => void onSave()}
                className="bt-btn bt-btn-secondary bt-btn-sm disabled:opacity-50"
              >
                Save & compute
              </button>
              <button
                type="button"
                disabled={busy || kycPass !== true || answeredCount < totalQuestions}
                onClick={() => void onCompleteUnderwriting()}
                className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
              >
                Complete anchor rating
              </button>
            </div>
          ) : null
        }
      >
        {kycPass === false ? (
          <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50/80 px-3 py-2 text-sm text-amber-950">
            Complete KYC before filling the due diligence checklist.
          </div>
        ) : null}
        {error ? (
          <div className="mb-4">
            <ErrorState message={error} />
          </div>
        ) : null}
        {savedMsg ? (
          <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50/80 px-3 py-2 text-sm text-emerald-900">
            {savedMsg}
          </div>
        ) : null}

        <div className="mb-4">
          <div className="mb-1 flex justify-between text-[11px] font-medium text-slate-500">
            <span>Checklist progress</span>
            <span>{progressPct}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-[var(--bt-orange)] transition-all duration-300"
              style={{ width: `${progressPct}%` }}
            />
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          {questions.map((q, idx) => {
            const filled = Boolean(answers[q.key]?.trim())
            return (
              <div
                key={q.key}
                className={`rounded-xl border p-4 transition-colors ${
                  filled ? 'border-indigo-200/80 bg-indigo-50/30' : 'border-slate-200 bg-white'
                }`}
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                    Q{idx + 1}
                  </span>
                  {filled ? (
                    <span className="bt-section-card__chip bt-section-card__chip--success text-[10px]">Done</span>
                  ) : null}
                </div>
                <label className="block">
                  <span className={labelCls}>{q.label}</span>
                  {q.hint ? <span className="mt-0.5 block text-[11px] text-slate-500">{q.hint}</span> : null}
                  <select
                    className={`${inputCls} mt-2`}
                    value={answers[q.key] ?? ''}
                    disabled={decisionDone || busy}
                    onChange={(e) => setAnswers({ ...answers, [q.key]: e.target.value })}
                  >
                    <option value="">Select response…</option>
                    {q.options.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </label>
                <QuestionComment
                  questionKey={q.key}
                  comment={comments[q.key] ?? ''}
                  disabled={decisionDone || busy}
                  onSave={(key, value) => setComments((prev) => ({ ...prev, [key]: value }))}
                  onRemove={(key) =>
                    setComments((prev) => {
                      const next = { ...prev }
                      delete next[key]
                      return next
                    })
                  }
                />
                <div className="mt-3 border-t border-slate-100 pt-3">
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Supporting document <span className="font-normal normal-case text-slate-400">(optional)</span>
                  </p>
                  {(docsByQuestion[q.key] ?? []).length > 0 ? (
                    <ul className="mt-1.5 space-y-1">
                      {(docsByQuestion[q.key] ?? []).map((d) => (
                        <li key={d.id} className="flex flex-wrap items-center gap-2 text-xs text-slate-700">
                          <span className="truncate font-medium">{d.fileName || d.documentType}</span>
                          <button
                            type="button"
                            className="font-medium text-blue-600 hover:text-blue-800"
                            onClick={() => void onViewDoc(d)}
                          >
                            View
                          </button>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-1 text-[11px] text-slate-400">No file uploaded for this question yet.</p>
                  )}
                  {!decisionDone ? (
                    <label className="mt-2 inline-flex cursor-pointer items-center gap-2 text-xs font-medium text-slate-600 hover:text-blue-700">
                      <span>{uploadBusyKey === q.key ? 'Uploading…' : 'Upload file'}</span>
                      <input
                        type="file"
                        className="sr-only"
                        disabled={busy || uploadBusyKey === q.key}
                        onChange={(e) => {
                          const f = e.target.files?.[0] ?? null
                          e.target.value = ''
                          void onUploadQuestionDoc(q.key, f)
                        }}
                      />
                    </label>
                  ) : null}
                </div>
              </div>
            )
          })}
        </div>

        {!decisionDone && kycPass === true ? (
          <div className="mt-5 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
            <button type="button" disabled={busy} onClick={() => void onSave()} className="bt-btn bt-btn-secondary disabled:opacity-50">
              Save & compute rating
            </button>
            <button
              type="button"
              disabled={busy || answeredCount < totalQuestions}
              onClick={() => void onCompleteUnderwriting()}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              Complete anchor rating
            </button>
          </div>
        ) : null}
      </AppSectionCard>

      {pendingManual ? (
        <AppSectionCard tone="warning" title="Manual review required">
          <p className="mb-4 text-sm text-slate-700">
            Rating <strong>C</strong> — credit team must approve or reject before sanction.
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => void onManualResolve(true)}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              Approve for sanction
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void onManualResolve(false)}
              className="bt-btn bt-btn-danger disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        </AppSectionCard>
      ) : null}

      {app.status === 'SANCTION_PENDING' ? (
        <AppSectionCard tone="success" title="Anchor rating complete">
          <p className="text-sm text-slate-700">
            Anchor has been pushed to PLP. Open the <strong>Sanction</strong> tab to set the program limit and finish
            PLP program setup.
          </p>
        </AppSectionCard>
      ) : null}
    </div>
  )
}
