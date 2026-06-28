import type { ReactNode } from 'react'

export function MasterDetailLayout({ children }: { children: ReactNode }) {
  return <div className="bt-master-detail">{children}</div>
}

type ListPanelProps = {
  title: string
  action?: ReactNode
  children: ReactNode
  empty?: ReactNode
  count?: number
  search?: string
  onSearchChange?: (value: string) => void
  searchPlaceholder?: string
}

export function MasterListPanel({
  title,
  action,
  children,
  empty,
  count,
  search,
  onSearchChange,
  searchPlaceholder = 'Search…',
}: ListPanelProps) {
  return (
    <div className="bt-master-list-panel">
      <div className="bt-card bt-master-list-card">
        <div className="bt-card-header">
          <div className="bt-master-list-heading">
            <div className="bt-card-title">{title}</div>
            {count !== undefined ? <span className="bt-master-list-count">{count}</span> : null}
          </div>
          {action}
        </div>
        {onSearchChange ? (
          <div className="bt-master-list-search">
            <div className="bt-search">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2" />
                <path d="M20 20L16.5 16.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <input
                type="search"
                value={search ?? ''}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder={searchPlaceholder}
              />
            </div>
          </div>
        ) : null}
        {empty ?? <div className="bt-master-list">{children}</div>}
      </div>
    </div>
  )
}

function initialsFromLabel(label: string): string {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
    .slice(0, 2)
}

export function ListItemAvatar({ label }: { label: string }) {
  return <span className="bt-master-list-item-avatar">{initialsFromLabel(label)}</span>
}

type ListItemProps = {
  active?: boolean
  onClick: () => void
  title: ReactNode
  subtitle?: ReactNode
  meta?: ReactNode
  tags?: ReactNode
  avatar?: string
  leading?: ReactNode
}

export function MasterListItem({
  active,
  onClick,
  title,
  subtitle,
  meta,
  tags,
  avatar,
  leading,
}: ListItemProps) {
  return (
    <button type="button" onClick={onClick} className={`bt-master-list-item${active ? ' active' : ''}`}>
      <div className="bt-master-list-item-body">
        {leading ?? (avatar ? <ListItemAvatar label={avatar} /> : null)}
        <div className="bt-master-list-item-content">
          <div className="bt-master-list-item-row">
            <span className="bt-master-list-item-title">{title}</span>
            {meta ? <span className="bt-master-list-item-meta">{meta}</span> : null}
          </div>
          {subtitle ? <span className="bt-master-list-item-subtitle">{subtitle}</span> : null}
          {tags ? <div className="bt-master-list-item-tags">{tags}</div> : null}
        </div>
        <span className="bt-master-list-item-chevron" aria-hidden="true">
          ›
        </span>
      </div>
    </button>
  )
}

type DetailPanelProps = {
  title?: string
  description?: ReactNode
  badge?: ReactNode
  children: ReactNode
  footer?: ReactNode
}

export function DetailPanel({ title, description, badge, children, footer }: DetailPanelProps) {
  return (
    <div className="bt-detail-panel">
      <div className="bt-card bt-detail-card">
        {title || description || badge ? (
          <div className="bt-detail-header">
            <div className="bt-detail-header-text">
              {title ? <h2 className="bt-detail-title">{title}</h2> : null}
              {description ? <p className="bt-detail-description">{description}</p> : null}
            </div>
            {badge ? <div className="bt-detail-header-badge">{badge}</div> : null}
          </div>
        ) : null}
        <div className="bt-detail-body">{children}</div>
        {footer ? <div className="bt-detail-footer">{footer}</div> : null}
      </div>
    </div>
  )
}

export function DetailEmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="bt-detail-panel">
      <div className="bt-card bt-detail-card bt-detail-empty">
        <div className="bt-detail-empty-icon" aria-hidden="true">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <rect x="4" y="5" width="16" height="14" rx="2" stroke="currentColor" strokeWidth="1.5" />
            <path d="M8 9H16M8 13H13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </div>
        <h3 className="bt-detail-empty-title">{title}</h3>
        {description ? <p className="bt-detail-empty-desc">{description}</p> : null}
        {action ? <div className="bt-detail-empty-action">{action}</div> : null}
      </div>
    </div>
  )
}

export function DetailSection({
  title,
  description,
  children,
}: {
  title?: string
  description?: string
  children: ReactNode
}) {
  return (
    <section className="bt-detail-section">
      {title ? <h3 className="bt-detail-section-title">{title}</h3> : null}
      {description ? <p className="bt-detail-section-desc">{description}</p> : null}
      {children}
    </section>
  )
}

export function DetailActions({ children }: { children: ReactNode }) {
  return <div className="bt-detail-actions">{children}</div>
}

type FieldProps = {
  label: string
  children: ReactNode
  className?: string
  hint?: string
}

export function FormField({ label, children, className = '', hint }: FieldProps) {
  return (
    <label className={`bt-form-field ${className}`.trim()}>
      <span className="bt-label">{label}</span>
      {children}
      {hint ? <span className="bt-field-hint">{hint}</span> : null}
    </label>
  )
}

export function DetailField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="bt-detail-field">
      <div className="bt-detail-field-label">{label}</div>
      <div className="bt-detail-field-value">{value}</div>
    </div>
  )
}

export function BtAlert({
  tone,
  children,
  className = '',
}: {
  tone: 'success' | 'error' | 'warning' | 'info'
  children: ReactNode
  className?: string
}) {
  const map = {
    success: 'bt-alert-success',
    error: 'bt-alert-error',
    warning: 'bt-alert-warning',
    info: 'bt-alert-info',
  }
  return <div className={`bt-alert ${map[tone]} ${className}`.trim()}>{children}</div>
}
