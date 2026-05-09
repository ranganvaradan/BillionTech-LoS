import { Link } from 'react-router-dom'
import type { ApplicationResponse } from '@/types/application'
import { loanProductLabel } from '@/catalog/loanProducts'
import { formatInstant, formatMoney } from '@/lib/format'

interface ApplicationTableProps {
  rows: ApplicationResponse[]
  emptyMessage?: string
}

export function ApplicationTable({ rows, emptyMessage = 'No applications found.' }: ApplicationTableProps) {
  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center text-sm text-slate-600">
        {emptyMessage}
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium uppercase text-slate-600">
          <tr>
            <th className="px-4 py-3">Application</th>
            <th className="px-4 py-3">Product</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Amount</th>
            <th className="px-4 py-3">Created</th>
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((a) => (
            <tr key={a.id} className="hover:bg-slate-50/80">
              <td className="px-4 py-3 font-medium text-slate-900">{a.applicationNumber}</td>
              <td className="px-4 py-3 text-slate-700">{loanProductLabel(a.loanProduct)}</td>
              <td className="px-4 py-3">
                <span className="inline-flex rounded border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">
                  {a.status}
                </span>
              </td>
              <td className="px-4 py-3 text-slate-700 tabular-nums">{formatMoney(a.requestedAmount)}</td>
              <td className="px-4 py-3 text-slate-600 tabular-nums">{formatInstant(a.createdAt)}</td>
              <td className="px-4 py-3 text-right">
                <Link
                  to={`/applications/${a.id}`}
                  className="text-sm font-medium text-slate-800 underline-offset-2 hover:underline"
                >
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
