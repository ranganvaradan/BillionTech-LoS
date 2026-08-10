import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { BtCard, BtCardHeader } from '@/components/ui/BtCard'
import { BtStatCard, statAccentAt } from '@/components/ui/BtStatCard'
import {
  downloadReportExport,
  fetchDashboardAnalytics,
  fetchMisReport,
  fetchOperationsReport,
  fetchRegulatoryReport,
  type DashboardAnalytics,
  type MisReport,
  type OperationsReport,
  type RegulatoryReport,
} from '@/api/reports'
import { ApiError } from '@/api/http'

type TabKey = 'portfolio' | 'operations' | 'mis' | 'regulatory' | 'exports'

const TABS: { key: TabKey; label: string; desc: string }[] = [
  { key: 'portfolio', label: 'Portfolio funnel', desc: 'Application counts, rates, and status mix' },
  { key: 'operations', label: 'Operations queues', desc: 'Handoff, KYC, underwriting, sanction & eSign' },
  { key: 'mis', label: 'MIS', desc: 'Management information summary' },
  { key: 'regulatory', label: 'Regulatory', desc: 'Compliance-oriented aggregates' },
  { key: 'exports', label: 'Exports', desc: 'Download CSV, PDF, or Excel for a date range' },
]

const EXPORT_TYPES: { key: string; label: string; desc: string }[] = [
  { key: 'MIS', label: 'MIS report', desc: 'Full application register with statuses and amounts' },
  { key: 'PORTFOLIO', label: 'Portfolio funnel', desc: 'Counts, approval and conversion rates' },
  { key: 'DISBURSEMENT', label: 'Disbursement', desc: 'Disbursed loans and amounts' },
  { key: 'REGULATORY', label: 'Regulatory', desc: 'RBI digital-lending & KYC compliance' },
  { key: 'DSA_PERFORMANCE', label: 'DSA performance', desc: 'Sourcing partner productivity' },
]

const INR = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })

function fmtNum(n: number | undefined | null): string {
  if (n == null || Number.isNaN(Number(n))) return '0'
  return new Intl.NumberFormat('en-IN').format(Number(n))
}

function fmtCurrency(n: number | undefined | null): string {
  return `₹${INR.format(Number(n ?? 0))}`
}

function fmtPct(n: number | undefined | null): string {
  return `${Number(n ?? 0).toFixed(1)}%`
}

const ACRONYMS: Record<string, string> = {
  kyc: 'KYC',
  rm: 'RM',
  co: 'CO',
  kfs: 'KFS',
  esign: 'eSign',
  cam: 'CAM',
  npa: 'NPA',
  dpd: 'DPD',
  mis: 'MIS',
}

function labelize(raw: string): string {
  return raw
    .replace(/_/g, ' ')
    .toLowerCase()
    .split(' ')
    .filter(Boolean)
    .map((w) => ACRONYMS[w] ?? w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

/** Label on the left, value on the right — always on the same line. */
function MetricList({
  rows,
  empty = 'No data for the selected filters.',
}: {
  rows: { label: string; value: ReactNode }[]
  empty?: string
}) {
  if (!rows.length) {
    return <p className="px-[18px] py-3 text-sm text-[var(--bt-gray-500)]">{empty}</p>
  }
  return (
    <div className="divide-y divide-[var(--bt-gray-100)]">
      {rows.map((r, i) => (
        <div key={i} className="flex items-center justify-between gap-4 px-[18px] py-2.5">
          <span className="text-[13px] text-[var(--bt-gray-600)]">{r.label}</span>
          <span className="font-serif text-[15px] font-semibold tabular-nums text-[var(--bt-gray-900)]">
            {r.value}
          </span>
        </div>
      ))}
    </div>
  )
}

function StatusCountCard({
  title,
  rows,
}: {
  title: string
  rows: { status: string; count: number }[]
}) {
  return (
    <BtCard>
      <BtCardHeader title={title} />
      <MetricList rows={rows.map((r) => ({ label: labelize(r.status), value: fmtNum(r.count) }))} />
    </BtCard>
  )
}

export function ReportsPage() {
  const { user } = useAuth()
  const allowed = user ? canAccessAdminConfigNav(user.role) : false

  const [tab, setTab] = useState<TabKey>('portfolio')
  const [period, setPeriod] = useState('30D')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [dashboard, setDashboard] = useState<DashboardAnalytics | null>(null)
  const [operations, setOperations] = useState<OperationsReport | null>(null)
  const [mis, setMis] = useState<MisReport | null>(null)
  const [regulatory, setRegulatory] = useState<RegulatoryReport | null>(null)
  const [exportBusy, setExportBusy] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (tab === 'portfolio') {
        setDashboard(await fetchDashboardAnalytics())
      } else if (tab === 'operations') {
        setOperations(await fetchOperationsReport(period))
      } else if (tab === 'mis') {
        setMis(await fetchMisReport(period))
      } else if (tab === 'regulatory') {
        setRegulatory(await fetchRegulatoryReport(period))
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load report')
    } finally {
      setLoading(false)
    }
  }, [tab, period])

  useEffect(() => {
    if (!allowed) return
    if (tab === 'exports') {
      setLoading(false)
      return
    }
    void load()
  }, [allowed, tab, load])

  if (!allowed) {
    return <Navigate to="/dashboard" replace />
  }

  async function runExport(reportType: string, format: 'csv' | 'pdf' | 'xlsx') {
    setExportBusy(`${reportType}-${format}`)
    setError(null)
    try {
      await downloadReportExport(reportType, format, startDate || undefined, endDate || undefined)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Export failed')
    } finally {
      setExportBusy(null)
    }
  }

  const current = TABS.find((t) => t.key === tab)!

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Portfolio, operations, MIS, and regulatory views for credit leadership."
      />

      <div className="bt-toolbar-card mb-4 flex flex-wrap items-end justify-between gap-3">
        <div className="bt-tabs mb-0 border-b-0">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              className={t.key === tab ? 'bt-tab active' : 'bt-tab'}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-end gap-3">
          {tab !== 'portfolio' && tab !== 'exports' ? (
            <div>
              <label className="bt-label" htmlFor="rpt-period">
                Period
              </label>
              <select
                id="rpt-period"
                className="bt-input"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
              >
                <option value="7D">Last 7 days</option>
                <option value="30D">Last 30 days</option>
                <option value="90D">Last 90 days</option>
                <option value="YTD">Year to date</option>
                <option value="ALL">All time</option>
              </select>
            </div>
          ) : null}
          <button
            type="button"
            className="bt-btn bt-btn--secondary"
            disabled={loading || tab === 'exports'}
            onClick={() => void load()}
          >
            Refresh
          </button>
        </div>
      </div>

      <p className="mb-4 text-sm text-[var(--bt-gray-600)]">{current.desc}</p>
      {error ? <ErrorState message={error} /> : null}
      {loading && tab !== 'exports' ? <LoadingState label="Loading report…" /> : null}

      {/* ── Portfolio funnel ─────────────────────────────────────── */}
      {!loading && tab === 'portfolio' && dashboard ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <BtStatCard title="Total applications" value={fmtNum(dashboard.totalApplications)} accent={statAccentAt(0)} />
            <BtStatCard title="Active pipeline" value={fmtNum(dashboard.activePipeline)} accent={statAccentAt(1)} />
            <BtStatCard title="Disbursed" value={fmtNum(dashboard.disbursedCount)} accent={statAccentAt(2)} />
            <BtStatCard title="Approval rate" value={fmtPct(dashboard.approvalRate)} accent={statAccentAt(3)} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <BtStatCard title="Approved" value={fmtNum(dashboard.approvedCount)} accent={statAccentAt(1)} />
            <BtStatCard title="Rejected" value={fmtNum(dashboard.rejectedCount)} accent={statAccentAt(3)} />
            <BtStatCard title="Disbursed amount" value={fmtCurrency(dashboard.totalDisbursedAmount)} accent={statAccentAt(4)} />
            <BtStatCard title="Requested amount" value={fmtCurrency(dashboard.totalRequestedAmount)} accent={statAccentAt(5)} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <BtCard>
              <BtCardHeader title="Status distribution" />
              <MetricList
                rows={(dashboard.statusDistribution ?? []).map((s) => ({
                  label: labelize(s.status),
                  value: (
                    <span className="flex items-baseline gap-2">
                      <span>{fmtNum(s.count)}</span>
                      <span className="text-xs font-normal text-[var(--bt-gray-500)]">{fmtPct(s.percentage)}</span>
                    </span>
                  ),
                }))}
              />
            </BtCard>
            <BtCard>
              <BtCardHeader title="Product mix" />
              <MetricList
                rows={(dashboard.productDistribution ?? []).map((p) => ({
                  label: p.product,
                  value: (
                    <span className="flex items-baseline gap-2">
                      <span>{fmtNum(p.count)}</span>
                      {p.totalAmount != null ? (
                        <span className="text-xs font-normal text-[var(--bt-gray-500)]">{fmtCurrency(p.totalAmount)}</span>
                      ) : null}
                    </span>
                  ),
                }))}
              />
            </BtCard>
          </div>
        </div>
      ) : null}

      {/* ── Operations queues ────────────────────────────────────── */}
      {!loading && tab === 'operations' && operations ? (
        <div className="space-y-6">
          <div className="grid gap-4 lg:grid-cols-2">
            <StatusCountCard title="RM ↔ CO handoff queue" rows={operations.handoffQueue} />
            <BtCard>
              <BtCardHeader title="Intake segment" />
              <MetricList
                rows={operations.intakeSegmentCounts.map((r) => ({ label: labelize(r.segment), value: fmtNum(r.count) }))}
              />
            </BtCard>
            <StatusCountCard title="KYC & underwriting" rows={operations.kycAndUnderwriting} />
            <StatusCountCard title="Sanction & eSign pipeline" rows={operations.sanctionAndEsign} />
          </div>
          <BtCard>
            <BtCardHeader title="Product breakdown" />
            <MetricList
              rows={operations.productCounts.map((r) => ({ label: r.product, value: fmtNum(r.count) }))}
            />
          </BtCard>
        </div>
      ) : null}

      {/* ── MIS ──────────────────────────────────────────────────── */}
      {!loading && tab === 'mis' && mis ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <BtStatCard title="Total applications" value={fmtNum(mis.summary.totalApplications)} accent={statAccentAt(0)} />
            <BtStatCard title="Approved" value={fmtNum(mis.summary.approved)} accent={statAccentAt(1)} />
            <BtStatCard title="Rejected" value={fmtNum(mis.summary.rejected)} accent={statAccentAt(3)} />
            <BtStatCard title="Pending" value={fmtNum(mis.summary.pending)} accent={statAccentAt(2)} />
            <BtStatCard title="Disbursed" value={fmtNum(mis.summary.disbursed)} accent={statAccentAt(4)} />
            <BtStatCard title="Approval rate" value={fmtPct(mis.summary.approvalRate)} accent={statAccentAt(5)} />
            <BtStatCard title="Disbursed amount" value={fmtCurrency(mis.summary.totalDisbursedAmount)} accent={statAccentAt(1)} />
            <BtStatCard title="Requested amount" value={fmtCurrency(mis.summary.totalRequestedAmount)} accent={statAccentAt(0)} />
          </div>
          <BtCard>
            <BtCardHeader
              title="Application register"
              actions={<span className="text-xs text-[var(--bt-gray-500)]">{fmtNum(mis.rows.length)} rows · latest first</span>}
            />
            {mis.rows.length ? (
              <div className="overflow-x-auto">
                <table className="bt-table w-full text-sm">
                  <thead>
                    <tr>
                      <th className="text-left">Application #</th>
                      <th className="text-left">Borrower</th>
                      <th className="text-left">Product</th>
                      <th className="text-right">Requested</th>
                      <th className="text-left">Status</th>
                      <th className="text-left">KYC</th>
                      <th className="text-right">Days</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mis.rows.map((r, i) => (
                      <tr key={r.applicationNumber ?? i}>
                        <td className="text-left font-medium">{r.applicationNumber || '—'}</td>
                        <td className="text-left">{r.borrowerName || '—'}</td>
                        <td className="text-left">{r.loanProduct || '—'}</td>
                        <td className="text-right tabular-nums">{fmtCurrency(r.requestedAmount)}</td>
                        <td className="text-left">{labelize(r.status)}</td>
                        <td className="text-left">{labelize(r.kycStatus)}</td>
                        <td className="text-right tabular-nums">{fmtNum(r.processingDays)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="px-[18px] py-3 text-sm text-[var(--bt-gray-500)]">No applications for the selected period.</p>
            )}
          </BtCard>
        </div>
      ) : null}

      {/* ── Regulatory ───────────────────────────────────────────── */}
      {!loading && tab === 'regulatory' && regulatory ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <BtStatCard
              title="Digital compliance rate"
              value={fmtPct(regulatory.digitalLending.digitalComplianceRate)}
              accent={statAccentAt(1)}
            />
            <BtStatCard
              title="KYC completion rate"
              value={fmtPct(regulatory.kyc.kycCompletionRate)}
              accent={statAccentAt(0)}
            />
            <BtStatCard
              title="NPA percentage"
              value={fmtPct(regulatory.dpdAnalysis.npaPercentage)}
              accent={statAccentAt(3)}
            />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <BtCard>
              <BtCardHeader title="Digital lending (RBI)" />
              <MetricList
                rows={[
                  { label: 'Total digital loans', value: fmtNum(regulatory.digitalLending.totalDigitalLoans) },
                  { label: 'KFS issued', value: fmtNum(regulatory.digitalLending.kfsIssued) },
                  { label: 'KFS signed', value: fmtNum(regulatory.digitalLending.kfsSigned) },
                  { label: 'Cooling-off complied', value: fmtNum(regulatory.digitalLending.coolingOffComplied) },
                  { label: 'eSign completed', value: fmtNum(regulatory.digitalLending.esignCompleted) },
                  { label: 'Compliance rate', value: fmtPct(regulatory.digitalLending.digitalComplianceRate) },
                ]}
              />
              <p className="border-t border-[var(--bt-gray-100)] px-[18px] py-2.5 text-xs text-[var(--bt-gray-500)]">
                Circular: {regulatory.digitalLending.rbiCircularRef}
              </p>
            </BtCard>
            <BtCard>
              <BtCardHeader title="KYC compliance" />
              <MetricList
                rows={[
                  { label: 'KYC initiated', value: fmtNum(regulatory.kyc.totalKycInitiated) },
                  { label: 'KYC completed', value: fmtNum(regulatory.kyc.kycCompleted) },
                  { label: 'KYC failed', value: fmtNum(regulatory.kyc.kycFailed) },
                  { label: 'Aadhaar verified', value: fmtNum(regulatory.kyc.aadhaarVerified) },
                  { label: 'PAN verified', value: fmtNum(regulatory.kyc.panVerified) },
                  { label: 'CKYC verified', value: fmtNum(regulatory.kyc.cKycVerified) },
                  { label: 'Face match completed', value: fmtNum(regulatory.kyc.faceMatchCompleted) },
                  { label: 'Completion rate', value: fmtPct(regulatory.kyc.kycCompletionRate) },
                ]}
              />
            </BtCard>
          </div>
          <BtCard>
            <BtCardHeader title="DPD & NPA analysis" />
            <MetricList
              rows={[
                { label: 'Total active loans', value: fmtNum(regulatory.dpdAnalysis.totalActiveLoans) },
                { label: 'DPD 0 (current)', value: fmtNum(regulatory.dpdAnalysis.dpd0) },
                { label: 'DPD 1–30', value: fmtNum(regulatory.dpdAnalysis.dpd1to30) },
                { label: 'DPD 31–60', value: fmtNum(regulatory.dpdAnalysis.dpd31to60) },
                { label: 'DPD 61–90', value: fmtNum(regulatory.dpdAnalysis.dpd61to90) },
                { label: 'DPD 90+', value: fmtNum(regulatory.dpdAnalysis.dpd90plus) },
                { label: 'NPA count', value: fmtNum(regulatory.dpdAnalysis.npaCount) },
                { label: 'Total NPA amount', value: fmtCurrency(regulatory.dpdAnalysis.totalNpaAmount) },
                { label: 'NPA percentage', value: fmtPct(regulatory.dpdAnalysis.npaPercentage) },
              ]}
            />
          </BtCard>
        </div>
      ) : null}

      {/* ── Exports ──────────────────────────────────────────────── */}
      {tab === 'exports' ? (
        <div className="space-y-4">
          <BtCard>
            <BtCardHeader title="Date range" />
            <div className="flex flex-wrap items-end gap-4 p-[18px]">
              <div>
                <label className="bt-label" htmlFor="export-start">
                  Start date
                </label>
                <input
                  id="export-start"
                  type="date"
                  className="bt-input"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              </div>
              <div>
                <label className="bt-label" htmlFor="export-end">
                  End date
                </label>
                <input
                  id="export-end"
                  type="date"
                  className="bt-input"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                />
              </div>
              <p className="text-xs text-[var(--bt-gray-500)]">
                Leave blank to export all-time data.
              </p>
            </div>
          </BtCard>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {EXPORT_TYPES.map((rt) => (
              <BtCard key={rt.key} className="flex flex-col">
                <div className="border-b border-[var(--bt-gray-100)] p-[18px]">
                  <div className="text-[13px] font-semibold text-[var(--bt-gray-900)]">{rt.label}</div>
                  <p className="mt-1 text-xs text-[var(--bt-gray-500)]">{rt.desc}</p>
                </div>
                <div className="flex flex-wrap gap-2 p-[18px]">
                  {(['csv', 'xlsx', 'pdf'] as const).map((fmt) => (
                    <button
                      key={fmt}
                      type="button"
                      className="bt-btn bt-btn--secondary bt-btn-sm"
                      disabled={exportBusy === `${rt.key}-${fmt}`}
                      onClick={() => void runExport(rt.key, fmt)}
                    >
                      {exportBusy === `${rt.key}-${fmt}` ? '…' : fmt.toUpperCase()}
                    </button>
                  ))}
                </div>
              </BtCard>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}
