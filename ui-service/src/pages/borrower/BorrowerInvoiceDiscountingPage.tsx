import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import {
  acceptInvoice,
  addPaymentCartBulk,
  addPaymentCartLine,
  deleteInvoiceDiscountingInvoice,
  getInvoiceDiscounting,
  getPaymentCart,
  repayInvoiceLoan,
  requestInvoiceFinance,
  type BorrowerInvoiceDiscounting,
  type BorrowerInvoiceItem,
  type BorrowerInvoiceLoan,
} from '@/api/borrowerInvoiceDiscounting'
import { getBorrowerDashboard } from '@/api/borrowerPortal'
import { BorrowerInvoiceLoanCard } from '@/components/borrower/BorrowerInvoiceLoanCard'
import { LosDigitalInvoiceAttachment } from '@/components/borrower/LosDigitalInvoiceAttachment'
import {
  BorrowerInvoiceActionsMenu,
  type InvoiceActionItem,
} from '@/components/borrower/BorrowerInvoiceActionsMenu'
import {
  BorrowerInvoiceUnfinancedRepaymentPanel,
  shouldShowUnfinancedRepayments,
} from '@/components/borrower/BorrowerInvoiceUnfinancedRepaymentPanel'
import { LosEarlyPayRequestModal } from '@/components/borrower/LosEarlyPayRequestModal'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { notifyErrorMessage, notifySuccess } from '@/lib/notify'

const LONG_FEEDBACK_CHARS = 160

function sanitizeNonNegativeNumberInput(raw: string): string {
  if (raw.trim() === '') return ''
  if (raw === '.' || raw === '0.' || /^\d*\.?\d*$/.test(raw)) {
    const n = Number(raw)
    if (raw === '.' || raw.endsWith('.')) return raw.startsWith('-') ? raw.slice(1) : raw
    if (Number.isFinite(n) && n < 0) return String(Math.abs(n))
    return raw.startsWith('-') ? raw.slice(1) : raw
  }
  return raw.replace(/^-/, '')
}

function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

function loanPrincipal(loan: BorrowerInvoiceLoan): number {
  return Number(loan.disbursedAmount ?? loan.sanctionedAmount ?? loan.requestedAmount ?? 0)
}

function loanPayable(loan: BorrowerInvoiceLoan): number {
  return Number(loan.outstandingAmount ?? loan.totalRepayable ?? loanPrincipal(loan))
}

function loanInterest(loan: BorrowerInvoiceLoan): number | null {
  const explicit = Number(loan.interestAmount ?? 0)
  if (explicit > 0) return explicit
  const derived = loanPayable(loan) - loanPrincipal(loan)
  return derived > 0 ? derived : null
}

function interestFromLoans(loans: BorrowerInvoiceLoan[]): number | null {
  let total = 0
  let any = false
  for (const loan of loans) {
    const interest = loanInterest(loan)
    if (interest != null && interest > 0) {
      total += interest
      any = true
    }
  }
  return any ? total : null
}

function defaultFinanceAmount(inv: BorrowerInvoiceItem): string {
  const amt =
    inv.suggestedFinanceAmount ??
    inv.maxFinanceableAmount ??
    inv.availableAmount ??
    inv.eligibleAmount ??
    inv.invoiceAmount
  return amt != null && amt > 0 ? String(amt) : ''
}

function statusBadge(status: string | null): string {
  const s = (status ?? '').toUpperCase()
  if (s.includes('OVERDUE') || s === 'REJECTED') return 'bg-rose-50 text-rose-700 border-rose-200'
  if (s.includes('DISCOUNTED') || s === 'DISBURSED' || s === 'CLOSED')
    return 'bg-emerald-50 text-emerald-700 border-emerald-200'
  if (s === 'ELIGIBLE' || s === 'BORROWER_ACCEPTED') return 'bg-sky-50 text-sky-700 border-sky-200'
  return 'bg-slate-50 text-slate-600 border-slate-200'
}

export function BorrowerInvoiceDiscountingPage({
  flowType,
  title = 'Invoice discounting',
  description = 'Purchase-flow invoices must be accepted before requesting finance.',
  createPath,
  createLabel,
}: {
  flowType?: string
  title?: string
  description?: string
  createPath?: string
  createLabel?: string
} = {}) {
  const [linked, setLinked] = useState<boolean | null>(null)
  const [data, setData] = useState<BorrowerInvoiceDiscounting | null>(null)
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)
  const [financeAmounts, setFinanceAmounts] = useState<Record<string, string>>({})
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({})
  const [expandedLoanInvoiceIds, setExpandedLoanInvoiceIds] = useState<Set<string>>(() => new Set())
  const [expandedUnfinancedRepayIds, setExpandedUnfinancedRepayIds] = useState<Set<string>>(() => new Set())
  const [selectedForCart, setSelectedForCart] = useState<Set<string>>(() => new Set())
  const [addingToCart, setAddingToCart] = useState(false)
  const [lifecycleTab, setLifecycleTab] = useState<'active' | 'closed'>('active')
  const [cartInvoiceIds, setCartInvoiceIds] = useState<Set<string>>(() => new Set())
  const [earlyPayInvoice, setEarlyPayInvoice] = useState<BorrowerInvoiceItem | null>(null)

  const usePayu = data?.paymentMethod === 'PAYU_PG'

  const invoiceById = useMemo(() => {
    const map = new Map<string, BorrowerInvoiceItem>()
    for (const inv of data?.invoices ?? []) {
      map.set(inv.invoiceId, inv)
    }
    return map
  }, [data?.invoices])

  const loansByInvoiceId = useMemo(() => {
    const map = new Map<string, BorrowerInvoiceLoan[]>()
    for (const loan of data?.loans ?? []) {
      const invId = loan.invoiceId
      if (!invId) continue
      const list = map.get(invId) ?? []
      list.push(loan)
      map.set(invId, list)
    }
    return map
  }, [data?.loans])

  const visibleInvoices = useMemo(() => {
    const all = data?.invoices ?? []
    return all.filter((inv) => {
      const s = (inv.status ?? '').toUpperCase()
      const closed = s === 'REJECTED' || s === 'CLOSED'
      return lifecycleTab === 'closed' ? closed : !closed
    })
  }, [data?.invoices, lifecycleTab])

  const toggleLoanDetails = (invoiceId: string) => {
    setExpandedLoanInvoiceIds((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) next.delete(invoiceId)
      else next.add(invoiceId)
      return next
    })
  }

  const toggleUnfinancedRepayments = (invoiceId: string) => {
    setExpandedUnfinancedRepayIds((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) next.delete(invoiceId)
      else next.add(invoiceId)
      return next
    })
  }

  const isInCart = (invoiceId: string) => cartInvoiceIds.has(invoiceId)

  const eligibilityByInvoice = useMemo(() => {
    const map = new Map<string, { payable?: boolean; payableAmount?: number }>()
    for (const row of data?.paymentEligibility ?? []) {
      const invoiceId = String(row.invoiceId ?? '')
      if (!invoiceId) continue
      map.set(invoiceId, {
        payable: Boolean(row.payable),
        payableAmount:
          typeof row.payableAmount === 'number'
            ? row.payableAmount
            : Number(row.payableAmount ?? 0),
      })
    }
    return map
  }, [data?.paymentEligibility])

  const canAddToCart = (inv: BorrowerInvoiceItem) => {
    const elig = eligibilityByInvoice.get(inv.invoiceId)
    return (
      usePayu &&
      Boolean(elig?.payable) &&
      !(inv.pipAmount && inv.pipAmount > 0) &&
      !isInCart(inv.invoiceId)
    )
  }

  const repayableAmountForInvoice = (invoiceId: string) => {
    const elig = eligibilityByInvoice.get(invoiceId)
    if (elig?.payableAmount != null && elig.payableAmount > 0) {
      return elig.payableAmount
    }
    const loan = (loansByInvoiceId.get(invoiceId) ?? []).find((l) => l.repayable)
    if (!loan) return 0
    return loan.outstandingAmount ?? loan.totalRepayable ?? 0
  }

  const cartSelectableIds = useMemo(
    () => (data?.invoices ?? []).filter((inv) => canAddToCart(inv)).map((inv) => inv.invoiceId),
    [data?.invoices, usePayu, cartInvoiceIds, eligibilityByInvoice],
  )

  const selectionSummary = useMemo(() => {
    let total = 0
    for (const id of selectedForCart) {
      total += repayableAmountForInvoice(id)
    }
    return { count: selectedForCart.size, total }
  }, [selectedForCart, loansByInvoiceId])

  const allCartSelectableSelected =
    cartSelectableIds.length > 0 && cartSelectableIds.every((id) => selectedForCart.has(id))

  const toggleCartSelect = (invoiceId: string) => {
    setSelectedForCart((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) next.delete(invoiceId)
      else next.add(invoiceId)
      return next
    })
  }

  const toggleSelectAllCart = () => {
    if (allCartSelectableSelected) {
      setSelectedForCart(new Set())
      return
    }
    setSelectedForCart(new Set(cartSelectableIds))
  }

  const refreshCartInvoiceIds = useCallback(async () => {
    if (!usePayu) {
      setCartInvoiceIds(new Set())
      return
    }
    try {
      const lines = await getPaymentCart()
      setCartInvoiceIds(new Set(lines.map((l) => l.invoiceId)))
    } catch {
      setCartInvoiceIds(new Set())
    }
  }, [usePayu])

  useEffect(() => {
    void refreshCartInvoiceIds()
  }, [refreshCartInvoiceIds])

  useEffect(() => {
    setSelectedForCart((prev) => {
      const next = new Set([...prev].filter((id) => !cartInvoiceIds.has(id)))
      return next.size === prev.size ? prev : next
    })
  }, [cartInvoiceIds])

  const reportSuccess = (message: string) => {
    setActionErr(null)
    notifySuccess(message)
  }

  const reportError = (message: string) => {
    if (message.length > LONG_FEEDBACK_CHARS || message.includes('\n')) {
      setActionErr(message)
      return
    }
    setActionErr(null)
    notifyErrorMessage(message)
  }

  const addToCart = async (invoiceId: string) => {
    setAddingToCart(true)
    setActionErr(null)
    try {
      await addPaymentCartLine(invoiceId)
      reportSuccess('Added to payment cart.')
      await refreshCartInvoiceIds()
      setSelectedForCart((prev) => {
        const next = new Set(prev)
        next.delete(invoiceId)
        return next
      })
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Could not add to cart.')
    } finally {
      setAddingToCart(false)
    }
  }

  const addSelectedToCart = async () => {
    if (selectedForCart.size === 0) return
    setAddingToCart(true)
    setActionErr(null)
    try {
      await addPaymentCartBulk(Array.from(selectedForCart))
      reportSuccess(`Added ${selectedForCart.size} invoice(s) to payment cart.`)
      setSelectedForCart(new Set())
      await refreshCartInvoiceIds()
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Bulk add failed.')
    } finally {
      setAddingToCart(false)
    }
  }

  const buildInvoiceActions = (
    inv: BorrowerInvoiceItem,
    linkedLoans: BorrowerInvoiceLoan[],
  ): InvoiceActionItem[] => {
    const items: InvoiceActionItem[] = []
    if (linkedLoans.length > 0) {
      items.push({
        id: 'view-loan',
        label: expandedLoanInvoiceIds.has(inv.invoiceId) ? 'Hide Loan' : 'View Loan',
        onClick: () => toggleLoanDetails(inv.invoiceId),
      })
    }
    if (shouldShowUnfinancedRepayments(inv)) {
      items.push({
        id: 'view-unfinanced-repayments',
        label: expandedUnfinancedRepayIds.has(inv.invoiceId) ? 'Hide repayments' : 'View repayments',
        onClick: () => toggleUnfinancedRepayments(inv.invoiceId),
      })
    }
    if (canAddToCart(inv)) {
      items.push({
        id: 'add-cart',
        label: 'Add to cart',
        onClick: () => void addToCart(inv.invoiceId),
        disabled: addingToCart,
      })
    }
    return items
  }

  const applyFinanceDefaults = useCallback((payload: BorrowerInvoiceDiscounting) => {
    setFinanceAmounts((prev) => {
      const next = { ...prev }
      for (const inv of payload.invoices) {
        if (inv.financeable) {
          next[inv.invoiceId] = defaultFinanceAmount(inv)
        }
      }
      return next
    })
    setRepayAmounts((prev) => {
      const next = { ...prev }
      for (const loan of payload.loans) {
        if (loan.repayable && !next[loan.loanId]) {
          const out = loan.outstandingAmount
          if (out != null && out > 0) next[loan.loanId] = String(out)
        }
      }
      return next
    })
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    setLoadErr(null)
    try {
      const dash = await getBorrowerDashboard()
      const flowLinked =
        !flowType || flowType === 'PURCHASE_BILL_DISCOUNTING'
          ? dash.purchaseBillDiscountingLinked ?? dash.invoiceDiscountingLinked
          : flowType === 'SALES_BILL_DISCOUNTING'
            ? dash.salesBillDiscountingLinked
            : flowType === 'PURCHASE_ORDER_DISCOUNTING'
              ? dash.purchaseOrderDiscountingLinked
              : dash.invoiceDiscountingLinked
      if (!flowLinked) {
        setLinked(false)
        return
      }
      setLinked(true)
      const payload = await getInvoiceDiscounting(flowType)
      setData(payload)
      applyFinanceDefaults(payload)
    } catch (e) {
      setLoadErr(e instanceof ApiError ? e.message : 'Failed to load invoice discounting')
    } finally {
      setLoading(false)
    }
  }, [applyFinanceDefaults, flowType])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function onAccept(inv: BorrowerInvoiceItem) {
    setActionErr(null)
    setBusyId(inv.invoiceId)
    try {
      const updated = await acceptInvoice(inv.invoiceId)
      setData(updated)
      applyFinanceDefaults(updated)
      reportSuccess(`Invoice ${inv.invoiceNumber ?? ''} accepted. You can now request finance.`)
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Could not accept the invoice.')
    } finally {
      setBusyId(null)
    }
  }

  async function onDeleteInvoice(inv: BorrowerInvoiceItem) {
    if (!inv.deletable) return
    if (!window.confirm('Delete this invoice permanently? This cannot be undone.')) {
      return
    }
    setActionErr(null)
    setBusyId(inv.invoiceId)
    try {
      const updated = await deleteInvoiceDiscountingInvoice(inv.invoiceId)
      setData(updated)
      applyFinanceDefaults(updated)
      reportSuccess(`Invoice ${inv.invoiceNumber ?? ''} deleted.`)
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Could not delete the invoice.')
    } finally {
      setBusyId(null)
    }
  }

  async function onFinance(e: FormEvent, inv: BorrowerInvoiceItem) {
    e.preventDefault()
    setActionErr(null)
    const value = Number(financeAmounts[inv.invoiceId] ?? '')
    if (!Number.isFinite(value) || value <= 0) {
      reportError('Enter a valid finance amount greater than 0.')
      return
    }
    const cap = inv.maxFinanceableAmount ?? inv.availableAmount ?? inv.eligibleAmount ?? 0
    if (cap > 0 && value > cap) {
      reportError(`Finance amount cannot exceed ${money(cap)} (available for this invoice).`)
      return
    }
    setBusyId(inv.invoiceId)
    try {
      const updated = await requestInvoiceFinance(inv.invoiceId, value)
      setData(updated)
      applyFinanceDefaults(updated)
      reportSuccess(`Finance request of ${money(value)} submitted for invoice ${inv.invoiceNumber ?? ''}.`)
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Could not submit the finance request.')
    } finally {
      setBusyId(null)
    }
  }

  async function onRepay(e: FormEvent, loan: BorrowerInvoiceLoan) {
    e.preventDefault()
    setActionErr(null)
    const value = Number(repayAmounts[loan.loanId] ?? '')
    if (!Number.isFinite(value) || value <= 0) {
      reportError('Enter a valid repayment amount greater than 0.')
      return
    }
    setBusyId(loan.loanId)
    try {
      const updated = await repayInvoiceLoan(loan.loanId, value)
      setData(updated)
      applyFinanceDefaults(updated)
      setRepayAmounts((m) => ({ ...m, [loan.loanId]: '' }))
      reportSuccess(`Repayment of ${money(value)} recorded for loan ${loan.loanNumber ?? ''}.`)
    } catch (ex) {
      reportError(ex instanceof ApiError ? ex.message : 'Could not record the repayment.')
    } finally {
      setBusyId(null)
    }
  }

  if (linked === false && !loading) {
    return <Navigate to="/borrower/dashboard" replace />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={title}
        description={description}
        actions={
          createPath ? (
            <Link to={createPath} className="bt-btn bt-btn-primary">
              {createLabel ?? 'Create'}
            </Link>
          ) : undefined
        }
      />

      {loadErr ? (
        <div className="bt-alert bt-alert-error" role="alert">
          {loadErr}
        </div>
      ) : null}

      {actionErr ? (
        <div className="bt-alert bt-alert-error whitespace-pre-wrap break-words" role="alert">
          {actionErr}
        </div>
      ) : null}

      {data && !data.available ? (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-sm text-slate-600 shadow-sm">
          {data.message ?? 'Invoice discounting is not available for your account yet.'}
        </div>
      ) : null}

      {(loading || (data && data.available)) ? (
        <>
          {data && data.available ? (
            <>
          <p className="text-sm text-slate-600">
            {data.invoices.length} invoice(s), {data.loans.length} invoice-discounting loan(s).
            {usePayu ? (
              <>
                {' '}
                Repayments use PayU —{' '}
                <Link to="/borrower/invoice-discounting/payments/cart" className="font-medium text-sky-700 hover:underline">
                  open payment cart
                </Link>
                .
              </>
            ) : null}
          </p>
          <p className="text-sm text-slate-500">
            Purchase-flow invoices must be accepted before requesting finance.
          </p>

          {usePayu && selectionSummary.count > 0 ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-sky-200 bg-sky-50/80 px-4 py-3">
              <p className="text-sm text-slate-700">
                <span className="font-semibold text-sky-900">{selectionSummary.count}</span> invoice
                {selectionSummary.count === 1 ? '' : 's'} selected
                <span className="mx-2 text-slate-300" aria-hidden>
                  |
                </span>
                Total repayment:{' '}
                <span className="font-semibold tabular-nums text-slate-900">{money(selectionSummary.total)}</span>
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setSelectedForCart(new Set())}
                  className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-800"
                >
                  Clear selection
                </button>
                <button
                  type="button"
                  disabled={addingToCart}
                  onClick={() => void addSelectedToCart()}
                  className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
                >
                  Add selected to cart
                </button>
              </div>
            </div>
          ) : null}
            </>
          ) : null}

          <section className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-bl-navy">Your invoices</h2>
              <div className="flex gap-2">
                {(['active', 'closed'] as const).map((tab) => (
                  <button
                    key={tab}
                    type="button"
                    onClick={() => setLifecycleTab(tab)}
                    className={`rounded-lg px-3 py-1 text-xs font-medium capitalize ${
                      lifecycleTab === tab ? 'bg-bl-primary text-white' : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {tab}
                  </button>
                ))}
              </div>
            </div>
              <div className="bt-card shadow-sm">
                <div className="overflow-x-auto">
                <table className="min-w-full text-sm border-collapse">
                  <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-600">
                    <tr>
                      {usePayu ? (
                        <th className="px-3 py-3 w-10 text-center align-middle">
                          {cartSelectableIds.length > 0 ? (
                            <input
                              type="checkbox"
                              checked={allCartSelectableSelected}
                              onChange={toggleSelectAllCart}
                              aria-label="Select all repayable invoices"
                            />
                          ) : null}
                        </th>
                      ) : null}
                      <th className="px-5 py-3 align-middle">Invoice</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Due date</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Amount</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Balance due</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Available</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Interest</th>
                      {usePayu ? <th className="px-5 py-3 align-middle whitespace-nowrap">PRUS</th> : null}
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Copy</th>
                      <th className="px-5 py-3 align-middle whitespace-nowrap">Status</th>
                      <th className="px-3 py-3 align-middle w-44">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {loading ? (
                      <tr>
                        <td colSpan={usePayu ? 11 : 9} className="px-5 py-10">
                          <div
                            className="flex min-h-[12rem] w-full items-center justify-center text-[var(--bt-gray-500)]"
                            role="status"
                          >
                            <div className="flex items-center gap-2 text-sm">
                              <span
                                className="inline-block size-4 animate-spin rounded-full border-2 border-[var(--bt-gray-300)] border-t-[var(--bt-orange)]"
                                aria-hidden
                              />
                              Loading invoices…
                            </div>
                          </div>
                        </td>
                      </tr>
                    ) : visibleInvoices.length === 0 ? (
                      <tr>
                        <td colSpan={usePayu ? 11 : 9} className="px-5 py-10 text-center text-sm text-slate-500">
                          No {lifecycleTab} invoices found for your account.
                        </td>
                      </tr>
                    ) : (
                    visibleInvoices.flatMap((inv) => {
                      const linkedLoans = loansByInvoiceId.get(inv.invoiceId) ?? []
                      const invoiceActions = buildInvoiceActions(inv, linkedLoans)
                      const rows = [
                        <tr key={inv.invoiceId} className="align-middle hover:bg-slate-50/60">
                        {usePayu ? (
                          <td className="px-3 py-3 text-center align-middle">
                            {isInCart(inv.invoiceId) ? (
                              <span
                                className="inline-block rounded bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold text-sky-800"
                                title="Already in payment cart"
                              >
                                In cart
                              </span>
                            ) : canAddToCart(inv) ? (
                              <input
                                type="checkbox"
                                checked={selectedForCart.has(inv.invoiceId)}
                                onChange={() => toggleCartSelect(inv.invoiceId)}
                                aria-label={`Select invoice ${inv.invoiceNumber ?? ''}`}
                              />
                            ) : null}
                          </td>
                        ) : null}
                        <td className="whitespace-nowrap px-5 py-3 align-middle font-medium text-bl-navy">{inv.invoiceNumber ?? '—'}</td>
                        <td className="whitespace-nowrap px-5 py-3 align-middle text-slate-600">{inv.dueDate ?? '—'}</td>
                        <td className="px-5 py-3 align-middle tabular-nums text-slate-700">{money(inv.invoiceAmount)}</td>
                        <td className="px-5 py-3 align-middle tabular-nums font-medium text-amber-800">
                          {inv.balDueAmount != null ? money(inv.balDueAmount) : '—'}
                        </td>
                        <td className="px-5 py-3 align-middle tabular-nums text-slate-700">{money(inv.availableAmount)}</td>
                        <td className="px-5 py-3 align-middle tabular-nums text-slate-600">
                          {money(interestFromLoans(linkedLoans))}
                        </td>
                        {usePayu ? (
                          <td className="px-5 py-3 align-middle tabular-nums text-amber-700 text-xs font-medium">
                            {inv.pipAmount && inv.pipAmount > 0 ? money(inv.pipAmount) : '—'}
                          </td>
                        ) : null}
                        <td className="px-5 py-3 align-middle whitespace-nowrap">
                          <LosDigitalInvoiceAttachment
                            invoiceId={inv.invoiceId}
                            fileName={inv.digitalInvoiceFileName}
                          />
                        </td>
                        <td className="px-5 py-3 align-middle">
                          <span
                            className={`inline-block rounded-full border px-2 py-0.5 text-xs ${statusBadge(inv.status)}`}
                          >
                            {inv.friendlyStatus || inv.status || '—'}
                          </span>
                        </td>
                        <td className="px-3 py-3 align-middle">
                          <div className="flex w-44 flex-col items-stretch gap-1.5">
                            {inv.acceptable ? (
                              <button
                                type="button"
                                onClick={() => void onAccept(inv)}
                                disabled={busyId === inv.invoiceId}
                                className="w-full rounded-md border border-sky-600 px-3 py-1.5 text-xs font-medium text-sky-700 hover:bg-sky-50 disabled:opacity-50"
                              >
                                {busyId === inv.invoiceId ? '…' : 'Accept invoice'}
                              </button>
                            ) : inv.financeable ? (
                              <form onSubmit={(e) => onFinance(e, inv)} className="flex flex-col gap-1.5">
                                <input
                                  type="number"
                                  min={0}
                                  max={inv.maxFinanceableAmount ?? inv.availableAmount ?? undefined}
                                  step="0.01"
                                  value={financeAmounts[inv.invoiceId] ?? defaultFinanceAmount(inv)}
                                  onChange={(e) =>
                                    setFinanceAmounts((m) => ({
                                      ...m,
                                      [inv.invoiceId]: sanitizeNonNegativeNumberInput(e.target.value),
                                    }))
                                  }
                                  className="w-full rounded border border-slate-300 px-2 py-1 text-sm focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
                                />
                                <button
                                  type="submit"
                                  disabled={busyId === inv.invoiceId}
                                  className="bt-btn bt-btn-primary bt-btn-sm w-full disabled:opacity-50"
                                >
                                  {busyId === inv.invoiceId ? '…' : 'Request finance'}
                                </button>
                              </form>
                            ) : null}
                            {inv.earlyPayable ? (
                              <button
                                type="button"
                                onClick={() => setEarlyPayInvoice(inv)}
                                disabled={busyId === inv.invoiceId}
                                className="w-full rounded-md border border-emerald-600 px-3 py-1.5 text-xs font-medium text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
                              >
                                Request Early Pay
                              </button>
                            ) : null}
                            {inv.deletable ? (
                              <button
                                type="button"
                                onClick={() => void onDeleteInvoice(inv)}
                                disabled={busyId === inv.invoiceId}
                                className="w-full rounded-md border border-rose-600 px-3 py-1.5 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                              >
                                {busyId === inv.invoiceId ? '…' : 'Delete'}
                              </button>
                            ) : null}
                            {invoiceActions.length > 0 ? (
                              <BorrowerInvoiceActionsMenu
                                items={invoiceActions}
                                busy={addingToCart || busyId === inv.invoiceId}
                              />
                            ) : null}
                            {!inv.acceptable &&
                            !inv.financeable &&
                            !inv.earlyPayable &&
                            !inv.deletable &&
                            invoiceActions.length === 0 ? (
                              <span className="text-xs text-slate-400">—</span>
                            ) : null}
                          </div>
                        </td>
                      </tr>,
                      ]
                      if (linkedLoans.length > 0 && expandedLoanInvoiceIds.has(inv.invoiceId)) {
                        rows.push(
                          <tr key={`${inv.invoiceId}-loans`}>
                            <td colSpan={usePayu ? 11 : 9} className="px-5 py-4 bg-slate-50/40 align-middle">
                              <div className="space-y-4">
                                {linkedLoans.map((loan) =>
                                  usePayu ? (
                                    <div key={loan.loanId} className="space-y-2">
                                      {inv.pipAmount && inv.pipAmount > 0 ? (
                                        <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">
                                          <span className="uppercase tracking-wide">PRUS</span>
                                          <span className="tabular-nums">{money(inv.pipAmount)}</span>
                                        </div>
                                      ) : null}
                                      <BorrowerInvoiceLoanCard
                                        loan={loan}
                                        invoice={invoiceById.get(inv.invoiceId)}
                                        readOnly
                                      />
                                    </div>
                                  ) : (
                                    <BorrowerInvoiceLoanCard
                                      key={loan.loanId}
                                      loan={loan}
                                      invoice={invoiceById.get(inv.invoiceId)}
                                      busyId={busyId}
                                      repayAmount={repayAmounts[loan.loanId] ?? ''}
                                      onRepayAmountChange={(value) =>
                                        setRepayAmounts((m) => ({
                                          ...m,
                                          [loan.loanId]: sanitizeNonNegativeNumberInput(value),
                                        }))
                                      }
                                      onRepay={onRepay}
                                      readOnly={inv.enablePaymentForBorrower === false}
                                    />
                                  ),
                                )}
                              </div>
                            </td>
                          </tr>,
                        )
                      }
                      if (expandedUnfinancedRepayIds.has(inv.invoiceId) && shouldShowUnfinancedRepayments(inv)) {
                        rows.push(
                          <tr key={`${inv.invoiceId}-unfinanced-repayments`}>
                            <td colSpan={usePayu ? 11 : 9} className="px-5 py-4 bg-slate-50/40 align-middle">
                              <BorrowerInvoiceUnfinancedRepaymentPanel
                                invoiceId={inv.invoiceId}
                                invoiceNumber={inv.invoiceNumber}
                                embedded
                              />
                            </td>
                          </tr>,
                        )
                      }
                      return rows
                    })
                    )}
                  </tbody>
                </table>
                </div>
              </div>
          </section>
        </>
      ) : null}
      {earlyPayInvoice && (
        <LosEarlyPayRequestModal
          invoice={earlyPayInvoice}
          onClose={() => setEarlyPayInvoice(null)}
          onSuccess={() => void refresh()}
        />
      )}
    </div>
  )
}
