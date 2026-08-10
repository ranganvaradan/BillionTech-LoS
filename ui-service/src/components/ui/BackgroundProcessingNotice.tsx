type Props = {
  visible: boolean
  label?: string
  className?: string
}

/** Banner shown after a long provider call exceeds the short blocking window. */
export function BackgroundProcessingNotice({
  visible,
  label = 'Still processing in the background',
  className,
}: Props) {
  if (!visible) return null
  return (
    <div
      role="status"
      className={
        className ??
        'rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950'
      }
    >
      <p className="font-medium">{label}</p>
      <p className="mt-0.5 text-xs text-amber-900/90">
        This can take up to several minutes with the provider. Controls are available again — when the
        response arrives you will see success, failure, or timeout with retry options.
      </p>
    </div>
  )
}
