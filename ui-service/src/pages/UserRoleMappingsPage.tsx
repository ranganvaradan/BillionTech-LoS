import { useCallback, useEffect, useState } from 'react'
import {
  ASSIGNMENT_ROLE_CODES,
  ASSIGNMENT_ROLE_LABELS,
  createUserRoleMapping,
  deleteUserRoleMapping,
  listLosUsers,
  listUserRoleMappings,
  updateUserRoleMapping,
  type LosUserResponse,
  type AssignmentRoleCode,
  type UserRoleMappingRequest,
  type UserRoleMappingResponse,
} from '@/api/losDirectory'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import {
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]
const BLANK = '__any__'
const ALL_PRODUCTS = '__all_products__'

function productLabel(p: string | null | undefined): string {
  return p == null || p === '' ? 'All Products' : loanProductLabel(p)
}

export function UserRoleMappingsPage() {
  const [rows, setRows] = useState<UserRoleMappingResponse[] | null>(null)
  const [users, setUsers] = useState<LosUserResponse[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UserRoleMappingResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<AssignmentRoleCode>('CREDIT_OFFICER')
  const [productSelection, setProductSelection] = useState<string>(ALL_PRODUCTS)
  const [borrowerType, setBorrowerType] = useState<string | typeof BLANK>(BLANK)
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [priority, setPriority] = useState('50')
  const [active, setActive] = useState(true)

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const [m, u] = await Promise.all([listUserRoleMappings(), listLosUsers()])
      setRows(m)
      setUsers(u)
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

  function apply(r: UserRoleMappingResponse) {
    setSelected(r)
    setIsCreating(false)
    setUserId(r.userId)
    setRole(r.role as AssignmentRoleCode)
    setProductSelection(
      r.loanProduct == null || r.loanProduct === '' ? ALL_PRODUCTS : r.loanProduct,
    )
    setBorrowerType(r.borrowerType ?? BLANK)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setPriority(String(r.priority))
    setActive(r.active)
    setActionError(null)
  }

  function toBody(): UserRoleMappingRequest {
    const p =
      productSelection === ALL_PRODUCTS || !productSelection.trim() ? null : productSelection.trim()
    return {
      userId,
      role,
      loanProduct: p,
      borrowerType: borrowerType === BLANK ? null : borrowerType,
      minAmount: minAmount ? Number.parseFloat(minAmount) : null,
      maxAmount: maxAmount ? Number.parseFloat(maxAmount) : null,
      geography:
        geoState || geoCity ? { state: geoState || undefined, city: geoCity || undefined } : null,
      priority: Number.parseInt(priority, 10) || 0,
      active,
    }
  }

  async function onSave() {
    setActionError(null)
    if (!userId) {
      setActionError('Select a user.')
      return
    }
    setSaving(true)
    try {
      if (isCreating) {
        const c = await createUserRoleMapping(toBody())
        setRows((p) => (p ? [c, ...p] : [c]))
        setIsCreating(false)
        apply(c)
      } else {
        if (!selected) return
        const u = await updateUserRoleMapping(selected.id, toBody())
        setRows((p) => (p ? p.map((x) => (x.id === u.id ? u : x)) : [u]))
        apply(u)
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  function startCreate() {
    setIsCreating(true)
    setSelected(null)
    setUserId(users[0]?.id ?? '')
    setRole('CREDIT_OFFICER')
    setProductSelection(ALL_PRODUCTS)
    setBorrowerType(BLANK)
    setMinAmount('')
    setMaxAmount('')
    setGeoState('')
    setGeoCity('')
    setPriority('50')
    setActive(true)
    setActionError(null)
  }

  return (
    <div>
      <PageHeader
        title="User–role mappings"
        description="Define which user can be assigned for which product, customer segment, amount range, and geography. Used when an assignment rule specifies a role but not a user."
      />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Mappings"
            count={rows.length}
            action={
              <button type="button" onClick={startCreate} className="bt-btn bt-btn-primary bt-btn-sm">
                Create mapping
              </button>
            }
          >
            {rows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => apply(r)}
                avatar={r.userName ?? r.userId}
                title={`${r.userName ?? r.userId}`}
                subtitle={ASSIGNMENT_ROLE_LABELS[r.role as AssignmentRoleCode] ?? r.role}
                meta={
                  r.active ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                }
                tags={
                  <>
                    <span className="bt-tag">{productLabel(r.loanProduct)}</span>
                    <span className="bt-tag">{r.borrowerType ?? 'Any borrower'}</span>
                    <span className="bt-tag">P{r.priority}</span>
                  </>
                }
              />
            ))}
          </MasterListPanel>

          {selected || isCreating ? (
            <DetailPanel
              title={isCreating ? 'New mapping' : `${selected?.userName ?? 'Mapping'}`}
              description="Define which user can be assigned for a product, segment, amount range, and geography."
              badge={
                !isCreating && selected ? (
                  selected.active ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                ) : undefined
              }
            >
              {actionError ? <p className="bt-alert bt-alert-warning mb-4">{actionError}</p> : null}
              <DetailSection title="Assignment criteria">
              <div className="grid gap-2 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <label className="block text-xs text-slate-500">User</label>
                  <select
                    className="bt-input w-full"
                    value={userId}
                    onChange={(e) => setUserId(e.target.value)}
                  >
                    <option value="">— select —</option>
                    {users
                      .filter((u) => u.active)
                      .map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name} ({u.email})
                        </option>
                      ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-slate-500">Role</label>
                  <select
                    className="bt-input w-full"
                    value={role}
                    onChange={(e) => setRole(e.target.value as AssignmentRoleCode)}
                  >
                    {ASSIGNMENT_ROLE_CODES.map((c) => (
                      <option key={c} value={c}>
                        {ASSIGNMENT_ROLE_LABELS[c]}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-slate-500">Priority</label>
                  <input
                    className="bt-input w-full"
                    value={priority}
                    onChange={(e) => setPriority(e.target.value.replace(/\D/g, ''))}
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs text-slate-500">Loan product</label>
                  <select
                    className="mt-0.5 bt-input w-full"
                    value={productSelection}
                    onChange={(e) => {
                      setProductSelection(e.target.value)
                      setActionError(null)
                    }}
                  >
                    <option value={ALL_PRODUCTS}>All Products</option>
                    {productSelection !== ALL_PRODUCTS && productSelection && !isLoanProductCode(productSelection) ? (
                      <option value={productSelection}>
                        {loanProductLabel(productSelection)} (other)
                      </option>
                    ) : null}
                    {LOAN_PRODUCT_CODES.map((p) => (
                      <option key={p} value={p}>
                        {LOAN_PRODUCT_LABELS[p]}
                      </option>
                    ))}
                  </select>
                </div>
                <select
                  className="border px-2 py-1"
                  value={borrowerType}
                  onChange={(e) => setBorrowerType(e.target.value as typeof BLANK | BorrowerType)}
                >
                  <option value={BLANK}>any borrower</option>
                  {BORROWER_TYPES.map((b) => (
                    <option key={b} value={b}>
                      {BORROWER_TYPE_LABELS[b]}
                    </option>
                  ))}
                </select>
                <input
                  className="border px-2 py-1"
                  placeholder="min amount"
                  value={minAmount}
                  onChange={(e) => setMinAmount(e.target.value)}
                />
                <input
                  className="border px-2 py-1"
                  placeholder="max amount"
                  value={maxAmount}
                  onChange={(e) => setMaxAmount(e.target.value)}
                />
                <input
                  className="border px-2 py-1"
                  placeholder="state"
                  value={geoState}
                  onChange={(e) => setGeoState(e.target.value)}
                />
                <input
                  className="border px-2 py-1"
                  placeholder="city"
                  value={geoCity}
                  onChange={(e) => setGeoCity(e.target.value)}
                />
              </div>
              <label className="flex items-center gap-2 text-xs">
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                Active
              </label>
              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => void onSave()}
                  className="bt-btn bt-btn-primary bt-btn-sm"
                >
                  {saving ? '…' : isCreating ? 'Create' : 'Save'}
                </button>
                {!isCreating && selected && (
                  <button
                    type="button"
                    onClick={async () => {
                      if (!window.confirm('Delete this mapping?')) return
                      setSaving(true)
                      try {
                        await deleteUserRoleMapping(selected.id)
                        setSelected(null)
                        void load()
                      } catch (e) {
                        setActionError(e instanceof Error ? e.message : 'Delete failed')
                      } finally {
                        setSaving(false)
                      }
                    }}
                    className="bt-btn bt-btn-danger bt-btn-sm"
                  >
                    Delete
                  </button>
                )}
              </div>
              </DetailSection>
            </DetailPanel>
          ) : (
            <DetailEmptyState
              title="Select a mapping"
              description="Choose a user–role mapping from the list to edit it, or create a new one."
              action={
                <button type="button" onClick={startCreate} className="bt-btn bt-btn-primary">
                  Create mapping
                </button>
              }
            />
          )}
        </MasterDetailLayout>
      )}
    </div>
  )
}
