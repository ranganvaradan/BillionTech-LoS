export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="bt-card flex min-h-[12rem] items-center justify-center text-[var(--bt-gray-500)]" role="status">
      <div className="flex items-center gap-2 text-sm">
        <span
          className="inline-block size-4 animate-spin rounded-full border-2 border-[var(--bt-gray-300)] border-t-[var(--bt-orange)]"
          aria-hidden
        />
        {label}
      </div>
    </div>
  )
}
