export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      className="flex min-h-[12rem] items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600"
      role="status"
    >
      <div className="flex items-center gap-2 text-sm">
        <span
          className="inline-block size-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-700"
          aria-hidden
        />
        {label}
      </div>
    </div>
  )
}
