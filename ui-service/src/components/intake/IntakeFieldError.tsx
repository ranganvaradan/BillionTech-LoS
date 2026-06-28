type Props = {
  message?: string | null
}

export function IntakeFieldError({ message }: Props) {
  if (!message) return null
  return (
    <p className="mt-1 text-xs text-[var(--bt-red)]" role="alert">
      {message}
    </p>
  )
}
