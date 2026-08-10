import { useCallback, useEffect, useState } from 'react'
import { http } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { LoadingState } from '@/components/LoadingState'
import { ErrorState } from '@/components/ErrorState'
import { formatInstant } from '@/lib/format'
import { AuditDiffView } from '@/components/audit/AuditDiffView'
import { EditorModal } from '@/components/scorecard/EditorModal'

type AuditTab = 'events' | 'api' | 'config'

type AuditEvent = {
  id: string
  applicationId: string
  eventType: string
  action: string
  performedBy?: string | null
  description?: string | null
  previousState?: Record<string, unknown> | null
  newState?: Record<string, unknown> | null
  createdAt: string
}

type ApiAuditRow = {
  id: string
  providerName: string
  apiName: string
  requestPayload?: string | null
  responsePayload?: string | null
  status?: string | null
  httpStatusCode?: number | null
  errorMessage?: string | null
  transactionId?: string | null
  applicationId?: string | null
  durationMs?: number | null
  createdAt: string
}

type EntityRecordAuditRow = {
  id: string
  entityType: string
  entityId: string
  action: string
  statusAtChange?: string | null
  performedBy?: string | null
  performedByRole?: string | null
  oldRow?: Record<string, unknown> | null
  newRow?: Record<string, unknown> | null
  changedFields?: string | null
  applicationId?: string | null
  createdAt: string
}

type PageResult<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function AuditTrailPage() {
  const [tab, setTab] = useState<AuditTab>('events')
  const [page, setPage] = useState(0)
  const [events, setEvents] = useState<PageResult<AuditEvent> | null>(null)
  const [apiCalls, setApiCalls] = useState<PageResult<ApiAuditRow> | null>(null)
  const [configChanges, setConfigChanges] = useState<PageResult<EntityRecordAuditRow> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [applicationId, setApplicationId] = useState('')
  const [provider, setProvider] = useState('')
  const [entityType, setEntityType] = useState('')
  const [entityId, setEntityId] = useState('')
  const [eventDiff, setEventDiff] = useState<AuditEvent | null>(null)
  const [configDiff, setConfigDiff] = useState<EntityRecordAuditRow | null>(null)
  const [apiDetail, setApiDetail] = useState<ApiAuditRow | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (tab === 'events') {
        const { data: res } = await http.get<PageResult<AuditEvent>>('/audit', {
          params: {
            page,
            size: 50,
            ...(applicationId.trim() ? { applicationId: applicationId.trim() } : {}),
          },
        })
        setEvents(res)
        setApiCalls(null)
        setConfigChanges(null)
      } else if (tab === 'config') {
        const { data: res } = await http.get<PageResult<EntityRecordAuditRow>>('/audit/records', {
          params: {
            page,
            size: 50,
            ...(entityType.trim() ? { entityType: entityType.trim() } : {}),
            ...(entityId.trim() ? { entityId: entityId.trim() } : {}),
            ...(applicationId.trim() ? { applicationId: applicationId.trim() } : {}),
          },
        })
        setConfigChanges(res)
        setEvents(null)
        setApiCalls(null)
      } else {
        const { data: res } = await http.get<PageResult<ApiAuditRow>>('/audit/api-calls', {
          params: {
            page,
            size: 50,
            ...(applicationId.trim() ? { applicationId: applicationId.trim() } : {}),
            ...(provider.trim() ? { provider: provider.trim() } : {}),
          },
        })
        setApiCalls(res)
        setEvents(null)
        setConfigChanges(null)
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load audit data')
      setEvents(null)
      setApiCalls(null)
    } finally {
      setLoading(false)
    }
  }, [page, applicationId, provider, entityType, entityId, tab])

  useEffect(() => {
    void load()
  }, [load])

  const data = tab === 'events' ? events : tab === 'config' ? configChanges : apiCalls

  return (
    <div>
      <PageHeader
        title="Audit Trail"
        description="Application events, admin configuration changes, and integration API request/response logs."
      />
      <AdministrationWorkspaceNav />
      <div className="mb-4 flex flex-wrap gap-1 rounded-lg border border-[var(--bt-gray-200)] bg-[var(--bt-gray-50)] p-1 w-fit">
        <button
          type="button"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === 'events' ? 'bg-white shadow-sm text-[var(--bt-gray-900)]' : 'text-[var(--bt-gray-600)]'
          }`}
          onClick={() => {
            setTab('events')
            setPage(0)
          }}
        >
          Application events
        </button>
        <button
          type="button"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === 'config' ? 'bg-white shadow-sm text-[var(--bt-gray-900)]' : 'text-[var(--bt-gray-600)]'
          }`}
          onClick={() => {
            setTab('config')
            setPage(0)
          }}
        >
          Config changes
        </button>
        <button
          type="button"
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            tab === 'api' ? 'bg-white shadow-sm text-[var(--bt-gray-900)]' : 'text-[var(--bt-gray-600)]'
          }`}
          onClick={() => {
            setTab('api')
            setPage(0)
          }}
        >
          Integration API calls
        </button>
      </div>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-xs font-medium text-[var(--bt-gray-500)]">Application ID (optional)</span>
          <input
            className="rounded-lg border border-[var(--bt-gray-200)] px-3 py-2 text-sm"
            value={applicationId}
            onChange={(e) => {
              setPage(0)
              setApplicationId(e.target.value)
            }}
            placeholder="UUID"
          />
        </label>
        {tab === 'api' ? (
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-xs font-medium text-[var(--bt-gray-500)]">Provider (optional)</span>
            <input
              className="rounded-lg border border-[var(--bt-gray-200)] px-3 py-2 text-sm"
              value={provider}
              onChange={(e) => {
                setPage(0)
                setProvider(e.target.value)
              }}
              placeholder="PERFIOS / EQUIFAX / PLP"
            />
          </label>
        ) : null}
        {tab === 'config' ? (
          <>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-xs font-medium text-[var(--bt-gray-500)]">Entity type (optional)</span>
              <input
                className="rounded-lg border border-[var(--bt-gray-200)] px-3 py-2 text-sm"
                value={entityType}
                onChange={(e) => {
                  setPage(0)
                  setEntityType(e.target.value)
                }}
                placeholder="WORKFLOW_CONFIG / UNDERWRITING_RULE_SET"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-xs font-medium text-[var(--bt-gray-500)]">Entity ID (optional)</span>
              <input
                className="rounded-lg border border-[var(--bt-gray-200)] px-3 py-2 text-sm"
                value={entityId}
                onChange={(e) => {
                  setPage(0)
                  setEntityId(e.target.value)
                }}
                placeholder="UUID"
              />
            </label>
          </>
        ) : null}
        <button type="button" className="bt-btn bt-btn-secondary" onClick={() => void load()}>
          Refresh
        </button>
      </div>
      {loading ? <LoadingState label="Loading audit…" /> : null}
      {error ? <ErrorState message={error} /> : null}
      {!loading && !error && tab === 'events' && events ? (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full text-sm">
            <thead>
              <tr>
                <th>Time</th>
                <th>Application</th>
                <th>Type</th>
                <th>Action</th>
                <th>Description</th>
                <th>Diff</th>
              </tr>
            </thead>
            <tbody>
              {(events.content ?? []).map((row) => (
                <tr key={row.id}>
                  <td className="whitespace-nowrap tabular-nums text-xs">{formatInstant(row.createdAt)}</td>
                  <td className="font-mono text-[11px]">{row.applicationId}</td>
                  <td>{row.eventType}</td>
                  <td>{row.action}</td>
                  <td className="max-w-xs text-xs text-[var(--bt-gray-600)]">{row.description ?? '—'}</td>
                  <td>
                    {(row.previousState || row.newState) && (
                      <button
                        type="button"
                        className="text-[11px] font-medium text-[var(--bt-orange)] hover:underline"
                        onClick={() => setEventDiff(row)}
                      >
                        View changes
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager data={events} page={page} setPage={setPage} />
        </div>
      ) : null}
      {!loading && !error && tab === 'config' && configChanges ? (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full text-sm">
            <thead>
              <tr>
                <th>Time</th>
                <th>Entity</th>
                <th>Action</th>
                <th>Changed by</th>
                <th>Fields</th>
                <th>Before / After</th>
              </tr>
            </thead>
            <tbody>
              {(configChanges.content ?? []).map((row) => (
                <tr key={row.id}>
                  <td className="whitespace-nowrap tabular-nums text-xs">{formatInstant(row.createdAt)}</td>
                  <td>
                    <div className="font-medium">{row.entityType}</div>
                    <div className="font-mono text-[11px] text-[var(--bt-gray-500)]">{row.entityId}</div>
                  </td>
                  <td>{row.action}</td>
                  <td className="text-xs">
                    <div>{row.performedBy ?? '—'}</div>
                    <div className="text-[var(--bt-gray-500)]">{row.performedByRole ?? ''}</div>
                  </td>
                  <td className="text-xs">{row.changedFields ?? '—'}</td>
                  <td>
                    {(row.oldRow || row.newRow) && (
                      <button
                        type="button"
                        className="text-[11px] font-medium text-[var(--bt-orange)] hover:underline"
                        onClick={() => setConfigDiff(row)}
                      >
                        View changes
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager data={configChanges} page={page} setPage={setPage} />
        </div>
      ) : null}
      {!loading && !error && tab === 'api' && apiCalls ? (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full text-sm">
            <thead>
              <tr>
                <th>Time</th>
                <th>Provider</th>
                <th>API</th>
                <th>Status</th>
                <th>HTTP</th>
                <th>Application</th>
                <th>Payload</th>
              </tr>
            </thead>
            <tbody>
              {(apiCalls.content ?? []).map((row) => (
                <tr key={row.id}>
                  <td className="whitespace-nowrap tabular-nums text-xs">{formatInstant(row.createdAt)}</td>
                  <td className="font-medium">{row.providerName}</td>
                  <td className="text-xs">{row.apiName}</td>
                  <td>{row.status ?? '—'}</td>
                  <td>{row.httpStatusCode ?? '—'}</td>
                  <td className="font-mono text-[11px]">{row.applicationId ?? '—'}</td>
                  <td>
                    <button
                      type="button"
                      className="text-[11px] font-medium text-[var(--bt-orange)] hover:underline"
                      onClick={() => setApiDetail(row)}
                    >
                      Request / Response
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager data={apiCalls} page={page} setPage={setPage} />
        </div>
      ) : null}
      {!loading && !error && data && (data.content?.length ?? 0) === 0 ? (
        <div className="bt-card bt-empty-state p-4">No audit records found.</div>
      ) : null}

      <EditorModal
        open={eventDiff != null}
        title="Application event changes"
        description={
          eventDiff
            ? `${eventDiff.eventType} · ${eventDiff.action}${eventDiff.description ? ` — ${eventDiff.description}` : ''}`
            : undefined
        }
        onClose={() => setEventDiff(null)}
      >
        {eventDiff ? (
          <AuditDiffView
            before={eventDiff.previousState}
            after={eventDiff.newState}
            beforeLabel="Before"
            afterLabel="After"
          />
        ) : null}
      </EditorModal>

      <EditorModal
        open={configDiff != null}
        title="Config change comparison"
        description={
          configDiff
            ? `${configDiff.entityType} · ${configDiff.action} · ${configDiff.entityId}`
            : undefined
        }
        onClose={() => setConfigDiff(null)}
      >
        {configDiff ? (
          <AuditDiffView
            before={configDiff.oldRow}
            after={configDiff.newRow}
            beforeLabel="Before"
            afterLabel="After"
            changedFieldsHint={configDiff.changedFields}
          />
        ) : null}
      </EditorModal>

      <EditorModal
        open={apiDetail != null}
        title="Integration API call"
        description={
          apiDetail
            ? `${apiDetail.providerName} · ${apiDetail.apiName}${apiDetail.status ? ` · ${apiDetail.status}` : ''}`
            : undefined
        }
        onClose={() => setApiDetail(null)}
      >
        {apiDetail ? (
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <p className="mb-1 text-[10px] font-semibold uppercase text-[var(--bt-gray-500)]">Request</p>
              <pre className="max-h-[60vh] overflow-auto rounded-lg border border-slate-200 bg-[var(--bt-gray-50)] p-3 text-[11px] whitespace-pre-wrap break-words">
                {apiDetail.requestPayload || '—'}
              </pre>
            </div>
            <div>
              <p className="mb-1 text-[10px] font-semibold uppercase text-[var(--bt-gray-500)]">Response</p>
              <pre className="max-h-[60vh] overflow-auto rounded-lg border border-slate-200 bg-[var(--bt-gray-50)] p-3 text-[11px] whitespace-pre-wrap break-words">
                {apiDetail.responsePayload || apiDetail.errorMessage || '—'}
              </pre>
            </div>
          </div>
        ) : null}
      </EditorModal>
    </div>
  )
}

function Pager<T>({
  data,
  page,
  setPage,
}: {
  data: PageResult<T>
  page: number
  setPage: (fn: (p: number) => number) => void
}) {
  return (
    <div className="flex items-center justify-between border-t border-[var(--bt-gray-100)] px-4 py-3 text-sm">
      <span>
        Page {data.number + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} events)
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={page <= 0}
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          Previous
        </button>
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={page + 1 >= (data.totalPages || 1)}
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}
