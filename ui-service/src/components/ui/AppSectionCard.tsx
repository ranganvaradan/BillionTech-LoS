import type { HTMLAttributes, ReactNode } from 'react'

export type AppSectionCardTone =
  | 'default'
  | 'hero'
  | 'info'
  | 'success'
  | 'warning'
  | 'danger'
  | 'violet'
  | 'override'

type Props = HTMLAttributes<HTMLElement> & {
  title?: ReactNode
  subtitle?: ReactNode
  badge?: ReactNode
  actions?: ReactNode
  tone?: AppSectionCardTone
  children?: ReactNode
  bodyClassName?: string
  /** When true, children render directly without body wrapper padding (for pre-padded legacy markup). */
  unstyledBody?: boolean
}

export function AppSectionCard({
  title,
  subtitle,
  badge,
  actions,
  tone = 'default',
  children,
  className = '',
  bodyClassName = '',
  unstyledBody = false,
  ...rest
}: Props) {
  const hasHeader = Boolean(title || subtitle || badge || actions)
  const hasBody = children != null && children !== false
  return (
    <section
      className={['bt-section-card', `bt-section-card--${tone}`, className].filter(Boolean).join(' ')}
      {...rest}
    >
      {hasHeader ? (
        <header className="bt-section-card__header">
          <div className="bt-section-card__header-text">
            {badge ? <div className="bt-section-card__badge-row">{badge}</div> : null}
            {title ? <h3 className="bt-section-card__title">{title}</h3> : null}
            {subtitle ? <p className="bt-section-card__subtitle">{subtitle}</p> : null}
          </div>
          {actions ? <div className="bt-section-card__actions">{actions}</div> : null}
        </header>
      ) : null}
      {hasBody ? (
        unstyledBody ? (
          children
        ) : (
          <div className={['bt-section-card__body', bodyClassName].filter(Boolean).join(' ')}>{children}</div>
        )
      ) : null}
    </section>
  )
}
