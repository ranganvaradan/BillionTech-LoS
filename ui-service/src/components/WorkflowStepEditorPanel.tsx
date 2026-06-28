import type { WorkflowEventTemplateMappingDto } from '@/api/workflowEventTemplateMappings'
import { providersForWorkflowStep } from '@/lib/integrationProviderMatrix'
import { defaultTemplateCodeForEventChannel } from '@/lib/workflowEventTemplateMappingsFallback'
import {
  PROCESS_DEFINITIONS,
  type ProcessNotificationConfig,
} from '@/lib/workflowProcessNotifications'
import { WORKFLOW_NOTIFICATION_CHANNELS, WORKFLOW_NOTIFICATION_EVENT_OPTIONS } from '@/lib/workflowNotificationConstants'
import {
  WORKFLOW_STEP_TYPES,
  createEmptyVisualStep,
  defaultProviderForWorkflowStep,
  getStepMatrixHelp,
  isPostKycWorkflowStep,
  type StepNotificationConfig,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'
import { useLayoutEffect, useMemo } from 'react'

type Props = {
  steps: VisualWorkflowStep[]
  onChange: (next: VisualWorkflowStep[]) => void
  templateMappings: WorkflowEventTemplateMappingDto[]
  processNotifications: ProcessNotificationConfig[]
  onProcessNotificationsChange: (next: ProcessNotificationConfig[]) => void
}

function move<T>(arr: T[], from: number, to: number): T[] {
  if (to < 0 || to >= arr.length) return arr
  const c = arr.slice()
  const [x] = c.splice(from, 1)
  c.splice(to, 0, x)
  return c
}

function templateSelectOptions(
  mappings: WorkflowEventTemplateMappingDto[],
  eventType: string,
  channel: string,
  currentCode: string,
): { code: string; label: string }[] {
  const ev = eventType.trim().toUpperCase()
  const ch = channel.trim().toUpperCase()
  const rows = mappings
    .filter((m) => m.workflowEvent === ev && m.channel === ch && m.active !== false)
    .slice()
    .sort((a, b) =>
      a.sortOrder !== b.sortOrder ? a.sortOrder - b.sortOrder : a.templateCode.localeCompare(b.templateCode),
    )
  const out: { code: string; label: string }[] = []
  const seen = new Set<string>()
  for (const r of rows) {
    if (seen.has(r.templateCode)) continue
    seen.add(r.templateCode)
    const label = r.defaultMapping ? `${r.templateCode} — default` : r.templateCode
    out.push({ code: r.templateCode, label })
  }
  const trimmed = currentCode.trim()
  if (trimmed && !seen.has(trimmed)) {
    out.unshift({ code: trimmed, label: `${trimmed} — saved in workflow` })
  }
  if (out.length === 0) {
    const fb = defaultTemplateCodeForEventChannel(ev, ch, mappings)
    if (fb) {
      out.push({ code: fb, label: `${fb} — default` })
    }
  }
  return out
}

function StepCard(props: {
  s: VisualWorkflowStep
  i: number
  steps: VisualWorkflowStep[]
  onChange: (next: VisualWorkflowStep[]) => void
  templateMappings: WorkflowEventTemplateMappingDto[]
}) {
  const { s, i, steps, onChange, templateMappings } = props
  const postKyc = isPostKycWorkflowStep(s.step)
  const help = getStepMatrixHelp(s.step)
  const providerOptions = providersForWorkflowStep(s.step)
  function updateNotifications(nextNotifications: StepNotificationConfig[]) {
    onChange(steps.map((x) => (x.id === s.id ? { ...x, notifications: nextNotifications } : x)))
  }

  return (
    <li
      className={
        postKyc
          ? 'rounded-lg border border-indigo-200/80 bg-indigo-50/50 p-3 shadow-sm'
          : 'rounded-lg border border-slate-200 bg-slate-50/80 p-3 shadow-sm'
      }
    >
      {postKyc ? (
        <p className="mb-2 text-xs font-medium text-indigo-800">After KYC (credit / bureau)</p>
      ) : (
        <p className="mb-2 text-xs font-medium text-slate-600">KYC verification</p>
      )}
      <div className="mb-2 flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <span className="min-w-0 shrink text-xs font-medium text-slate-500">
          Order: <span className="font-mono text-slate-800">{i + 1}</span>
        </span>
        <div className="flex shrink-0 flex-wrap gap-1 sm:justify-end">
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
            onClick={() => onChange(move(steps, i, i - 1))}
            disabled={i === 0}
          >
            Up
          </button>
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
            onClick={() => onChange(move(steps, i, i + 1))}
            disabled={i === steps.length - 1}
          >
            Down
          </button>
          <button
            type="button"
            className="rounded border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs text-rose-900"
            onClick={() => onChange(steps.filter((x) => x.id !== s.id))}
          >
            Remove
          </button>
        </div>
      </div>
      <div className="grid min-w-0 gap-2 sm:grid-cols-2">
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-2">
          <span className="mb-0.5 block text-slate-500">Step name (label, optional)</span>
          <input
            className="w-full min-w-0 rounded border border-slate-300 px-2 py-1 text-sm"
            value={s.name}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, name: v } : x)),
              )
            }}
            placeholder="e.g. Customer PAN"
          />
        </label>
        <label className="block min-w-0 text-xs text-slate-600">
          <span className="mb-0.5 block text-slate-500">Step type *</span>
          <select
            className="w-full min-w-0 rounded border border-slate-300 bg-white px-2 py-1 text-sm"
            value={s.step}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) =>
                  x.id === s.id
                    ? { ...x, step: v, provider: defaultProviderForWorkflowStep(v) }
                    : x,
                ),
              )
            }}
          >
            {WORKFLOW_STEP_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
                {isPostKycWorkflowStep(t) ? ' (post-KYC / eSign)' : ''}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-500">
            <span className="font-medium text-slate-600">Purpose:</span> {help.purpose}
            <br />
            <span className="font-medium text-slate-600">Applies to:</span> {help.appliesTo}
          </p>
        </label>
        <label className="block min-w-0 text-xs text-slate-600">
          <span className="mb-0.5 block text-slate-500">Provider (ordered primary → fallback)</span>
          <select
            className="w-full min-w-0 rounded border border-slate-300 bg-white px-2 py-1 text-sm"
            value={s.provider || defaultProviderForWorkflowStep(s.step)}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, provider: v } : x)),
              )
            }}
          >
            {providerOptions.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        <label className="flex min-w-0 items-center gap-2 text-xs text-slate-600 sm:col-span-2">
          <input
            type="checkbox"
            className="rounded border-slate-300"
            checked={s.mandatory}
            onChange={(e) => {
              const v = e.target.checked
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, mandatory: v } : x)),
              )
            }}
          />
          Mandatory
        </label>
        {s.step === 'VIDEO_KYC' || s.step === 'VKYC' ? (
          <label className="flex min-w-0 items-center gap-2 text-xs text-slate-600 sm:col-span-2">
            <input
              type="checkbox"
              className="rounded border-slate-300"
              checked={s.allowPhysicalKycFallback}
              onChange={(e) => {
                const v = e.target.checked
                onChange(steps.map((x) => (x.id === s.id ? { ...x, allowPhysicalKycFallback: v } : x)))
              }}
            />
            Allow Physical KYC completion
          </label>
        ) : null}
        <details className="min-w-0 overflow-hidden rounded border border-slate-200 bg-white p-2 sm:col-span-2">
          <summary className="cursor-pointer text-xs font-medium text-slate-600">
            Legacy step-level notifications ({s.notifications.length})
          </summary>
          <div className="mt-2 space-y-2 border-t border-slate-100 pt-2">
            <p className="text-xs text-slate-500">
              Process-level notifications are preferred. Keep these only for backward compatibility.
            </p>
            <div className="mb-2 flex min-w-0 flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <span className="text-xs font-medium text-slate-600">Step notifications</span>
              <button
                type="button"
                className="shrink-0 self-start rounded border border-slate-300 bg-slate-50 px-2 py-0.5 text-xs sm:self-auto"
                onClick={() => {
                  const eventType = WORKFLOW_NOTIFICATION_EVENT_OPTIONS[0]
                  const channel = 'EMAIL'
                  const tc = defaultTemplateCodeForEventChannel(eventType, channel, templateMappings)
                  updateNotifications([
                    ...s.notifications,
                    {
                      id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`,
                      enabled: true,
                      eventType,
                      channel,
                      templateCode: tc,
                      recipientType: 'BORROWER_EMAIL',
                      delaySeconds: 0,
                    },
                  ])
                }}
              >
                Add notification
              </button>
            </div>
            {s.notifications.length === 0 ? (
              <p className="text-xs text-slate-500">No notification rules for this step.</p>
            ) : (
              <div className="space-y-2">
                {s.notifications.map((n) => (
                  <NotificationRuleRow
                    key={n.id}
                    n={n}
                    notifyAll={s.notifications}
                    updateNotifications={updateNotifications}
                    templateMappings={templateMappings}
                  />
                ))}
              </div>
            )}
          </div>
        </details>
      </div>
    </li>
  )
}

function NotificationRuleRow(props: {
  n: StepNotificationConfig
  notifyAll: StepNotificationConfig[]
  updateNotifications: (next: StepNotificationConfig[]) => void
  templateMappings: WorkflowEventTemplateMappingDto[]
}) {
  const { n, notifyAll, updateNotifications, templateMappings } = props

  const opts = useMemo(
    () => templateSelectOptions(templateMappings, n.eventType, n.channel, n.templateCode),
    [templateMappings, n.channel, n.eventType, n.templateCode],
  )

  const displayTemplateCode =
    n.templateCode.trim() ||
    defaultTemplateCodeForEventChannel(n.eventType, n.channel, templateMappings) ||
    opts[0]?.code ||
    ''

  useLayoutEffect(() => {
    if (n.templateCode.trim()) return
    const d =
      defaultTemplateCodeForEventChannel(n.eventType, n.channel, templateMappings) || opts[0]?.code
    if (!d) return
    updateNotifications(
      notifyAll.map((x) => (x.id === n.id ? { ...x, templateCode: d } : x)),
    )
  }, [n.channel, n.eventType, n.id, n.templateCode, notifyAll, opts, templateMappings, updateNotifications])

  return (
    <div className="min-w-0 space-y-2 rounded border border-slate-200 bg-slate-50 p-2">
      <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-12 sm:items-end">
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-3">
          Event
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={n.eventType}
            onChange={(e) => {
              const eventType = e.target.value
              const nextCode = defaultTemplateCodeForEventChannel(eventType, n.channel, templateMappings)
              updateNotifications(
                notifyAll.map((x) => (x.id === n.id ? { ...x, eventType, templateCode: nextCode } : x)),
              )
            }}
          >
            {WORKFLOW_NOTIFICATION_EVENT_OPTIONS.map((eventCode) => (
              <option key={eventCode} value={eventCode}>
                {eventCode}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-2">
          Channel
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={n.channel}
            onChange={(e) => {
              const channel = e.target.value
              const nextCode = defaultTemplateCodeForEventChannel(n.eventType, channel, templateMappings)
              updateNotifications(
                notifyAll.map((x) => (x.id === n.id ? { ...x, channel, templateCode: nextCode } : x)),
              )
            }}
          >
            {WORKFLOW_NOTIFICATION_CHANNELS.map((ch) => (
              <option key={ch} value={ch}>
                {ch}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-4">
          Template
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={displayTemplateCode}
            onChange={(e) =>
              updateNotifications(
                notifyAll.map((x) => (x.id === n.id ? { ...x, templateCode: e.target.value } : x)),
              )
            }
          >
            {opts.length === 0 && n.templateCode.trim() ? (
              <option value={n.templateCode.trim()}>{n.templateCode.trim()} (no catalog match)</option>
            ) : null}
            {opts.map((o) => (
              <option key={o.code} value={o.code}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-3">
          Delay (sec)
          <input
            type="number"
            min={0}
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 px-1 py-1 text-xs"
            value={n.delaySeconds}
            onChange={(e) =>
              updateNotifications(
                notifyAll.map((x) =>
                  x.id === n.id ? { ...x, delaySeconds: Number(e.target.value || 0) } : x,
                ),
              )
            }
          />
        </label>
      </div>
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-t border-slate-200/80 pt-2">
        <label className="flex min-w-0 items-center gap-1 text-xs text-slate-600">
          <input
            type="checkbox"
            checked={n.enabled}
            onChange={(e) =>
              updateNotifications(
                notifyAll.map((x) => (x.id === n.id ? { ...x, enabled: e.target.checked } : x)),
              )
            }
          />
          Enabled
        </label>
        <button
          type="button"
          className="shrink-0 rounded border border-rose-300 bg-rose-50 px-2 py-1 text-xs text-rose-800"
          onClick={() => updateNotifications(notifyAll.filter((x) => x.id !== n.id))}
        >
          Remove
        </button>
      </div>
    </div>
  )
}

export function WorkflowStepEditorPanel({
  steps,
  onChange,
  templateMappings,
  processNotifications,
  onProcessNotificationsChange,
}: Props) {
  const processRowsByCode = useMemo(() => {
    const map = new Map<string, ProcessNotificationConfig[]>()
    for (const row of processNotifications) {
      const list = map.get(row.processCode) ?? []
      list.push(row)
      map.set(row.processCode, list)
    }
    return map
  }, [processNotifications])

  return (
    <div className="space-y-2">
      <div className="rounded border border-slate-200 bg-white p-2">
        <div className="mb-2 flex min-w-0 flex-wrap items-center justify-between gap-2">
          <span className="text-xs font-medium text-slate-600">Process-level notifications</span>
        </div>
        <p className="mb-2 text-xs text-slate-500">
          Configure business notifications by process/event. Runtime resolves these first, then falls back to legacy
          step-level rules.
        </p>
        <div className="space-y-2">
          {PROCESS_DEFINITIONS.map((p) => (
            <details key={p.code} className="rounded border border-slate-200 bg-slate-50 p-2">
              <summary className="cursor-pointer text-xs font-medium text-slate-700">
                {p.label} ({processRowsByCode.get(p.code)?.length ?? 0})
              </summary>
              <div className="mt-2 space-y-2 border-t border-slate-200 pt-2">
                <button
                  type="button"
                  className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
                  onClick={() => {
                    const eventType = p.events[0] ?? WORKFLOW_NOTIFICATION_EVENT_OPTIONS[0]
                    const channel = 'EMAIL'
                    onProcessNotificationsChange([
                      ...processNotifications,
                      {
                        id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}`,
                        processCode: p.code,
                        eventType,
                        channel,
                        templateCode: defaultTemplateCodeForEventChannel(eventType, channel, templateMappings),
                        recipientType: 'BORROWER_EMAIL',
                        delaySeconds: 0,
                        enabled: true,
                      },
                    ])
                  }}
                >
                  Add {p.label} notification
                </button>
                {(processRowsByCode.get(p.code) ?? []).length === 0 ? (
                  <p className="text-xs text-slate-500">No process-level rules configured.</p>
                ) : null}
                {(processRowsByCode.get(p.code) ?? []).map((n) => (
                  <ProcessNotificationRuleRow
                    key={n.id}
                    row={n}
                    allowedEvents={p.events}
                    rows={processNotifications}
                    onChange={onProcessNotificationsChange}
                    templateMappings={templateMappings}
                  />
                ))}
              </div>
            </details>
          ))}
        </div>
      </div>
      <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <span className="min-w-0 text-xs font-medium text-slate-500">
          Workflow steps (order = execution order)
        </span>
        <button
          type="button"
          onClick={() => onChange([...steps, createEmptyVisualStep()])}
          className="shrink-0 self-start rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-800 sm:self-auto"
        >
          Add step
        </button>
      </div>
      {steps.length === 0 ? (
        <p className="text-sm text-slate-500">No steps. Add a step or use Advanced JSON below.</p>
      ) : null}
      <ul className="space-y-2">
        {steps.map((s, i) => (
          <StepCard
            key={s.id}
            s={s}
            i={i}
            steps={steps}
            onChange={onChange}
            templateMappings={templateMappings}
          />
        ))}
      </ul>
    </div>
  )
}

function ProcessNotificationRuleRow(props: {
  row: ProcessNotificationConfig
  allowedEvents: string[]
  rows: ProcessNotificationConfig[]
  onChange: (next: ProcessNotificationConfig[]) => void
  templateMappings: WorkflowEventTemplateMappingDto[]
}) {
  const { row, allowedEvents, rows, onChange, templateMappings } = props
  const templateOptions = useMemo(
    () => templateSelectOptions(templateMappings, row.eventType, row.channel, row.templateCode),
    [templateMappings, row.channel, row.eventType, row.templateCode],
  )
  const displayTemplateCode =
    row.templateCode.trim() ||
    defaultTemplateCodeForEventChannel(row.eventType, row.channel, templateMappings) ||
    templateOptions[0]?.code ||
    ''

  useLayoutEffect(() => {
    if (row.templateCode.trim()) return
    const d =
      defaultTemplateCodeForEventChannel(row.eventType, row.channel, templateMappings) || templateOptions[0]?.code
    if (!d) return
    onChange(rows.map((x) => (x.id === row.id ? { ...x, templateCode: d } : x)))
  }, [onChange, row.channel, row.eventType, row.id, row.templateCode, rows, templateMappings, templateOptions])

  return (
    <div className="min-w-0 space-y-2 rounded border border-slate-200 bg-white p-2">
      <div className="grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-12 sm:items-end">
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-3">
          Event
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={row.eventType}
            onChange={(e) => {
              const eventType = e.target.value
              const templateCode = defaultTemplateCodeForEventChannel(eventType, row.channel, templateMappings)
              onChange(rows.map((x) => (x.id === row.id ? { ...x, eventType, templateCode } : x)))
            }}
          >
            {allowedEvents.map((eventCode) => (
              <option key={eventCode} value={eventCode}>
                {eventCode}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-2">
          Channel
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={row.channel}
            onChange={(e) => {
              const channel = e.target.value
              const templateCode = defaultTemplateCodeForEventChannel(row.eventType, channel, templateMappings)
              onChange(rows.map((x) => (x.id === row.id ? { ...x, channel, templateCode } : x)))
            }}
          >
            {WORKFLOW_NOTIFICATION_CHANNELS.map((ch) => (
              <option key={ch} value={ch}>
                {ch}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-4">
          Template
          <select
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 bg-white px-1 py-1 text-xs"
            value={displayTemplateCode}
            onChange={(e) => onChange(rows.map((x) => (x.id === row.id ? { ...x, templateCode: e.target.value } : x)))}
          >
            {templateOptions.length === 0 ? (
              <option value="">No mapped template available</option>
            ) : null}
            {templateOptions.map((o) => (
              <option key={o.code} value={o.code}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block min-w-0 text-xs text-slate-600 sm:col-span-3">
          Delay (sec)
          <input
            type="number"
            min={0}
            className="mt-0.5 w-full min-w-0 rounded border border-slate-300 px-1 py-1 text-xs"
            value={row.delaySeconds}
            onChange={(e) =>
              onChange(
                rows.map((x) =>
                  x.id === row.id ? { ...x, delaySeconds: Number(e.target.value || 0) } : x,
                ),
              )
            }
          />
        </label>
      </div>
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-t border-slate-200/80 pt-2">
        <label className="flex min-w-0 items-center gap-1 text-xs text-slate-600">
          <input
            type="checkbox"
            checked={row.enabled}
            onChange={(e) => onChange(rows.map((x) => (x.id === row.id ? { ...x, enabled: e.target.checked } : x)))}
          />
          Enabled
        </label>
        <button
          type="button"
          className="shrink-0 rounded border border-rose-300 bg-rose-50 px-2 py-1 text-xs text-rose-800"
          onClick={() => onChange(rows.filter((x) => x.id !== row.id))}
        >
          Remove
        </button>
      </div>
    </div>
  )
}
