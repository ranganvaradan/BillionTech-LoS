import { useCallback, useEffect, useState } from 'react'
import {
  createLosUser,
  deactivateLosUser,
  listLosUsers,
  updateLosUser,
  type LosUserRequest,
  type LosUserResponse,
} from '@/api/losDirectory'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'

export function UsersPage() {
  const [rows, setRows] = useState<LosUserResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<LosUserResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [mobile, setMobile] = useState('')
  const [active, setActive] = useState(true)

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const u = await listLosUsers()
      setRows(u)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load
    void load()
  }, [load])

  function applyRow(u: LosUserResponse) {
    setSelected(u)
    setIsCreating(false)
    setName(u.name)
    setEmail(u.email)
    setMobile(u.mobile ?? '')
    setActive(u.active)
    setActionError(null)
  }

  function toRequest(): LosUserRequest {
    return { name, email, mobile, active }
  }

  async function onSave() {
    setActionError(null)
    if (!name.trim() || !email.trim()) {
      setActionError('Name and email are required.')
      return
    }
    setSaving(true)
    try {
      if (isCreating) {
        const c = await createLosUser(toRequest())
        setRows((p) => (p ? [c, ...p] : [c]))
        setIsCreating(false)
        applyRow(c)
      } else {
        if (!selected) return
        const u = await updateLosUser(selected.id, toRequest())
        setRows((p) => (p ? p.map((x) => (x.id === u.id ? u : x)) : [u]))
        applyRow(u)
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="User directory"
        description="Local LOS users for assignment and role mapping. This is a demo directory separate from IAM."
      />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {rows && !loading && (
        <div className="grid gap-6 lg:grid-cols-2">
          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Users</h2>
            <ul className="divide-y rounded-lg border border-slate-200 bg-white">
              {rows.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => applyRow(r)}
                    className="w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                  >
                    <div className="font-medium text-slate-900">{r.name}</div>
                    <div className="text-xs text-slate-500">
                      {r.email}
                      {r.active ? (
                        <span className="ml-1 rounded bg-emerald-100 px-1 text-emerald-800">active</span>
                      ) : (
                        <span className="ml-1 rounded bg-slate-200 px-1">inactive</span>
                      )}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
            <button
              type="button"
              onClick={() => {
                setIsCreating(true)
                setSelected(null)
                setName('New user')
                setEmail('')
                setMobile('')
                setActive(true)
                setActionError(null)
              }}
              className="mt-2 rounded border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs text-white"
            >
              Create user
            </button>
          </div>
          {(selected || isCreating) && (
            <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-4 text-sm">
              {actionError ? <p className="text-amber-800">{actionError}</p> : null}
              <label className="block text-xs text-slate-500">Name</label>
              <input className="w-full border px-2 py-1" value={name} onChange={(e) => setName(e.target.value)} />
              <label className="block text-xs text-slate-500">Email</label>
              <input
                className="w-full border px-2 py-1"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                autoComplete="off"
              />
              <label className="block text-xs text-slate-500">Mobile</label>
              <input
                className="w-full border px-2 py-1"
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
              />
              <label className="flex items-center gap-2 text-xs">
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                Active
              </label>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => void onSave()}
                  className="rounded bg-slate-900 px-3 py-1.5 text-white"
                >
                  {saving ? '…' : isCreating ? 'Create' : 'Save'}
                </button>
                {!isCreating && selected?.active ? (
                  <button
                    type="button"
                    onClick={async () => {
                      if (!selected) return
                      if (!window.confirm('Deactivate this user?')) return
                      setSaving(true)
                      try {
                        await deactivateLosUser(selected.id)
                        void load()
                        setSelected(null)
                      } catch (e) {
                        setActionError(e instanceof Error ? e.message : 'Deactivate failed')
                      } finally {
                        setSaving(false)
                      }
                    }}
                    className="rounded border border-amber-700 px-3 py-1.5 text-amber-900"
                  >
                    Deactivate
                  </button>
                ) : null}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
