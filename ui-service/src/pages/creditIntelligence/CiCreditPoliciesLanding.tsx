import { useRef, useState, type DragEvent, type FormEvent } from 'react'
import { PoliciesWorkspaceNav } from '@/components/workspace/PoliciesWorkspaceNav'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

type Kind = 'banking' | 'bureau' | 'kyc'

/**
 * POLICY-CREATION-1 — Credit Policies landing (create / upload / copy / list).
 */
export function CiCreditPoliciesLanding({
  landing,
  loading,
  busy,
  error,
  demos,
  onCreateScratch,
  onUploadFile,
  onCopy,
  onOpen,
  onOpenDemo,
}: {
  landing: Record<string, unknown> | null
  loading: boolean
  busy: boolean
  error: string | null
  demos: unknown[]
  onCreateScratch: (name: string, description: string) => void
  onUploadFile: (file: File | null | undefined) => void
  onCopy: (documentId: string, policyName?: string) => void
  onOpen: (documentId: string) => void
  onOpenDemo: (kind: Kind) => void
}) {
  const [createOpen, setCreateOpen] = useState(false)
  const [createPath, setCreatePath] = useState<'menu' | 'scratch' | 'upload' | 'copy'>('menu')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [copySourceId, setCopySourceId] = useState('')
  const [copyName, setCopyName] = useState('')
  const [examplesOpen, setExamplesOpen] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const policies = asList(landing?.existingPolicies)

  const scopeLabel = (row: Record<string, unknown>) => {
    const products = asList(row.products)
    if (products.length) return products.map(String).join(', ')
    const scope = asRecord(row.scopeSummary)
    if (scope.appliesTo) return String(scope.appliesTo)
    return '—'
  }

  const resetCreate = () => {
    setCreateOpen(false)
    setCreatePath('menu')
    setName('')
    setDescription('')
    setCopySourceId('')
    setCopyName('')
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const f = e.dataTransfer.files?.[0]
    if (f) {
      onUploadFile(f)
      resetCreate()
    }
  }

  const onSubmitUpload = (e: FormEvent) => {
    e.preventDefault()
    onUploadFile(fileRef.current?.files?.[0])
    resetCreate()
  }

  return (
    <div data-testid="credit-policies-landing">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Credit Policies</h1>
          <p className="mt-1 max-w-2xl text-sm text-slate-600">
            Create and manage underwriting policies for your lending products and customer segments.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm"
            data-testid="create-policy"
            disabled={busy}
            onClick={() => {
              setCreateOpen(true)
              setCreatePath('menu')
            }}
          >
            + Create Policy
          </button>
          <button
            type="button"
            className="bt-btn bt-btn-secondary bt-btn-sm"
            disabled={busy}
            onClick={() => {
              setCreateOpen(true)
              setCreatePath('upload')
            }}
          >
            Upload Existing Policy
          </button>
        </div>
      </div>

      <PoliciesWorkspaceNav />

      {loading ? <p className="text-sm text-slate-600">Loading policies…</p> : null}
      {error ? (
        <p className="mb-4 rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">
          {error}
        </p>
      ) : null}

      <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 px-4 py-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Existing policies</h2>
        </div>
        {policies.length === 0 ? (
          <div className="px-4 py-10 text-center text-sm text-slate-600">
            No policies yet. Use <strong>+ Create Policy</strong> to start from scratch, upload, or copy.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm" data-testid="existing-policies-table">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-2 font-semibold">Policy</th>
                  <th className="px-4 py-2 font-semibold">Status</th>
                  <th className="px-4 py-2 font-semibold">Scope</th>
                  <th className="px-4 py-2 font-semibold">Version</th>
                  <th className="px-4 py-2 font-semibold">Rules</th>
                  <th className="px-4 py-2 font-semibold">Need input</th>
                  <th className="px-4 py-2 font-semibold">Effective</th>
                  <th className="px-4 py-2 font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody>
                {policies.map((raw, i) => {
                  const row = asRecord(raw)
                  const docId = String(row.documentId ?? '')
                  return (
                    <tr key={docId || i} className="border-t border-slate-100">
                      <td className="px-4 py-3 font-medium text-slate-900">
                        {String(row.policyName ?? '—')}
                        {row.demo ? (
                          <span className="ml-2 text-[10px] font-semibold uppercase text-amber-700">Example</span>
                        ) : null}
                        {row.copiedFromLabel ? (
                          <div className="text-xs font-normal text-slate-500">{String(row.copiedFromLabel)}</div>
                        ) : null}
                      </td>
                      <td className="px-4 py-3">{String(row.status ?? 'DRAFT')}</td>
                      <td className="px-4 py-3 text-slate-700">{scopeLabel(row)}</td>
                      <td className="px-4 py-3">{String(row.policyVersion ?? '—')}</td>
                      <td className="px-4 py-3">{row.underwritingRuleCount != null ? String(row.underwritingRuleCount) : '—'}</td>
                      <td className="px-4 py-3">{row.needsInputCount != null ? String(row.needsInputCount) : '—'}</td>
                      <td className="px-4 py-3">{row.effectiveFrom ? String(row.effectiveFrom) : '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            className="bt-btn bt-btn-primary bt-btn-sm"
                            disabled={busy || !docId}
                            onClick={() => onOpen(docId)}
                          >
                            Open
                          </button>
                          <button
                            type="button"
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            disabled={busy || !docId}
                            onClick={() => {
                              setCreateOpen(true)
                              setCreatePath('copy')
                              setCopySourceId(docId)
                              setCopyName(`${String(row.policyName ?? 'Policy')} (Copy)`)
                            }}
                          >
                            Copy
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <details
        className="mt-6 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3"
        open={examplesOpen}
        onToggle={(e) => setExamplesOpen((e.target as HTMLDetailsElement).open)}
      >
        <summary className="cursor-pointer text-sm font-semibold text-slate-700">
          Examples &amp; templates
          <span className="ml-2 text-xs font-normal text-slate-500">Development / demo</span>
        </summary>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          {demos.map((d) => {
            const row = asRecord(d)
            const kind = String(row.kind ?? '') as Kind
            return (
              <button
                key={kind}
                type="button"
                disabled={busy || (kind !== 'banking' && kind !== 'bureau' && kind !== 'kyc')}
                onClick={() => onOpenDemo(kind)}
                className="rounded-lg border border-slate-200 bg-white px-3 py-3 text-left text-sm hover:border-sky-300"
              >
                <div className="font-medium text-slate-900">{String(row.name ?? kind)}</div>
                <div className="mt-1 text-xs text-slate-500">{String(row.description ?? '')}</div>
              </button>
            )
          })}
        </div>
      </details>

      {createOpen ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4" role="dialog">
          <div className="w-full max-w-lg rounded-xl border border-slate-200 bg-white p-5 shadow-xl">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">
                {createPath === 'menu'
                  ? 'Create Policy'
                  : createPath === 'scratch'
                    ? 'Start from scratch'
                    : createPath === 'upload'
                      ? 'Upload existing policy'
                      : 'Copy existing policy'}
              </h2>
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={resetCreate}>
                Close
              </button>
            </div>

            {createPath === 'menu' ? (
              <div className="space-y-2">
                <button
                  type="button"
                  className="w-full rounded-lg border border-slate-200 px-4 py-3 text-left hover:bg-slate-50"
                  data-testid="create-scratch"
                  onClick={() => setCreatePath('scratch')}
                >
                  <div className="font-medium text-slate-900">Start from scratch</div>
                  <div className="text-xs text-slate-500">Name the policy, then define Scope and Rules.</div>
                </button>
                <button
                  type="button"
                  className="w-full rounded-lg border border-slate-200 px-4 py-3 text-left hover:bg-slate-50"
                  onClick={() => setCreatePath('upload')}
                >
                  <div className="font-medium text-slate-900">Upload existing policy</div>
                  <div className="text-xs text-slate-500">PDF, DOCX, or TXT — extracted as proposed draft rules.</div>
                </button>
                <button
                  type="button"
                  className="w-full rounded-lg border border-slate-200 px-4 py-3 text-left hover:bg-slate-50"
                  data-testid="create-copy"
                  onClick={() => setCreatePath('copy')}
                >
                  <div className="font-medium text-slate-900">Copy existing policy</div>
                  <div className="text-xs text-slate-500">New draft — source policy is not changed.</div>
                </button>
              </div>
            ) : null}

            {createPath === 'scratch' ? (
              <div className="space-y-3">
                <label className="block text-sm">
                  <span className="text-slate-600">Policy name</span>
                  <input
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="SME Term Loan Policy"
                    data-testid="scratch-policy-name"
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-slate-600">Description (optional)</span>
                  <textarea
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                    rows={3}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </label>
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm"
                  disabled={busy || !name.trim()}
                  onClick={() => {
                    onCreateScratch(name.trim(), description.trim())
                    resetCreate()
                  }}
                >
                  Create draft
                </button>
              </div>
            ) : null}

            {createPath === 'upload' ? (
              <form onSubmit={onSubmitUpload} className="space-y-3">
                <div
                  onDragOver={(e) => {
                    e.preventDefault()
                    setDragOver(true)
                  }}
                  onDragLeave={() => setDragOver(false)}
                  onDrop={onDrop}
                  className={`rounded-xl border-2 border-dashed px-6 py-8 text-center ${
                    dragOver ? 'border-sky-500 bg-sky-50' : 'border-slate-300 bg-slate-50'
                  }`}
                >
                  <p className="text-sm font-medium text-slate-800">Drop policy file here</p>
                  <p className="mt-1 text-xs text-slate-500">PDF, DOCX, TXT</p>
                  <input
                    ref={fileRef}
                    type="file"
                    accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                    className="mt-4 block w-full text-sm"
                  />
                </div>
                <p className="text-xs text-slate-500">
                  Extracted clauses become proposed rules in the same Policy Studio draft for review.
                </p>
                <button type="submit" className="bt-btn bt-btn-primary bt-btn-sm" disabled={busy}>
                  {busy ? 'Processing…' : 'Upload & open draft'}
                </button>
              </form>
            ) : null}

            {createPath === 'copy' ? (
              <div className="space-y-3">
                <label className="block text-sm">
                  <span className="text-slate-600">Source policy</span>
                  <select
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                    value={copySourceId}
                    onChange={(e) => {
                      setCopySourceId(e.target.value)
                      const src = policies.find((p) => String(asRecord(p).documentId) === e.target.value)
                      if (src) setCopyName(`${String(asRecord(src).policyName ?? 'Policy')} (Copy)`)
                    }}
                    data-testid="copy-source"
                  >
                    <option value="">Select…</option>
                    {policies.map((p, i) => {
                      const row = asRecord(p)
                      const id = String(row.documentId ?? '')
                      if (!id) return null
                      return (
                        <option key={id || i} value={id}>
                          {String(row.policyName)} · {String(row.policyVersion ?? '')} · {String(row.status ?? '')}
                        </option>
                      )
                    })}
                  </select>
                </label>
                <label className="block text-sm">
                  <span className="text-slate-600">New draft name</span>
                  <input
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                    value={copyName}
                    onChange={(e) => setCopyName(e.target.value)}
                  />
                </label>
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm"
                  disabled={busy || !copySourceId}
                  onClick={() => {
                    onCopy(copySourceId, copyName.trim() || undefined)
                    resetCreate()
                  }}
                >
                  Create copy draft
                </button>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  )
}
