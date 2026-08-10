import { useId, useState, type ReactNode } from 'react'

type Props = {
  title: string
  subtitle?: ReactNode
  badge?: ReactNode
  /** Controlled open state when provided. */
  open?: boolean
  defaultOpen?: boolean
  onOpenChange?: (open: boolean) => void
  children: ReactNode
  className?: string
  toneClassName?: string
}

/**
 * Theme-aligned collapsible card used on underwriting and similar review tabs.
 */
export function CollapsibleSection({
  title,
  subtitle,
  badge,
  open: openControlled,
  defaultOpen = false,
  onOpenChange,
  children,
  className = '',
  toneClassName = 'bt-section-card bt-section-card--default',
}: Props) {
  const reactId = useId()
  const panelId = `collapsible-panel-${reactId}`
  const isControlled = openControlled !== undefined
  const [internalOpen, setInternalOpen] = useState(defaultOpen)
  const open = isControlled ? Boolean(openControlled) : internalOpen

  function toggle() {
    const next = !open
    if (!isControlled) setInternalOpen(next)
    onOpenChange?.(next)
  }

  return (
    <div className={`${toneClassName} overflow-hidden ${className}`.trim()}>
      <button
        type="button"
        className="flex w-full items-start justify-between gap-3 px-4 py-3 text-left hover:bg-slate-50/80"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <div className="min-w-0 flex-1">
          <div className="mb-1.5 h-1 w-10 rounded-full bg-orange-500" />
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="bt-card-title text-sm text-slate-900">{title}</h3>
            {badge}
          </div>
          {subtitle ? <div className="mt-1 text-xs leading-5 text-slate-500">{subtitle}</div> : null}
        </div>
        <span
          className={`mt-1 shrink-0 text-slate-400 transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
          aria-hidden
        >
          ›
        </span>
      </button>
      <div
        id={panelId}
        className={`grid transition-[grid-template-rows] duration-200 ease-out ${
          open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'
        }`}
      >
        <div className="min-h-0 overflow-hidden">
          <div className="border-t border-slate-100 px-4 py-3">{children}</div>
        </div>
      </div>
    </div>
  )
}
