import { useCallback, useEffect, useRef, useState } from 'react'
import {
  answerCategoryDisambiguation,
  autoSelectCategory,
  evaluateCategorySelection,
  selectCategory,
  type EligibilityResult,
} from '../../api/categorySelection'
import { PinnedWorkflowSummary } from '../workflow/PinnedWorkflowSummary'
import { pinnedWorkflowDisplayFromCategoryHandoff } from '@/lib/workflow/pinnedWorkflowDisplay'
import {
  buildCategorySelectedResult,
  hasCategoryPin,
  mergeCategoryEvaluateResult,
  shouldPreservePinOnEvaluateError,
  type CategoryPinnedSelection,
} from '@/lib/category/categorySelectionPanelState'

type Props = {
  applicationId: string
  actor?: string
  actorRole?: 'CUSTOMER' | 'RM' | 'SYSTEM'
  /** Persisted application category pin — authoritative on revisit; never displaced by loading/error. */
  pinnedSelection?: CategoryPinnedSelection | null
  /** Staging-only: evaluate DRAFT Categories without activating Day-1 seeds. */
  allowDraftSimulation?: boolean
  onSelected?: (result: EligibilityResult) => void
}

/**
 * Customer/RM Category selection step — progressive question then proposition choice.
 * Does not show Policy/Workflow UUIDs or credit thresholds.
 */
export function CategorySelectionPanel({
  applicationId,
  actor = 'user',
  actorRole = 'CUSTOMER',
  pinnedSelection = null,
  allowDraftSimulation = false,
  onSelected,
}: Props) {
  const [result, setResult] = useState<EligibilityResult | null>(() =>
    hasCategoryPin(pinnedSelection) ? buildCategorySelectedResult(pinnedSelection) : null,
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [backgroundRefresh, setBackgroundRefresh] = useState(false)
  const requestGenerationRef = useRef(0)
  const resultRef = useRef<EligibilityResult | null>(result)
  const pinnedSelectionRef = useRef(pinnedSelection)

  useEffect(() => {
    resultRef.current = result
  }, [result])

  useEffect(() => {
    pinnedSelectionRef.current = pinnedSelection
  }, [pinnedSelection])

  useEffect(() => {
    if (!hasCategoryPin(pinnedSelection)) return
    setResult((prev) => {
      if (prev?.state === 'CATEGORY_SELECTED') return prev
      return buildCategorySelectedResult(pinnedSelection)
    })
    setError(null)
  }, [pinnedSelection])

  const refresh = useCallback(async () => {
    const generation = ++requestGenerationRef.current
    const pinKnown = hasCategoryPin(pinnedSelectionRef.current)
    const hasPinnedDisplay =
      pinKnown || resultRef.current?.state === 'CATEGORY_SELECTED'

    if (!hasPinnedDisplay) {
      setBusy(true)
    } else {
      setBackgroundRefresh(true)
    }
    setError(null)

    try {
      const incoming = await evaluateCategorySelection(applicationId, allowDraftSimulation)
      if (generation !== requestGenerationRef.current) return

      setResult((prev) => {
        const merged = mergeCategoryEvaluateResult(prev, incoming, generation, requestGenerationRef.current)
        return merged ?? prev
      })

      const effective = mergeCategoryEvaluateResult(
        resultRef.current,
        incoming,
        generation,
        requestGenerationRef.current,
      )

      if (incoming.state === 'AUTO_SINGLE_MATCH') {
        const selected = await autoSelectCategory(applicationId, actor, allowDraftSimulation)
        if (generation !== requestGenerationRef.current) return
        setResult(selected)
        onSelected?.(selected)
      } else if (incoming.state === 'CATEGORY_SELECTED' || effective?.state === 'CATEGORY_SELECTED') {
        onSelected?.(incoming.state === 'CATEGORY_SELECTED' ? incoming : effective!)
      }
    } catch (e) {
      if (generation !== requestGenerationRef.current) return
      if (
        shouldPreservePinOnEvaluateError(pinnedSelectionRef.current, resultRef.current)
      ) {
        setError(null)
        return
      }
      setError(e instanceof Error ? e.message : 'Category evaluation failed')
    } finally {
      if (generation === requestGenerationRef.current) {
        setBusy(false)
        setBackgroundRefresh(false)
      }
    }
  }, [applicationId, allowDraftSimulation, actor, onSelected])

  useEffect(() => {
    if (hasCategoryPin(pinnedSelection)) return
    void refresh()
  }, [refresh, pinnedSelection])

  const onAnswer = async (questionId: string, answerValue: string) => {
    setBusy(true)
    setError(null)
    try {
      let r = await answerCategoryDisambiguation(
        applicationId,
        { questionId, answerValue, actor, actorRole },
        allowDraftSimulation,
      )
      if (r.state === 'AUTO_SINGLE_MATCH') {
        r = await autoSelectCategory(applicationId, actor, allowDraftSimulation)
        onSelected?.(r)
      }
      setResult(r)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Answer failed')
    } finally {
      setBusy(false)
    }
  }

  const onPick = async (categoryId: string) => {
    setBusy(true)
    setError(null)
    try {
      const r = await selectCategory(
        applicationId,
        {
          categoryId,
          actor,
          actorRole,
          selectionSource: actorRole === 'RM' ? 'RM_SELECTED' : 'CUSTOMER_SELECTED',
          reason: actorRole === 'RM' ? 'RM assisted proposition selection' : undefined,
        },
        allowDraftSimulation,
      )
      setResult(r)
      onSelected?.(r)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Selection failed')
    } finally {
      setBusy(false)
    }
  }

  const showInitialLoading = !result && busy
  const pinnedDisplay =
    result?.state === 'CATEGORY_SELECTED' && result.selected
      ? pinnedWorkflowDisplayFromCategoryHandoff(result.selected)
      : null

  if (showInitialLoading) {
    return <p className="text-sm text-slate-600">Checking lending propositions…</p>
  }

  if (error && !pinnedDisplay) {
    return <p className="text-sm text-red-700">{error}</p>
  }

  if (!result && !pinnedDisplay) {
    return null
  }

  if (result?.state === 'CATEGORY_SELECTED' && result.selected) {
    return (
      <div className="space-y-2">
        {backgroundRefresh ? (
          <p className="text-xs text-slate-500" aria-live="polite">
            Refreshing lending proposition…
          </p>
        ) : null}
        {error ? <p className="text-xs text-amber-700">{error}</p> : null}
        <div className="rounded border border-emerald-200 bg-emerald-50 p-4">
          <h3 className="text-sm font-semibold text-emerald-900">Lending proposition selected</h3>
          <p className="mt-1 text-sm text-emerald-800">
            {result.selected.categoryDisplayName ?? result.selected.categoryCode} (v
            {result.selected.categoryVersion})
          </p>
          {pinnedDisplay ? (
            <div className="mt-2">
              <PinnedWorkflowSummary display={pinnedDisplay} tone="success" />
            </div>
          ) : null}
          <p className="mt-2 text-xs text-emerald-700">
            Source: {result.selected.selectionSource}. Policy and Workflow versions locked for this
            application. Underwriting has not been run.
          </p>
        </div>
      </div>
    )
  }

  if (result?.state === 'NO_ELIGIBLE_CATEGORY') {
    return (
      <div className="rounded border border-amber-200 bg-amber-50 p-4">
        <h3 className="text-sm font-semibold text-amber-900">No matching lending proposition</h3>
        <p className="mt-1 text-sm text-amber-800">
          Adjust product or amount, or ask your relationship manager for help.
        </p>
        {result.noMatchReasons && result.noMatchReasons.length > 0 && (
          <p className="mt-2 text-xs text-amber-700">Reasons: {result.noMatchReasons.join(', ')}</p>
        )}
      </div>
    )
  }

  if (result?.state === 'DISAMBIGUATION_REQUIRED' && result.nextQuestion) {
    const q = result.nextQuestion
    return (
      <div className="space-y-3 rounded border border-slate-200 bg-white p-4">
        <h3 className="text-sm font-semibold text-slate-900">{q.prompt}</h3>
        <p className="text-xs text-slate-500">This helps choose the right lending journey for you.</p>
        <div className="flex flex-col gap-2">
          {q.options.map((o) => (
            <button
              key={o.value}
              type="button"
              disabled={busy}
              onClick={() => void onAnswer(q.questionId, o.value)}
              className="rounded border border-slate-300 px-3 py-2 text-left text-sm hover:border-slate-500"
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>
    )
  }

  if (result?.state === 'EXPLICIT_PROPOSITION_SELECTION_REQUIRED') {
    const cards = result.propositions?.length ? result.propositions : result.eligible
    return (
      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-900">Choose a lending proposition</h3>
        <p className="text-xs text-slate-500">More than one option fits your application.</p>
        {error ? <p className="text-sm text-red-700">{error}</p> : null}
        <div className="grid gap-3 md:grid-cols-2">
          {cards.map((p) => (
            <button
              key={p.categoryId}
              type="button"
              disabled={busy}
              onClick={() => void onPick(p.categoryId)}
              className="rounded border border-slate-300 bg-white p-4 text-left hover:border-slate-600"
            >
              <div className="text-sm font-semibold text-slate-900">
                {'name' in p ? p.name : (p as { customerFacingName?: string }).customerFacingName}
              </div>
              {p.shortDescription && (
                <p className="mt-1 text-xs text-slate-600">{p.shortDescription}</p>
              )}
              {'requirementsSummary' in p && p.requirementsSummary && (
                <p className="mt-2 text-xs text-slate-500">{p.requirementsSummary}</p>
              )}
            </button>
          ))}
        </div>
      </div>
    )
  }

  return null
}
