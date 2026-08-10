export type InvoiceOnboardingChoice = 'BORROWER' | 'ANCHOR'

const OPTIONS: {
  value: InvoiceOnboardingChoice
  title: string
  icon: string
  subtitle: string
  description: string
}[] = [
  {
    value: 'BORROWER',
    title: 'Borrower',
    icon: '👤',
    subtitle: 'Standard borrower onboarding flow',
    description: 'Personal or business applicant onboarding for invoice discounting.',
  },
  {
    value: 'ANCHOR',
    title: 'Anchor',
    icon: '🏢',
    subtitle: 'Invoice discounting anchor onboarding',
    description: 'Corporate anchor entity onboarding for invoice discounting programs.',
  },
]

export type InvoiceOnboardingTypeCardsProps = {
  value: '' | InvoiceOnboardingChoice
  onChange: (value: InvoiceOnboardingChoice) => void
  disabled?: boolean
}

export function InvoiceOnboardingTypeCards({ value, onChange, disabled }: InvoiceOnboardingTypeCardsProps) {
  return (
    <div className="sm:col-span-2" role="radiogroup" aria-label="Onboarding type">
      <span className="mb-2 block text-xs font-medium text-slate-500">Onboarding type *</span>
      <div className="grid gap-3 sm:grid-cols-2">
        {OPTIONS.map((opt) => {
          const selected = value === opt.value
          return (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled}
              tabIndex={disabled ? -1 : 0}
              onClick={() => onChange(opt.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  onChange(opt.value)
                }
              }}
              className={[
                'flex w-full flex-col rounded-lg border p-4 text-left shadow-sm transition-colors',
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-900 focus-visible:ring-offset-2',
                disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer',
                selected
                  ? 'border-slate-900 bg-slate-50 ring-1 ring-slate-900/10'
                  : 'border-slate-200 bg-white hover:border-slate-300 ',
              ].join(' ')}
            >
              <span className="flex items-start gap-3">
                <span
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white text-lg"
                  aria-hidden
                >
                  {opt.icon}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block bt-card-title">{opt.title}</span>
                  <span className="mt-0.5 block text-xs font-medium text-slate-600">{opt.subtitle}</span>
                </span>
              </span>
              <span className="mt-3 block text-xs leading-relaxed text-slate-600">{opt.description}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
