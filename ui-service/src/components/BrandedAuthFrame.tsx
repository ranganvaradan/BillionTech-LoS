import { type ReactNode } from 'react'
import { BrandLogo } from '@/components/BrandLogo'
import { PoweredByFooter } from '@/components/ui/PoweredByFooter'
import { COMPANY_DISPLAY_NAME } from '@/catalog/brand'

type BrandedAuthFrameProps = {
  children: ReactNode
  title: string
  subtitle: string
  variant?: 'borrower' | 'staff'
}

export function BrandedAuthFrame({ children, title, subtitle }: BrandedAuthFrameProps) {
  return (
    <div className="bt-auth-split">
      <div className="bt-auth-panel">
        <div className="bt-auth-card">
          <div className="mb-6 text-center">
            <BrandLogo variant="billiontech" tone="dark" className="text-2xl" />
            <p className="mt-3 text-sm font-semibold text-[var(--bt-gray-900)]">{COMPANY_DISPLAY_NAME}</p>
            <h1 className="mt-2 font-display text-lg font-bold text-[var(--bt-gray-900)]">{title}</h1>
            <p className="mt-1 text-sm text-[var(--bt-gray-500)]">{subtitle}</p>
          </div>
          {children}
          <PoweredByFooter className="mt-6 border-0 bg-transparent" />
        </div>
      </div>
      <div className="bt-auth-hero">
        <div className="max-w-sm text-center">
          <h2 className="font-display text-2xl font-extrabold text-[var(--bt-orange)]">Loans made simple</h2>
          <p className="mt-3 text-sm leading-relaxed text-[var(--bt-gray-700)]">
            Secure access to your BillionTech loan origination workspace.
          </p>
        </div>
      </div>
    </div>
  )
}
