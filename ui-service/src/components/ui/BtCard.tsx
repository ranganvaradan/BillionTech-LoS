import type { HTMLAttributes, ReactNode } from 'react'

type Props = HTMLAttributes<HTMLDivElement> & {
  children: ReactNode
}

export function BtCard({ children, className = '', ...rest }: Props) {
  return (
    <div className={['bt-card', className].filter(Boolean).join(' ')} {...rest}>
      {children}
    </div>
  )
}

type HeaderProps = HTMLAttributes<HTMLDivElement> & {
  title?: ReactNode
  actions?: ReactNode
}

export function BtCardHeader({ title, actions, className = '', ...rest }: HeaderProps) {
  return (
    <div className={['bt-card-header', className].filter(Boolean).join(' ')} {...rest}>
      {title ? <div className="bt-card-title">{title}</div> : null}
      {actions ? <div>{actions}</div> : null}
    </div>
  )
}
