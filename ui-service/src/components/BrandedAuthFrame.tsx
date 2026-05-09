import { type ReactNode } from 'react'

const COMPANY = 'Billionloans Financial Services Private Ltd.'

type BrandedAuthFrameProps = {
  children: ReactNode
  title: string
  subtitle: string
  /** Optional: override default (borrower self-service) */
  variant?: 'borrower' | 'staff'
}

/**
 * Shared branding for /login, /borrower/login, register, and forgot-password flows.
 */
export function BrandedAuthFrame({ children, title, subtitle, variant = 'borrower' }: BrandedAuthFrameProps) {
  return (
    <div className="min-h-screen bg-gradient-to-b from-bl-navy/5 via-bl-canvas to-bl-canvas px-4 py-10 sm:py-12">
      <div className="mx-auto w-full max-w-md rounded-lg border border-slate-200/90 bg-white p-6 shadow-md">
        <div className="mb-1 flex flex-col items-center text-center">
          <div className="flex min-h-[2.75rem] w-full max-w-[200px] items-center justify-center">
            <img
              src="/brand/BillionLoans_Logo_Final_noBG.png"
              alt="Billionloans"
              className="h-auto max-h-11 w-full max-w-[180px] object-contain drop-shadow-sm"
            />
          </div>
          <p className="mt-2 text-[12px] font-medium leading-tight text-bl-navy sm:text-sm">{COMPANY}</p>
          <h1 className="mt-2 text-lg font-semibold text-bl-navy">{title}</h1>
          <p
            className={[
              'mt-1 text-sm',
              variant === 'borrower' ? 'text-slate-600' : 'text-slate-600',
            ].join(' ')}
          >
            {subtitle}
          </p>
        </div>
        {children}
        <div className="mt-5 flex items-center justify-center gap-1.5 border-t border-slate-100 pt-3">
          <span className="text-[11px] text-slate-500">Powered by</span>
          <img
            src="/brand/BillionTech_Logo_Final.png"
            alt="BillionTech"
            className="h-[18px] w-auto object-contain"
          />
        </div>
      </div>
    </div>
  )
}
