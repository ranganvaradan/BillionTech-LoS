import { Link } from 'react-router-dom'
import type { ApplicationResponse } from '@/types/application'
import { loanProductLabel } from '@/catalog/loanProducts'
import { formatInstant, formatMoney } from '@/lib/format'
import { BtBadge } from '@/components/ui/BtBadge'
import { BtCard } from '@/components/ui/BtCard'

interface ApplicationTableProps {
  rows: ApplicationResponse[]
  emptyMessage?: string
}

export function ApplicationTable({ rows, emptyMessage = 'No applications found.' }: ApplicationTableProps) {
  if (rows.length === 0) {
    return (
      <BtCard className="bt-empty-state p-8">
        {emptyMessage}
      </BtCard>
    )
  }

  return (
    <BtCard className="overflow-x-auto">
      <table className="bt-table">
        <thead>
          <tr>
            <th>Application</th>
            <th>Intake</th>
            <th>Product</th>
            <th>Status</th>
            <th>Amount</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((a) => (
            <tr key={a.id} className="clickable">
              <td className="font-medium text-[var(--bt-gray-900)]">{a.applicationNumber}</td>
              <td>
                {a.intakeSegment === 'ANCHOR' ? (
                  <BtBadge tone="blue">Anchor</BtBadge>
                ) : (
                  <span className="text-[var(--bt-gray-500)]">Borrower</span>
                )}
              </td>
              <td>{loanProductLabel(a.loanProduct)}</td>
              <td>
                <BtBadge status={a.status}>{a.status.replaceAll('_', ' ')}</BtBadge>
              </td>
              <td className="tabular-nums">{formatMoney(a.requestedAmount)}</td>
              <td className="text-[var(--bt-gray-500)] tabular-nums">{formatInstant(a.createdAt)}</td>
              <td className="text-right">
                <Link to={`/applications/${a.id}`}>View</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </BtCard>
  )
}
