import { softFixtureLabel } from '@/lib/creditIntelligence/businessLexicon'

export function CiFixtureBanner({ text }: { text?: string }) {
  const label = softFixtureLabel(text)
  return (
    <div
      className="mb-4 rounded-md border border-amber-400 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-950"
      role="status"
    >
      <span className="mr-2 inline-block rounded bg-amber-700 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide text-white">
        Demo
      </span>
      {label}
      <p className="mt-1 text-xs font-normal text-amber-800">
        Assistive review only — production underwriting remains authoritative. Verified production authority is off.
      </p>
    </div>
  )
}
