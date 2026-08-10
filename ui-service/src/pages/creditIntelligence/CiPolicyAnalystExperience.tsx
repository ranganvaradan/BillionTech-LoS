import { useEffect, useMemo, useState } from 'react'
import type { StagingPolicyStudio } from '@/api/creditIntelligence'
import {
  buildRevealScript,
  discoveryCardsFromSession,
  firstNameFromUser,
  policySummaryFromSession,
  timelineFromSession,
  waitingStatusLabels,
  type AnalystMessage,
  type DiscoveryCard,
} from '@/pages/creditIntelligence/policyAnalystModel'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export function CiPolicyAnalystExperience({
  userName,
  fileLabel,
  waiting,
  waitStartedAt,
  session,
  error,
  onReviewPolicy,
  onCancel,
}: {
  userName?: string | null
  fileLabel?: string | null
  waiting: boolean
  waitStartedAt: number
  session: StagingPolicyStudio | null
  error?: string | null
  onReviewPolicy: () => void
  onCancel?: () => void
}) {
  const firstName = firstNameFromUser(userName)
  const [elapsed, setElapsed] = useState(0)
  const [visibleMessages, setVisibleMessages] = useState<AnalystMessage[]>([])
  const [visibleCards, setVisibleCards] = useState<DiscoveryCard[]>([])
  const [revealStep, setRevealStep] = useState(0)
  const [phase, setPhase] = useState<'waiting' | 'revealing' | 'summary'>('waiting')

  const allCards = useMemo(
    () => (session ? discoveryCardsFromSession(session) : []),
    [session],
  )
  const script = useMemo(
    () => (session ? buildRevealScript(session, firstName) : []),
    [session, firstName],
  )
  const summary = useMemo(
    () => (session ? policySummaryFromSession(session) : null),
    [session],
  )

  // Waiting clock — honest elapsed progress while API runs
  useEffect(() => {
    if (!waiting) return
    setPhase('waiting')
    setVisibleMessages([])
    setVisibleCards([])
    setRevealStep(0)
    const t0 = waitStartedAt || Date.now()
    const id = window.setInterval(() => setElapsed(Date.now() - t0), 200)
    return () => window.clearInterval(id)
  }, [waiting, waitStartedAt])

  // When session arrives, begin reveal from real data
  useEffect(() => {
    if (waiting || !session || error) return
    setPhase('revealing')
    setVisibleMessages([])
    setVisibleCards([])
    setRevealStep(0)

    let cancelled = false
    let i = 0
    const cards = discoveryCardsFromSession(session)
    const messages = buildRevealScript(session, firstName)

    const tick = () => {
      if (cancelled) return
      if (i >= messages.length) {
        setPhase('summary')
        setVisibleCards(cards)
        return
      }
      const msg = messages[i]
      setVisibleMessages((prev) => [...prev, msg])
      setRevealStep(i + 1)

      // Reveal a card when a "find" message lands
      if (msg.kind === 'find') {
        setVisibleCards((prev) => {
          const nextIdx = prev.length
          if (nextIdx < cards.length) {
            return [...prev, cards[nextIdx]]
          }
          return prev
        })
      }
      // After greeting/progress, show first batch of cards if we have highlight progress
      if (msg.kind === 'progress' && i === 1 && cards.length > 0) {
        setVisibleCards(cards.slice(0, Math.min(5, cards.length)))
      }

      i += 1
      const delay = msg.kind === 'greeting' ? 700 : msg.kind === 'complete' ? 500 : 550
      window.setTimeout(tick, delay)
    }

    const start = window.setTimeout(tick, 400)
    return () => {
      cancelled = true
      window.clearTimeout(start)
    }
  }, [waiting, session, error, firstName])

  const waitUi = waitingStatusLabels(elapsed)
  const timeline = timelineFromSession(
    session,
    phase === 'waiting' ? 'waiting' : phase,
    revealStep,
    script.length || 1,
  )
  const progress =
    phase === 'waiting'
      ? waitUi.progress
      : phase === 'summary'
        ? 100
        : Math.min(99, Math.round(((revealStep + 1) / Math.max(1, script.length)) * 100))

  return (
    <div className="ci-analyst fixed inset-0 z-40 overflow-auto bg-[#f4f6f8]">
      <style>{`
        @keyframes ciAnalystFadeUp {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes ciAnalystPulse {
          0%, 100% { opacity: .45; }
          50% { opacity: 1; }
        }
        @keyframes ciAnalystBar {
          0% { background-position: 0% 50%; }
          100% { background-position: 100% 50%; }
        }
        .ci-analyst-msg { animation: ciAnalystFadeUp .45s ease-out both; }
        .ci-analyst-card { animation: ciAnalystFadeUp .5s ease-out both; }
        .ci-analyst-dot { animation: ciAnalystPulse 1.2s ease-in-out infinite; }
        .ci-analyst-bar {
          background: linear-gradient(90deg, #0f4c81, #2a7ab8, #0f4c81);
          background-size: 200% 100%;
          animation: ciAnalystBar 2.2s linear infinite;
        }
      `}</style>

      <header className="sticky top-0 z-10 border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-6 py-4">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">
              AI Policy Analyst
            </div>
            <h1 className="font-serif text-xl text-slate-900 sm:text-2xl">
              {phase === 'summary' ? 'Analysis complete' : 'Analysing credit policy'}
            </h1>
            {fileLabel ? (
              <p className="mt-0.5 text-xs text-slate-500">{fileLabel}</p>
            ) : null}
          </div>
          {onCancel && waiting ? (
            <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onCancel}>
              Cancel
            </button>
          ) : null}
        </div>
        <div className="h-1.5 w-full bg-slate-100">
          <div
            className={`h-full transition-[width] duration-500 ease-out ${waiting ? 'ci-analyst-bar' : 'bg-[#0f4c81]'}`}
            style={{ width: `${progress}%` }}
          />
        </div>
      </header>

      <div className="mx-auto grid max-w-6xl gap-6 px-6 py-8 lg:grid-cols-[minmax(0,1.35fr)_minmax(280px,0.85fr)]">
        <main className="min-h-[60vh]">
          {error ? (
            <div className="rounded-xl border border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-900">
              <p>{error}</p>
              {onCancel ? (
                <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm mt-3" onClick={onCancel}>
                  Back to upload
                </button>
              ) : null}
            </div>
          ) : null}

          {waiting ? (
            <div className="rounded-2xl border border-slate-200 bg-white px-6 py-8 shadow-sm">
              <p className="text-lg font-medium text-slate-900">{waitUi.headline}</p>
              <p className="mt-2 text-sm text-slate-600">{waitUi.detail}</p>
              <div className="mt-6 h-2 overflow-hidden rounded-full bg-slate-100">
                <div className="ci-analyst-bar h-full rounded-full" style={{ width: `${waitUi.progress}%` }} />
              </div>
              <ul className="mt-8 space-y-3 text-sm text-slate-600">
                {[
                  'Understanding document structure…',
                  'Finding business rules…',
                  'Identifying definitions…',
                  'Looking for exceptions…',
                  'Finding ambiguous business terms…',
                  'Preparing draft policy…',
                ].map((line, idx) => {
                  const active = waitUi.progress >= 20 + idx * 12
                  return (
                    <li key={line} className={active ? 'text-slate-900' : 'text-slate-400'}>
                      <span className={`mr-2 inline-block h-1.5 w-1.5 rounded-full bg-[#0f4c81] ${active ? 'ci-analyst-dot' : 'opacity-30'}`} />
                      {line}
                    </li>
                  )
                })}
              </ul>
              <p className="mt-8 text-xs text-slate-500">
                Progress reflects live analysis. Counts appear only after extraction completes — nothing is invented.
              </p>
            </div>
          ) : null}

          {!waiting && phase !== 'summary' ? (
            <div className="space-y-4">
              <div className="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
                <div className="mb-4 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-[#0f4c81] text-[10px] text-white">
                    AI
                  </span>
                  Policy Analyst
                </div>
                <div className="space-y-4">
                  {visibleMessages.map((m) => (
                    <div
                      key={m.id}
                      className={`ci-analyst-msg rounded-xl px-4 py-3 text-[15px] leading-relaxed text-slate-800 ${
                        m.kind === 'greeting' || m.kind === 'complete'
                          ? 'bg-slate-50'
                          : m.kind === 'find'
                            ? 'border border-sky-100 bg-sky-50/70'
                            : 'bg-white'
                      }`}
                    >
                      <p className="whitespace-pre-wrap">{m.text}</p>
                    </div>
                  ))}
                  {phase === 'revealing' ? (
                    <div className="flex items-center gap-2 px-2 text-xs text-slate-400">
                      <span className="ci-analyst-dot inline-block h-1.5 w-1.5 rounded-full bg-slate-400" />
                      Continuing analysis…
                    </div>
                  ) : null}
                </div>
              </div>

              <div>
                <h2 className="mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
                  Live discovery
                </h2>
                <div className="grid gap-3 sm:grid-cols-2">
                  {visibleCards.map((c, idx) => (
                    <div
                      key={c.key}
                      className="ci-analyst-card rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm"
                      style={{ animationDelay: `${idx * 40}ms` }}
                    >
                      <div className="text-2xl font-semibold tabular-nums text-[#0f4c81]">{c.count}</div>
                      <div className="mt-1 text-sm font-medium text-slate-800">{c.label}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : null}

          {!waiting && phase === 'summary' && summary ? (
            <div className="space-y-5">
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50/80 px-6 py-5">
                <div className="text-sm font-semibold text-emerald-900">Analysis Complete</div>
                <p className="mt-1 text-sm text-emerald-900/90">
                  ✓ Draft policy successfully prepared. Nothing is active in production.
                </p>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white px-6 py-6 shadow-sm">
                <h2 className="font-serif text-2xl text-slate-900">Policy Summary</h2>
                <p className="mt-1 text-sm text-slate-600">{summary.policyName}</p>
                <p className="mt-5 text-sm font-medium text-slate-800">We found</p>
                <ul className="mt-3 space-y-2">
                  {summary.lines.map((line) => (
                    <li key={line.label} className="flex justify-between border-b border-slate-100 py-2 text-sm">
                      <span className="text-slate-700">{line.label}</span>
                      <span className="font-semibold tabular-nums text-slate-900">{line.count}</span>
                    </li>
                  ))}
                </ul>
                <div className="mt-6 rounded-lg bg-slate-50 px-4 py-3 text-sm">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Estimated Review Time
                  </div>
                  <div className="mt-1 text-lg font-semibold text-slate-900">
                    {summary.estimatedMinutes} minutes
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Based on open ambiguities and rules still needing review — not a commitment.
                  </p>
                </div>
                <button type="button" className="bt-btn bt-btn-primary mt-6" onClick={onReviewPolicy}>
                  Review Policy
                </button>
              </div>

              {allCards.length > 0 ? (
                <div>
                  <h3 className="mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">
                    Discovery inventory
                  </h3>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {allCards.map((c) => (
                      <div key={c.key} className="rounded-xl border border-slate-200 bg-white px-4 py-3">
                        <div className="text-xl font-semibold tabular-nums text-[#0f4c81]">{c.count}</div>
                        <div className="text-sm text-slate-700">{c.label}</div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}
        </main>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="rounded-2xl border border-slate-200 bg-white px-5 py-5 shadow-sm">
            <h2 className="text-xs font-semibold uppercase tracking-[0.12em] text-slate-500">Timeline</h2>
            <ol className="mt-4 space-y-3">
              {timeline.map((step) => (
                <li key={step.key} className="flex gap-3 text-sm">
                  <span
                    className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${
                      step.state === 'DONE'
                        ? 'bg-emerald-100 text-emerald-800'
                        : step.state === 'CURRENT'
                          ? 'bg-[#0f4c81] text-white'
                          : 'bg-slate-100 text-slate-400'
                    }`}
                  >
                    {step.state === 'DONE' ? '✓' : step.state === 'CURRENT' ? '•' : ''}
                  </span>
                  <span
                    className={
                      step.state === 'PENDING' ? 'text-slate-400' : 'font-medium text-slate-800'
                    }
                  >
                    {step.label}
                  </span>
                </li>
              ))}
            </ol>
            {!waiting && session ? (
              <p className="mt-5 border-t border-slate-100 pt-4 text-xs text-slate-500">
                Stage: {String(asRecord(session.pipeline).currentStage ?? 'Review')} · readiness{' '}
                {String(asRecord(session.readinessBanner).policyReadinessPercent ?? '—')}%
              </p>
            ) : null}
          </div>
          <p className="mt-3 px-1 text-[11px] leading-relaxed text-slate-500">
            Draft for review only — not live in production lending.
          </p>
        </aside>
      </div>
    </div>
  )
}
