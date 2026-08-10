import { formatAmountInWords, formatAmountShortScale } from '@/lib/amountInWords'

function parseNumeric(value: string | number | null | undefined): number | null {
  if (value == null || value === '') return null
  const n = typeof value === 'number' ? value : Number(String(value).replace(/,/g, '').trim())
  return Number.isFinite(n) && n >= 0 ? n : null
}

/** Compact Indian-words line under an amount input (legacy / dense forms). */
export function AmountInputHint({
  value,
  className = '',
}: {
  value: string | number | null | undefined
  className?: string
}) {
  const words = formatAmountInWords(value)
  if (!words) return null
  return (
    <p className={`mt-1 text-[11px] text-slate-500 ${className}`.trim()}>
      {words}
    </p>
  )
}

type PreviewProps = {
  value: string | number | null | undefined
  label?: string
  className?: string
  compact?: boolean
}

/**
 * Live amount reading pane — short scale headline (“50 thousand”) + full words.
 * Use beside amount inputs on admin config screens.
 */
export function AmountPreviewPane({
  value,
  label,
  className = '',
  compact = false,
}: PreviewProps) {
  const n = parseNumeric(value)
  const empty = n == null
  const short = empty ? '—' : formatAmountShortScale(n)
  const words = empty ? 'Enter an amount' : formatAmountInWords(n)

  return (
    <aside
      className={[
        'flex flex-col justify-center rounded-lg border border-sky-200/80 bg-gradient-to-br from-sky-50 via-white to-slate-50',
        compact ? 'min-h-[3.25rem] px-3 py-2' : 'min-h-[4.5rem] px-3.5 py-2.5',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      aria-live="polite"
    >
      {label ? (
        <p className="text-[10px] font-semibold uppercase tracking-wide text-sky-800/70">{label}</p>
      ) : null}
      <p
        className={[
          'font-semibold tabular-nums tracking-tight text-slate-900',
          compact ? 'text-base' : 'text-lg',
          label ? (compact ? 'mt-0.5' : 'mt-1') : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {short}
      </p>
      <p className={`leading-snug text-slate-500 ${compact ? 'mt-0.5 text-[10px]' : 'mt-1 text-[11px]'}`}>{words}</p>
    </aside>
  )
}

type SplitProps = {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  inputMode?: 'decimal' | 'numeric' | 'text'
  className?: string
  inputClassName?: string
  previewLabel?: string
  disabled?: boolean
  id?: string
  'aria-label'?: string
}

/**
 * Amount input + right-side reading pane (small split layout).
 * Updates live as the numeric value changes.
 */
export function AmountInputSplit({
  value,
  onChange,
  placeholder,
  inputMode = 'decimal',
  className = '',
  inputClassName = '',
  previewLabel,
  disabled,
  id,
  'aria-label': ariaLabel,
}: SplitProps) {
  return (
    <div
      className={`grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(9.5rem,12rem)] sm:items-stretch ${className}`.trim()}
    >
      <input
        id={id}
        className={`bt-input w-full ${inputClassName}`.trim()}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        inputMode={inputMode}
        disabled={disabled}
        aria-label={ariaLabel}
      />
      <AmountPreviewPane value={value} label={previewLabel} compact />
    </div>
  )
}
