import { useEffect, useMemo, useState } from 'react'
import { formatInstant } from '@/lib/format'
import type { ApplicationTimelineEntry, ApplicationTimelineResponse } from '@/types/applicationTimeline'

type FlowStage = {
  status: string
  enteredAt: string | null
  isCurrent: boolean
  actions: ApplicationTimelineEntry[]
  processes: ApplicationTimelineEntry[]
  integrations: ApplicationTimelineEntry[]
  plpOperations: ApplicationTimelineEntry[]
}

const PLP_ACTION_LABELS: Record<string, string> = {
  PROGRAM_CREATED: 'Program created in LOS',
  PROGRAM_RESUBMITTED: 'Program resubmitted',
  PROGRAM_SYNCED: 'Program pushed to PLP',
  PROGRAM_SYNC_FAILED: 'Program push to PLP failed',
  SUB_PROGRAM_SYNCED: 'Sub-program pushed to PLP',
  SUB_PROGRAM_SYNC_FAILED: 'Sub-program push to PLP failed',
  PROGRAM_ACTIVATED: 'Program activated on PLP',
  PROGRAM_ACTIVATE_FAILED: 'Program activation on PLP failed',
  PROGRAM_STATUS_REFRESHED: 'Program status refreshed from PLP',
  PROGRAM_APPROVED_FROM_PLP: 'Program approved on PLP',
  PROGRAM_SENT_BACK_FROM_PLP: 'Program sent back from PLP',
  PROGRAM_PENDING_L2_FROM_PLP: 'Program pending L2 on PLP',
  ANCHOR_SYNCED: 'Anchor pushed to PLP',
  ANCHOR_SYNC_FAILED: 'Anchor push to PLP failed',
  BORROWER_SYNCED: 'Borrower pushed to PLP',
  BORROWER_SYNC_FAILED: 'Borrower push to PLP failed',
  BORROWER_LINK_SYNCED: 'Borrower linked on PLP sub-program',
  BORROWER_LINK_SYNC_FAILED: 'Borrower link on PLP failed',
  BORROWER_MAPPING_SYNCED: 'Borrower program mapping synced to PLP',
  BORROWER_MAPPING_SYNC_FAILED: 'Borrower mapping sync failed',
}

function isPlpEntry(entry: ApplicationTimelineEntry): boolean {
  const title = (entry.title ?? '').toUpperCase()
  if (title.startsWith('PLP')) return true
  if (entry.category === 'INTEGRATION' && title.includes('PLP')) return true
  const action = String(entry.result ?? '').toUpperCase()
  return Object.keys(PLP_ACTION_LABELS).some((k) => action.includes(k) || title.includes(k))
}

function plpFriendlyLabel(entry: ApplicationTimelineEntry): string {
  const action = String(entry.result ?? '').toUpperCase()
  for (const [key, label] of Object.entries(PLP_ACTION_LABELS)) {
    if (action === key || (entry.title ?? '').toUpperCase().includes(key)) {
      return label
    }
  }
  if (entry.category === 'INTEGRATION') {
    return entry.title?.replace(/^PLP\s*·\s*/i, 'PLP API: ') ?? 'PLP integration'
  }
  return entry.title ?? 'PLP operation'
}

function attachPlpOperations(stage: Omit<FlowStage, 'plpOperations'>): FlowStage {
  const all = [...stage.actions, ...stage.processes, ...stage.integrations]
  const plpOperations = all.filter(isPlpEntry).sort((a, b) => {
    const ta = a.occurredAt ? new Date(a.occurredAt).getTime() : 0
    const tb = b.occurredAt ? new Date(b.occurredAt).getTime() : 0
    return ta - tb
  })
  const plpIds = new Set(plpOperations.map((e) => e.id))
  return {
    ...stage,
    plpOperations,
    actions: stage.actions.filter((e) => !plpIds.has(e.id)),
    processes: stage.processes.filter((e) => !plpIds.has(e.id)),
    integrations: stage.integrations.filter((e) => !plpIds.has(e.id)),
  }
}

function resultBadgeClass(result: string | null | undefined): string {
  const r = (result ?? '').toUpperCase()
  if (r.includes('FAIL') || r.includes('REJECT') || r === 'FAILED' || r === 'ERROR') {
    return 'text-rose-700'
  }
  if (r.includes('SUCCESS') || r.includes('COMPLETED') || r.includes('DISBURSED') || r === 'PASS') {
    return 'text-emerald-700'
  }
  return 'text-slate-300'
}

function buildFlowStages(timeline: ApplicationTimelineResponse): FlowStage[] {
  const entriesAsc = [...(timeline.entries ?? [])].sort((a, b) => {
    const ta = a.occurredAt ? new Date(a.occurredAt).getTime() : 0
    const tb = b.occurredAt ? new Date(b.occurredAt).getTime() : 0
    return ta - tb
  })

  const statusEvents = entriesAsc.filter((e) => e.category === 'STATUS')
  const stages: FlowStage[] = []

  if (statusEvents.length === 0) {
    const fallback = timeline.currentStatus ?? 'DRAFT'
    stages.push(attachPlpOperations({
      status: fallback,
      enteredAt: null,
      isCurrent: true,
      actions: entriesAsc.filter((e) => e.category === 'ACTION'),
      processes: entriesAsc.filter((e) => e.category === 'PROCESS'),
      integrations: entriesAsc.filter((e) => e.category === 'INTEGRATION'),
    }))
    return stages
  }

  for (let i = 0; i < statusEvents.length; i++) {
    const st = statusEvents[i]
    const next = statusEvents[i + 1]
    const start = st.occurredAt ? new Date(st.occurredAt).getTime() : 0
    const end = next?.occurredAt ? new Date(next.occurredAt).getTime() : Number.POSITIVE_INFINITY
    const status = String(st.result ?? st.title ?? 'UNKNOWN')
    const bucket = entriesAsc.filter((e) => {
      if (e.category === 'STATUS') return false
      const t = e.occurredAt ? new Date(e.occurredAt).getTime() : 0
      return t >= start && t < end
    })
    stages.push(attachPlpOperations({
      status,
      enteredAt: st.occurredAt,
      isCurrent: status === timeline.currentStatus,
      actions: bucket.filter((e) => e.category === 'ACTION'),
      processes: bucket.filter((e) => e.category === 'PROCESS'),
      integrations: bucket.filter((e) => e.category === 'INTEGRATION'),
    }))
  }
  return stages
}

export function ApplicationFlowMap({
  timeline,
  fullscreen = false,
  onToggleFullscreen,
}: {
  timeline: ApplicationTimelineResponse
  fullscreen?: boolean
  onToggleFullscreen?: () => void
}) {
  const stages = useMemo(() => buildFlowStages(timeline), [timeline])
  const [openStage, setOpenStage] = useState<string | null>(null)

  useEffect(() => {
    if (openStage != null || stages.length === 0) return
    const idx = stages.findIndex((s) => s.isCurrent)
    if (idx >= 0) setOpenStage(`${stages[idx].status}-${idx}`)
    else setOpenStage(`${stages[stages.length - 1].status}-${stages.length - 1}`)
  }, [stages, openStage])

  useEffect(() => {
    if (!fullscreen) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onToggleFullscreen?.()
    }
    window.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [fullscreen, onToggleFullscreen])

  const shell = (
    <div
      className={`relative overflow-hidden rounded-2xl border border-[var(--bt-gray-200)] bg-gradient-to-br from-slate-950 via-slate-900 to-slate-800 text-slate-100 shadow-lg ${
        fullscreen ? 'flex h-full min-h-0 flex-col p-6' : 'p-4'
      }`}
    >
      <div
        className="pointer-events-none absolute inset-0 opacity-30"
        style={{
          backgroundImage:
            'radial-gradient(circle at 20% 20%, rgba(251,146,60,0.25), transparent 40%), radial-gradient(circle at 80% 0%, rgba(56,189,248,0.2), transparent 35%), linear-gradient(rgba(148,163,184,0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.08) 1px, transparent 1px)',
          backgroundSize: 'auto, auto, 24px 24px, 24px 24px',
        }}
      />
      <div className="relative mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-orange-300/90">Lifecycle graph</p>
          <h3 className={`mt-1 font-semibold text-white ${fullscreen ? 'text-xl' : 'text-base'}`}>
            {timeline.applicationNumber}
            <span className="ml-2 text-sm font-normal text-slate-400">
              · {timeline.currentStatus?.replaceAll('_', ' ') ?? '—'}
            </span>
          </h3>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex flex-wrap gap-3 text-[10px] uppercase tracking-wide text-slate-400">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-amber-400" /> Actions
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-violet-400" /> Processes
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-cyan-400" /> Integrations
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-sky-400" /> PLP sync
            </span>
          </div>
          {onToggleFullscreen ? (
            <button
              type="button"
              onClick={onToggleFullscreen}
              className="rounded-md border border-slate-500/60 bg-slate-900/80 px-2.5 py-1 text-[11px] font-medium text-slate-200 hover:border-orange-400/50 hover:text-orange-100"
            >
              {fullscreen ? 'Exit full screen' : 'Full screen'}
            </button>
          ) : null}
        </div>
      </div>

      <div className={`relative flex gap-4 overflow-x-auto pb-2 ${fullscreen ? 'min-h-0 flex-1 items-stretch' : ''}`}>
        {stages.map((stage, idx) => {
          const key = `${stage.status}-${idx}`
          const isOpen = openStage === key
          const eventCount = stage.actions.length + stage.processes.length + stage.integrations.length + stage.plpOperations.length
          return (
            <div
              key={key}
              className={`flex shrink-0 items-stretch gap-3 ${
                fullscreen ? 'min-w-[340px] max-w-[400px]' : 'min-w-[280px] max-w-[320px]'
              }`}
            >
              <button
                type="button"
                onClick={() => setOpenStage(isOpen ? null : key)}
                className={`group relative w-full rounded-2xl border p-4 text-left transition ${
                  stage.isCurrent
                    ? 'border-orange-400/70 bg-orange-500/15 shadow-[0_0_0_1px_rgba(251,146,60,0.35),0_12px_40px_rgba(251,146,60,0.15)]'
                    : 'border-slate-600/60 bg-slate-900/70 hover:border-slate-400/50'
                } ${fullscreen ? 'min-h-[420px]' : ''}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">
                      Stage {idx + 1}
                    </p>
                    <p className={`mt-1 font-semibold leading-snug text-white ${fullscreen ? 'text-base' : 'text-sm'}`}>
                      {stage.status.replaceAll('_', ' ')}
                    </p>
                    {stage.enteredAt ? (
                      <p className="mt-1 text-[11px] tabular-nums text-slate-400">{formatInstant(stage.enteredAt)}</p>
                    ) : null}
                  </div>
                  {stage.isCurrent ? (
                    <span className="rounded-full bg-orange-400/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-orange-200">
                      Now
                    </span>
                  ) : null}
                </div>

                <div className="mt-3 flex flex-wrap gap-1.5">
                  <span className="rounded-md bg-amber-400/15 px-2 py-0.5 text-[10px] font-medium text-amber-200">
                    {stage.actions.length} actions
                  </span>
                  <span className="rounded-md bg-violet-400/15 px-2 py-0.5 text-[10px] font-medium text-violet-200">
                    {stage.processes.length} processes
                  </span>
                  <span className="rounded-md bg-cyan-400/15 px-2 py-0.5 text-[10px] font-medium text-cyan-200">
                    {stage.integrations.length} APIs
                  </span>
                  {stage.plpOperations.length > 0 ? (
                    <span className="rounded-md bg-sky-400/15 px-2 py-0.5 text-[10px] font-medium text-sky-200">
                      {stage.plpOperations.length} PLP
                    </span>
                  ) : null}
                </div>

                {isOpen && eventCount > 0 ? (
                  <div
                    className={`mt-3 space-y-2 overflow-y-auto border-t border-white/10 pt-3 ${
                      fullscreen ? 'max-h-[55vh]' : 'max-h-56'
                    }`}
                  >
                    {stage.plpOperations.length > 0 ? (
                      <div className="space-y-1.5">
                        <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300/90">
                          PLP platform sync
                        </p>
                        {stage.plpOperations.map((e) => (
                          <FlowEventRow key={e.id} entry={e} tone="plp" label={plpFriendlyLabel(e)} />
                        ))}
                      </div>
                    ) : null}
                    {stage.processes.map((e) => (
                      <FlowEventRow key={e.id} entry={e} tone="process" />
                    ))}
                    {stage.integrations.map((e) => (
                      <FlowEventRow key={e.id} entry={e} tone="integration" />
                    ))}
                    {stage.actions.slice(0, fullscreen ? 40 : 12).map((e) => (
                      <FlowEventRow key={e.id} entry={e} tone="action" />
                    ))}
                    {!fullscreen && stage.actions.length > 12 ? (
                      <p className="text-[10px] text-slate-500">+{stage.actions.length - 12} more actions</p>
                    ) : null}
                  </div>
                ) : stage.plpOperations.length > 0 ? (
                  <div className="mt-3 space-y-1 border-t border-white/10 pt-3">
                    <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-sky-300/90">PLP sync</p>
                    {stage.plpOperations.slice(0, 3).map((e) => (
                      <p key={e.id} className="truncate text-[11px] text-sky-100/90">
                        {plpFriendlyLabel(e)}
                      </p>
                    ))}
                    {stage.plpOperations.length > 3 ? (
                      <p className="text-[10px] text-slate-500">+{stage.plpOperations.length - 3} more PLP steps</p>
                    ) : null}
                  </div>
                ) : (
                  <p className="mt-3 text-[11px] text-slate-500">
                    {eventCount === 0 ? 'No nested events' : 'Click to expand nested steps'}
                  </p>
                )}
              </button>
              {idx < stages.length - 1 ? (
                <div className="flex shrink-0 items-center" aria-hidden>
                  <div
                    className={`h-px bg-gradient-to-r from-slate-500 to-orange-400/80 ${fullscreen ? 'w-10' : 'w-6'}`}
                  />
                  <div className="h-2 w-2 rotate-45 border-r border-t border-orange-300/80" />
                </div>
              ) : null}
            </div>
          )
        })}
      </div>
    </div>
  )

  if (!fullscreen) {
    return shell
  }

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
      <div className="flex h-[min(92vh,920px)] w-[min(96vw,1400px)] flex-col overflow-hidden rounded-2xl shadow-2xl">
        {shell}
      </div>
    </div>
  )
}

function FlowEventRow({
  entry,
  tone,
  label,
}: {
  entry: ApplicationTimelineEntry
  tone: 'action' | 'process' | 'integration' | 'plp'
  label?: string
}) {
  const toneClass =
    tone === 'plp'
      ? 'border-sky-400/40 bg-sky-500/15 text-sky-100'
      : tone === 'process'
      ? 'border-violet-400/30 bg-violet-500/10 text-violet-100'
      : tone === 'integration'
        ? 'border-cyan-400/30 bg-cyan-500/10 text-cyan-100'
        : 'border-amber-400/30 bg-amber-500/10 text-amber-100'
  return (
    <div className={`rounded-lg border px-2.5 py-1.5 ${toneClass}`}>
      <p className="truncate text-[11px] font-medium">{label ?? entry.title}</p>
      <div className="mt-0.5 flex flex-wrap gap-x-2 text-[10px] opacity-80">
        {entry.occurredAt ? <span className="tabular-nums">{formatInstant(entry.occurredAt)}</span> : null}
        {entry.result ? <span className={resultBadgeClass(entry.result)}>{entry.result}</span> : null}
      </div>
    </div>
  )
}
