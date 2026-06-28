import type { ReactNode } from 'react'
import { badgeToneForStatus, btBadgeClass, type BadgeTone } from './btStatusUtils'

type Props = {
  children: ReactNode
  tone?: BadgeTone
  status?: string
  className?: string
}

export function BtBadge({ children, tone, status, className = '' }: Props) {
  const resolved = tone ?? badgeToneForStatus(status)
  return <span className={btBadgeClass(resolved, className)}>{children}</span>
}
