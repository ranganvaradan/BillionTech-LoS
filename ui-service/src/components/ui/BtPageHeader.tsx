import type { ReactNode } from 'react'

type Props = {
  title: string
  description?: string
  actions?: ReactNode
}

export function BtPageHeader({ title, description, actions }: Props) {
  return (
    <div className="bt-page-header">
      <div>
        <h1 className="bt-page-title">{title}</h1>
        {description ? <p className="mt-1 text-[13px] text-[var(--bt-gray-500)]">{description}</p> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  )
}
