import { buildBodmasPreview, type FormulaOperandValue } from '@/components/credit/formulaBodmas'

type Props = {
  expression?: string | null
  operands?: FormulaOperandValue[] | null
  result?: string | null
  title?: string
  compact?: boolean
  className?: string
}

export function BodmasExpressionPreview({
  expression,
  operands,
  result,
  title = 'Expression preview',
  compact = false,
  className = '',
}: Props) {
  const preview = buildBodmasPreview(expression, operands)
  const showValues = Boolean(preview.withValues)
  const finalResult = result != null && String(result).trim() !== '' ? String(result) : preview.computedResult
  const displayExpression =
    preview.parseOk && preview.fullyParenthesized ? preview.fullyParenthesized : preview.original

  return (
    <div
      className={[
        'rounded-lg border border-indigo-100 bg-gradient-to-br from-indigo-50/90 via-white to-sky-50/60',
        compact ? 'px-2.5 py-2' : 'px-3 py-3',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span className={`font-semibold uppercase tracking-wide text-indigo-700 ${compact ? 'text-[10px]' : 'text-[11px]'}`}>
        {title}
      </span>

      <div className={`mt-2 space-y-2 ${compact ? 'text-[11px]' : 'text-sm'}`}>
        <p className="break-all font-mono font-medium text-slate-900">{displayExpression}</p>

        {showValues ? (
          <div className="rounded-md border border-indigo-100/80 bg-white/80 px-2.5 py-2">
            <p className="break-all font-mono text-indigo-950">{preview.withValues}</p>
          </div>
        ) : null}

        {(operands?.length ?? 0) > 0 &&
        operands!.some((op) => op.valueUsed != null && String(op.valueUsed).trim() !== '') ? (
          <ul className={`grid gap-1 ${compact ? '' : 'sm:grid-cols-2'}`}>
            {operands!.map((op, idx) => (
              <li
                key={`${op.parameter ?? 'op'}-${idx}`}
                className="rounded border border-slate-100 bg-white/70 px-2 py-1 text-[11px] text-slate-700"
              >
                <span className="font-medium text-slate-900">{op.parameter || '—'}</span>
                {op.source ? <span className="text-slate-500"> [{op.source}]</span> : null}
                <span className="ml-1 font-mono">= {op.valueUsed ?? '—'}</span>
              </li>
            ))}
          </ul>
        ) : null}

        {preview.steps.length > 0 ? (
          <ol className="list-decimal space-y-0.5 pl-4 font-mono text-[11px] text-slate-800">
            {preview.steps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ol>
        ) : null}

        {preview.parseOk ? (
          <p className="text-[11px] text-slate-500">
            Division and multiplication are evaluated before addition and subtraction. Brackets force evaluation order.
          </p>
        ) : null}

        {finalResult != null && finalResult !== '' ? (
          <div className="flex flex-wrap items-center gap-2 border-t border-indigo-100 pt-2">
            <span className="text-[10px] font-medium uppercase tracking-wide text-slate-500">Result</span>
            <span className="rounded-md bg-indigo-600 px-2 py-0.5 font-mono text-xs font-semibold text-white">
              {finalResult}
            </span>
          </div>
        ) : null}
      </div>
    </div>
  )
}
