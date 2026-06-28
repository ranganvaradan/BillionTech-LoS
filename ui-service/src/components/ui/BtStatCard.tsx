export type BtStatAccent = 'orange' | 'green' | 'red' | 'gray' | 'blue' | 'amber' | 'purple'

const border: Record<BtStatAccent, string> = {
  orange: 'border-t-[var(--bt-orange)]',
  green: 'border-t-[var(--bt-green)]',
  red: 'border-t-[var(--bt-red)]',
  gray: 'border-t-[var(--bt-gray-400)]',
  blue: 'border-t-[var(--bt-blue)]',
  amber: 'border-t-[var(--bt-amber)]',
  purple: 'border-t-[#722ed1]',
}

const valueColor: Record<BtStatAccent, string> = {
  orange: 'text-[var(--bt-orange)]',
  green: 'text-[var(--bt-green)]',
  red: 'text-[var(--bt-red)]',
  gray: 'text-[var(--bt-gray-600)]',
  blue: 'text-[var(--bt-blue)]',
  amber: 'text-[var(--bt-amber)]',
  purple: 'text-[#722ed1]',
}

const ACCENT_CYCLE: BtStatAccent[] = ['orange', 'green', 'amber', 'red', 'blue', 'purple']

export function statAccentAt(index: number): BtStatAccent {
  return ACCENT_CYCLE[index % ACCENT_CYCLE.length]!
}

/** Readable sentence-case label used on dashboard stat tiles. */
export const btStatLabelClass =
  'bt-stat-label normal-case tracking-normal text-[12px] text-[var(--bt-gray-600)]'

type Props = {
  title: string
  value: string
  subtitle?: string
  accent?: BtStatAccent
}

export function BtStatCard({ title, value, subtitle, accent = 'orange' }: Props) {
  return (
    <div className={`bt-card border-t-4 ${border[accent]} px-5 pt-5 pb-5`}>
      <div className={btStatLabelClass}>{title}</div>
      <p className={`mt-2 font-serif text-[28px] font-normal leading-none tabular-nums ${valueColor[accent]}`}>
        {value}
      </p>
      {subtitle ? <p className="mt-2 text-xs text-[var(--bt-gray-500)]">{subtitle}</p> : null}
    </div>
  )
}
