export type BtButtonVariant = 'primary' | 'secondary' | 'danger'
export type BtButtonSize = 'default' | 'sm'

export function btButtonClass(
  variant: BtButtonVariant = 'primary',
  className = '',
  size: BtButtonSize = 'default',
): string {
  const variantClass =
    variant === 'primary' ? 'bt-btn-primary' : variant === 'danger' ? 'bt-btn-danger' : 'bt-btn-secondary'
  const sizeClass = size === 'sm' ? 'bt-btn-sm' : ''
  return ['bt-btn', variantClass, sizeClass, className].filter(Boolean).join(' ')
}

export function sidebarLinkClass(isActive: boolean): string {
  return isActive ? 'bt-sidebar-link active' : 'bt-sidebar-link'
}
