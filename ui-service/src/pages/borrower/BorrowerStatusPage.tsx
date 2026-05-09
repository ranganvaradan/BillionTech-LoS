import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getBorrowerApplicationStatus } from '@/api/borrowerStatus'
import { ApiError } from '@/api/http'
import { friendlyStatusHeadline } from '@/lib/borrowerFriendly'
import { isUuid } from '@/lib/format'

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-slate-100 py-3 sm:flex-row sm:justify-between">
      <span className="text-xs font-medium uppercase text-slate-500">{label}</span>
      <span className="text-sm text-slate-900">{value}</span>
    </div>
  )
}

function BorrowerStatusContent({ applicationId }: { applicationId: string }) {
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState<string | null>(null)
  const [data, setData] = useState<Awaited<ReturnType<typeof getBorrowerApplicationStatus>> | null>(null)

  useEffect(() => {
    let cancelled = false
    void getBorrowerApplicationStatus(applicationId)
      .then((d) => {
        if (!cancelled) {
          setData(d)
          setErr(null)
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setData(null)
          setErr(e instanceof ApiError ? e.message : 'Could not load status.')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId])

  if (loading && !data) {
    return (
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Application status</h1>
        <p className="mt-4 text-sm text-slate-600">Loading…</p>
      </div>
    )
  }

  if (err && !data) {
    return (
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Application status</h1>
        <p className="mt-4 text-sm text-rose-700">{err}</p>
        <Link to="/borrower" className="mt-4 inline-block text-sm text-slate-800 underline">
          Start an application
        </Link>
      </div>
    )
  }

  if (!data) {
    return null
  }

  const headline = friendlyStatusHeadline(data.status)

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">{headline}</h1>
      <p className="mt-1 text-sm text-slate-600">
        Reference <span className="font-mono text-slate-800">{data.applicationNumber}</span>
      </p>

      <div className="mt-6 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <Row label="Your name" value={data.customerName} />
        <Row label="Product" value={data.product} />
        <Row label="Verification" value={data.kycStatus} />
        <Row label="Documents" value={data.documentStatus} />
        <Row label="Credit decision" value={data.sanctionStatus} />
        <Row label="Key facts (KFS)" value={data.kfsStatus} />
        <Row label="Agreement signature" value={data.eSignStatus} />
        <Row label="Disbursement" value={data.disbursementStatus} />
      </div>

      {data.requiredActions.length > 0 ? (
        <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <h2 className="text-sm font-semibold text-amber-950">What you can do next</h2>
          <ul className="mt-2 list-inside list-disc text-sm text-amber-950">
            {data.requiredActions.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <p className="mt-6 text-xs text-slate-500">For questions, use the number you gave us in your application.</p>
    </div>
  )
}

export function BorrowerStatusPage() {
  const { applicationId } = useParams<{ applicationId: string }>()
  if (!applicationId || !isUuid(applicationId)) {
    return (
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Application status</h1>
        <p className="mt-2 text-sm text-rose-700">This link does not look valid. Check the address and try again.</p>
        <Link to="/borrower" className="mt-4 inline-block text-sm text-slate-800 underline">
          Start an application
        </Link>
      </div>
    )
  }
  return <BorrowerStatusContent key={applicationId} applicationId={applicationId} />
}
