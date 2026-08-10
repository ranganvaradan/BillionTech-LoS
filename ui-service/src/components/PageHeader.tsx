import type { ReactNode } from 'react'

type Props = {
  title: string
  description?: ReactNode
  actions?: ReactNode
}

export function PageHeader({ title, description, actions }: Props) {
  return (
    <div className="bt-page-header">
      <div>
        <h1 className="bt-page-title">{title}</h1>
        {description ? <div className="mt-1 text-[13px] text-[var(--bt-gray-500)]">{description}</div> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">{actions}</div> : null}
    </div>
  )
}
