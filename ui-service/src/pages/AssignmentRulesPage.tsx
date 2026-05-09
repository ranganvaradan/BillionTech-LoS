import { useCallback, useEffect, useState } from 'react'
import {
  activateAssignmentRule,
  createAssignmentRule,
  deactivateAssignmentRule,
  deleteAssignmentRule,
  listAssignmentRules,
  updateAssignmentRule,
  type AssignmentRuleSetRequest,
  type AssignmentRuleSetResponse,
} from '@/api/assignmentRules'
import {
  ASSIGNMENT_ROLE_CODES,
  ASSIGNMENT_ROLE_LABELS,
  listLosUsers,
  type LosUserResponse,
} from '@/api/losDirectory'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

export function AssignmentRulesPage() {
  const [rows, setRows] = useState<AssignmentRuleSetResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<AssignmentRuleSetResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [minTenure, setMinTenure] = useState('')
  const [maxTenure, setMaxTenure] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [priority, setPriority] = useState('50')
  const [assignedRole, setAssignedRole] = useState<string>('CREDIT_MANAGER')
  const [assignedUserId, setAssignedUserId] = useState('')
  const [usersForRole, setUsersForRole] = useState<LosUserResponse[]>([])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const rules = await listAssignmentRules()
      setRows(rules)
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

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const u = await listLosUsers(assignedRole)
        if (!cancelled) setUsersForRole(u)
      } catch {
        if (!cancelled) setUsersForRole([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [assignedRole])

  function roleLabel(code: string) {
    return (ASSIGNMENT_ROLE_LABELS as Record<string, string>)[code] ?? code
  }

  function applyRule(r: AssignmentRuleSetResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    setMinTenure(r.minTenureMonths != null ? String(r.minTenureMonths) : '')
    setMaxTenure(r.maxTenureMonths != null ? String(r.maxTenureMonths) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setPriority(String(r.priority))
    setAssignedRole(r.assignedRole)
    setAssignedUserId(r.assignedUserId ?? '')
    setActionError(null)
  }

  function toBody(): AssignmentRuleSetRequest {
    return {
      name: name.trim() || 'Unnamed',
      borrowerType: borrowerType,
      loanProduct: loanProduct.trim() || 'PERSONAL_LOAN',
      minAmount: minAmount ? Number.parseFloat(minAmount) : null,
      maxAmount: maxAmount ? Number.parseFloat(maxAmount) : null,
      minTenureMonths: minTenure ? Number.parseInt(minTenure, 10) : null,
      maxTenureMonths: maxTenure ? Number.parseInt(maxTenure, 10) : null,
      geography:
        geoState || geoCity
          ? { state: geoState || undefined, city: geoCity || undefined }
          : null,
      priority: Number.parseInt(priority, 10) || 0,
      assignedRole: assignedRole,
      assignedUserId: assignedUserId && /^[0-9a-f-]{36}$/i.test(assignedUserId) ? assignedUserId : null,
    }
  }

  async function onSave() {
    setActionError(null)
    if (!loanProduct.trim()) {
      setActionError('Enter loan product (exact match for applications).')
      return
    }
    setSaving(true)
    try {
      if (isCreating) {
        const c = await createAssignmentRule(toBody())
        setRows((p) => (p ? [c, ...p] : [c]))
        setIsCreating(false)
        applyRule(c)
      } else {
        if (!selected) return
        const u = await updateAssignmentRule(selected.id, toBody())
        setRows((p) => (p ? p.map((x) => (x.id === u.id ? u : x)) : [u]))
        applyRule(u)
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
        title="Assignment rules"
        description="Credit Manager setup — route applications to a role and optional user when they enter underwriting, using the same match dimensions as other policy rules. Highest priority match wins."
      />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {rows && !loading && (
        <div className="grid gap-6 lg:grid-cols-2">
          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Rules</h2>
            <ul className="divide-y rounded-lg border border-slate-200 bg-white">
              {rows.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => applyRule(r)}
                    className="w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                  >
                    <div className="font-medium text-slate-900">{r.name}</div>
                    <div className="text-xs text-slate-500">
                      {BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType} · {loanProductLabel(r.loanProduct)} ·
                      p{r.priority} · {roleLabel(r.assignedRole)}
                      {r.active ? (
                        <span className="ml-1 rounded bg-emerald-100 px-1 text-emerald-800">on</span>
                      ) : (
                        <span className="ml-1 rounded bg-slate-100 px-1">off</span>
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
                setName('New assignment')
                setAssignedRole('CREDIT_MANAGER')
                setAssignedUserId('')
                setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
                setActionError(null)
              }}
              className="mt-2 rounded border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs text-white"
            >
              New
            </button>
          </div>
          {(selected || isCreating) && (
            <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-4 text-sm">
              {actionError ? <p className="text-amber-800">{actionError}</p> : null}
              <input className="w-full border px-2 py-1" value={name} onChange={(e) => setName(e.target.value)} />
              <div className="grid gap-2 sm:grid-cols-2">
                <select
                  className="border px-2 py-1"
                  value={borrowerType}
                  onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}
                >
                  {BORROWER_TYPES.map((b) => (
                    <option key={b} value={b}>
                      {BORROWER_TYPE_LABELS[b]}
                    </option>
                  ))}
                </select>
                <select
                  className="border bg-white px-2 py-1"
                  value={loanProduct}
                  onChange={(e) => setLoanProduct(e.target.value)}
                >
                  {LOAN_PRODUCT_CODES.map((c) => (
                    <option key={c} value={c}>
                      {LOAN_PRODUCT_LABELS[c]}
                    </option>
                  ))}
                  {loanProduct && !isLoanProductCode(loanProduct) ? (
                    <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy)</option>
                  ) : null}
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
                  placeholder="min tenure"
                  value={minTenure}
                  onChange={(e) => setMinTenure(e.target.value)}
                />
                <input
                  className="border px-2 py-1"
                  placeholder="max tenure"
                  value={maxTenure}
                  onChange={(e) => setMaxTenure(e.target.value)}
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
                <input
                  className="border px-2 py-1"
                  placeholder="priority"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value.replace(/\D/g, ''))}
                />
                <select
                  className="border px-2 py-1"
                  value={assignedRole}
                  onChange={(e) => {
                    setAssignedRole(e.target.value)
                    setAssignedUserId('')
                  }}
                >
                  {assignedRole && !ASSIGNMENT_ROLE_CODES.includes(assignedRole as (typeof ASSIGNMENT_ROLE_CODES)[number]) ? (
                    <option value={assignedRole}>
                      {assignedRole} (legacy — change before save)
                    </option>
                  ) : null}
                  {ASSIGNMENT_ROLE_CODES.map((c) => (
                    <option key={c} value={c}>
                      {ASSIGNMENT_ROLE_LABELS[c]}
                    </option>
                  ))}
                </select>
                <div className="sm:col-span-2">
                  <label className="mb-0.5 block text-xs text-slate-500">User (optional)</label>
                  <select
                    className="w-full border px-2 py-1"
                    value={assignedUserId}
                    onChange={(e) => setAssignedUserId(e.target.value)}
                  >
                    <option value="">Unassigned (resolve from user–role mappings)</option>
                    {usersForRole.map((u) => (
                      <option key={u.id} value={u.id}>
                        {u.name} ({u.email})
                      </option>
                    ))}
                  </select>
                  {usersForRole.length === 0 ? (
                    <p className="mt-1 text-xs text-amber-800">No users mapped to this role</p>
                  ) : null}
                </div>
              </div>
              <button
                type="button"
                disabled={saving}
                onClick={() => void onSave()}
                className="rounded bg-slate-900 px-3 py-1.5 text-white"
              >
                {saving ? '…' : isCreating ? 'Create' : 'Save'}
              </button>
              {!isCreating && selected ? (
                <div className="flex flex-wrap gap-2 pt-2">
                  {selected.active ? (
                    <button
                      type="button"
                      disabled={toggling}
                      onClick={async () => {
                        setToggling(true)
                        try {
                          await deactivateAssignmentRule(selected.id)
                          void load()
                        } finally {
                          setToggling(false)
                        }
                      }}
                    >
                      Deactivate
                    </button>
                  ) : (
                    <button
                      type="button"
                      disabled={toggling}
                      onClick={async () => {
                        setToggling(true)
                        try {
                          await activateAssignmentRule(selected.id)
                          void load()
                        } finally {
                          setToggling(false)
                        }
                      }}
                    >
                      Activate
                    </button>
                  )}
                  <button
                    type="button"
                    disabled={selected.active}
                    onClick={async () => {
                      if (!selected.active) {
                        await deleteAssignmentRule(selected.id)
                        setSelected(null)
                        void load()
                      }
                    }}
                  >
                    Delete (inactive)
                  </button>
                </div>
              ) : null}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
