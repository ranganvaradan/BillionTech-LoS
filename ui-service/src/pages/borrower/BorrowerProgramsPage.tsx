import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { getBorrowerPrograms, type BorrowerProgramEnrollment } from '@/api/borrowerPrograms'
import { getBorrowerDashboard } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { ProgramConfigDetailsPanel } from '@/components/borrower/ProgramConfigDetailsPanel'
import { BtBadge } from '@/components/ui/BtBadge'
import { BtCard } from '@/components/ui/BtCard'
import { BtPageHeader } from '@/components/ui/BtPageHeader'
import {
  buildBorrowerTermsRowsFromEnrollment,
  buildProgramConfigurationRowsFromEnrollment,
  buildSubProgramConfigurationRowsFromEnrollment,
} from '@/utils/programDetailsDisplay'

function formatCurrency(amount: number | null | undefined): string {
  if (amount == null || Number.isNaN(Number(amount))) return '—'
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(Number(amount))
}

function formatPct(n: number | null | undefined): string {
  if (n == null || Number.isNaN(Number(n))) return '—'
  return `${Number(n)}%`
}

function productLabel(productType: string | null | undefined): string {
  if (productType === 'PAY_DAY_LOAN') return 'Pay Day Loan'
  if (productType === 'INVOICE_DISCOUNTING') return 'Invoice Discounting'
  return productType ?? '—'
}

function flowLabel(flowType: string | null | undefined): string {
  switch (flowType) {
    case 'PURCHASE_BILL_DISCOUNTING':
      return 'Purchase Bill Discounting'
    case 'SALES_BILL_DISCOUNTING':
      return 'Sales Bill Discounting'
    case 'PAY_LOAN':
    case 'PAY_DAY_LOAN':
      return 'Pay Loan'
    default:
      return flowType ?? '—'
  }
}

function EnrollmentCard({ row }: { row: BorrowerProgramEnrollment }) {
  const hasProgram = Boolean(row.programId)
  const membershipAvailable = row.borrowerLimit != null

  return (
    <BtCard className="overflow-hidden p-0">
      <div className="border-b border-[var(--bt-gray-100)] px-5 py-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-base font-semibold text-[var(--bt-gray-800)]">
            {row.programName ?? 'Program'}
          </h2>
          {hasProgram && row.productType ? (
            <BtBadge tone={row.productType === 'PAY_DAY_LOAN' ? 'blue' : 'gray'}>
              {productLabel(row.productType)}
            </BtBadge>
          ) : null}
          {hasProgram && row.programStatus ? <BtBadge status={row.programStatus}>{row.programStatus}</BtBadge> : null}
        </div>
        {row.programCode ? (
          <p className="mt-0.5 font-mono text-xs text-[var(--bt-gray-400)]">{row.programCode}</p>
        ) : null}
      </div>

      {hasProgram ? (
        <div className="border-b border-[var(--bt-gray-100)] bg-[var(--bt-gray-50)] px-5 py-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">
            Program details
          </h3>
          <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3 lg:grid-cols-6">
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Program limit</div>
              <div className="font-medium tabular-nums">{formatCurrency(row.programLimit)}</div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Utilized</div>
              <div className="font-medium tabular-nums text-[var(--bt-amber)]">
                {formatCurrency(row.programUtilizedLimit ?? 0)}
              </div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Available</div>
              <div className="font-medium tabular-nums text-[var(--bt-green)]">
                {formatCurrency(
                  row.programAvailableLimit ??
                    Math.max(0, (row.programLimit ?? 0) - (row.programUtilizedLimit ?? 0)),
                )}
              </div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Interest rate</div>
              <div className="font-medium">{formatPct(row.defaultInterestRate)}</div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Margin</div>
              <div className="font-medium">{formatPct(row.programMarginPercent)}</div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Max tenure</div>
              <div className="font-medium">
                {row.programMaxTenureDays != null ? `${row.programMaxTenureDays} days` : '—'}
              </div>
            </div>
          </div>
          <ProgramConfigDetailsPanel
            className="mt-3"
            rows={buildProgramConfigurationRowsFromEnrollment(row)}
            label="Show more program details"
          />
        </div>
      ) : null}

      <div className="border-b border-[var(--bt-gray-100)] px-5 py-4">
        <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">
          Sub-program
        </h3>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium text-[var(--bt-gray-800)]">{row.subProgramName}</span>
          <span className="font-mono text-xs text-[var(--bt-gray-400)]">{row.subProgramCode}</span>
          <BtBadge status={row.subProgramStatus}>{row.subProgramStatus}</BtBadge>
        </div>
        <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
          <div>
            <div className="text-xs text-[var(--bt-gray-500)]">Flow type</div>
            <div className="font-medium">{flowLabel(row.flowType)}</div>
          </div>
          <div>
            <div className="text-xs text-[var(--bt-gray-500)]">Interest rate</div>
            <div className="font-medium">{formatPct(row.subProgramInterestRate)}</div>
          </div>
          <div>
            <div className="text-xs text-[var(--bt-gray-500)]">Margin</div>
            <div className="font-medium">{formatPct(row.subProgramMarginPercent)}</div>
          </div>
          <div>
            <div className="text-xs text-[var(--bt-gray-500)]">Max tenure</div>
            <div className="font-medium">
              {row.subProgramMaxTenureDays != null ? `${row.subProgramMaxTenureDays} days` : '—'}
            </div>
          </div>
        </div>
        <ProgramConfigDetailsPanel
          className="mt-3"
          rows={buildSubProgramConfigurationRowsFromEnrollment(row)}
          label="Show more sub-program details"
        />
      </div>

      <div className="px-5 py-4">
        <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">
          Your limit
        </h3>
        {membershipAvailable ? (
          <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Sanctioned limit</div>
              <div className="font-medium tabular-nums">{formatCurrency(row.borrowerLimit)}</div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Utilized</div>
              <div className="font-medium tabular-nums text-[var(--bt-amber)]">
                {formatCurrency(row.borrowerUtilizedLimit)}
              </div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Available</div>
              <div className="font-medium tabular-nums text-[var(--bt-green)]">
                {formatCurrency(
                  (row.borrowerAvailableLimit ?? 0) > 0
                    ? row.borrowerAvailableLimit
                    : Math.max(0, (row.borrowerLimit ?? 0) - (row.borrowerUtilizedLimit ?? 0)),
                )}
              </div>
            </div>
            <div>
              <div className="text-xs text-[var(--bt-gray-500)]">Status</div>
              <div className="mt-0.5">
                <BtBadge status={row.membershipStatus}>{row.membershipStatus}</BtBadge>
              </div>
            </div>
          </div>
        ) : (
          <p className="text-sm text-[var(--bt-gray-400)]">Limit details unavailable.</p>
        )}
        <ProgramConfigDetailsPanel
          className="mt-3"
          rows={buildBorrowerTermsRowsFromEnrollment(row)}
          label="Show your pricing terms"
          hideLabel="Hide your pricing terms"
        />
      </div>
    </BtCard>
  )
}

export function BorrowerProgramsPage() {
  const [linked, setLinked] = useState<boolean | null>(null)
  const [enrollments, setEnrollments] = useState<BorrowerProgramEnrollment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      setError('')
      try {
        const dash = await getBorrowerDashboard()
        if (cancelled) return
        if (!dash.invoiceDiscountingLinked) {
          setLinked(false)
          setEnrollments([])
          setLoading(false)
          return
        }
        setLinked(true)
        const res = await getBorrowerPrograms()
        if (cancelled) return
        if (!res.linked) {
          setLinked(false)
          setInfo(res.message ?? '')
          setEnrollments([])
          return
        }
        setInfo(res.message ?? '')
        setEnrollments(res.enrollments ?? [])
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : 'Could not load your program details.')
          setEnrollments([])
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  if (linked === false && !loading) {
    return <Navigate to="/borrower/dashboard" replace />
  }

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="animate-pulse text-sm text-[var(--bt-gray-400)]">Loading programs…</div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <BtPageHeader title="Programs" description="Programs and limits you are linked to" />
        <p className="bt-alert bt-alert-error mt-4 text-sm">{error}</p>
      </div>
    )
  }

  return (
    <div>
      <BtPageHeader
        title="Programs"
        description="Program and sub-program details for your linked memberships"
      />

      {info && enrollments.length === 0 ? (
        <p className="bt-alert bt-alert-warning mt-4 text-sm">{info}</p>
      ) : null}

      {enrollments.length === 0 ? (
        <BtCard className="p-10 text-center">
          <p className="text-sm text-[var(--bt-gray-500)]">You are not linked to any programs yet.</p>
          <p className="mt-1 text-xs text-[var(--bt-gray-400)]">
            Contact your employer or anchor to get enrolled.
          </p>
        </BtCard>
      ) : (
        <div className="space-y-6">
          {enrollments.map((row) => (
            <EnrollmentCard key={row.subProgramId} row={row} />
          ))}
        </div>
      )}
    </div>
  )
}
