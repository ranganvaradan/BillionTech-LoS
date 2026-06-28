import { useCallback, useEffect, useMemo, useState } from 'react'
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
import { BtBadge } from '@/components/ui/BtBadge'
import { BtButton } from '@/components/ui/BtButton'

export function UsersPage() {
  const [rows, setRows] = useState<LosUserResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<LosUserResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionOk, setActionOk] = useState<string | null>(null)
  const [search, setSearch] = useState('')

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
    void load()
  }, [load])

  const filteredRows = useMemo(() => {
    if (!rows) return []
    const q = search.trim().toLowerCase()
    if (!q) return rows
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.email.toLowerCase().includes(q) ||
        (r.mobile ?? '').toLowerCase().includes(q),
    )
  }, [rows, search])

  function startCreate() {
    setIsCreating(true)
    setSelected(null)
    setName('New user')
    setEmail('')
    setMobile('')
    setActive(true)
    setActionError(null)
  }

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
    setActionOk(null)
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
        setActionOk(
          `User created. Temporary password: Temp@123 — they must change it on first sign-in.`,
        )
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
    <div className="space-y-4">
      <PageHeader
        title="User directory"
        description="Local LOS users for assignment and role mapping. This is a demo directory separate from IAM."
      />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {actionOk ? <BtAlert tone="success">{actionOk}</BtAlert> : null}
      {actionError ? <BtAlert tone="error">{actionError}</BtAlert> : null}
      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Users"
            count={filteredRows.length}
            search={search}
            onSearchChange={setSearch}
            searchPlaceholder="Search by name or email…"
            action={<BtButton size="sm" onClick={startCreate}>Create user</BtButton>}
            empty={
              filteredRows.length === 0 ? (
                <div className="bt-master-list-empty">
                  {search.trim() ? 'No users match your search.' : 'No users yet.'}
                </div>
              ) : undefined
            }
          >
            {filteredRows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => applyRow(r)}
                avatar={r.name}
                title={r.name}
                subtitle={r.email}
                meta={r.active ? <BtBadge tone="green">Active</BtBadge> : <BtBadge tone="gray">Inactive</BtBadge>}
                tags={r.mobile ? <span className="bt-tag">{r.mobile}</span> : undefined}
              />
            ))}
          </MasterListPanel>

          {selected || isCreating ? (
            <DetailPanel
              title={isCreating ? 'New user' : name}
              description={
                isCreating
                  ? 'Add a local LOS user for assignment rules and role mapping. A temporary password (Temp@123) is assigned automatically; the user must change it on first sign-in.'
                  : 'Update contact details and availability for this user.'
              }
              badge={
                !isCreating && selected ? (
                  selected.active ? <BtBadge tone="green">Active</BtBadge> : <BtBadge tone="gray">Inactive</BtBadge>
                ) : undefined
              }
              footer={
                <DetailActions>
                  <BtButton disabled={saving} onClick={() => void onSave()}>
                    {saving ? 'Saving…' : isCreating ? 'Create user' : 'Save changes'}
                  </BtButton>
                  {!isCreating && selected?.active ? (
                    <BtButton
                      variant="danger"
                      disabled={saving}
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
                    >
                      Deactivate
                    </BtButton>
                  ) : null}
                </DetailActions>
              }
            >
              {actionError ? <BtAlert tone="warning" className="mb-4">{actionError}</BtAlert> : null}
              <DetailSection title="Profile">
                <div className="space-y-3">
                  <FormField label="Name">
                    <input className="bt-input w-full" value={name} onChange={(e) => setName(e.target.value)} />
                  </FormField>
                  <FormField label="Email">
                    <input
                      className="bt-input w-full"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="off"
                    />
                  </FormField>
                  <FormField label="Mobile">
                    <input className="bt-input w-full" value={mobile} onChange={(e) => setMobile(e.target.value)} />
                  </FormField>
                  <label className="flex items-center gap-2 text-sm text-[var(--bt-gray-700)]">
                    <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                    Active
                  </label>
                </div>
              </DetailSection>
            </DetailPanel>
          ) : (
            <DetailEmptyState
              title="Select a user"
              description="Choose someone from the directory to view or edit their profile, or create a new user."
              action={<BtButton onClick={startCreate}>Create user</BtButton>}
            />
          )}
        </MasterDetailLayout>
      )}
    </div>
  )
}
