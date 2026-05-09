import { buildIntakeReadback } from '@/lib/intake/intakeReadback'
import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import type { ApplicationResponse } from '@/types/application'

export function CollateralIntakeStaffPanel({ app }: { app: ApplicationResponse }) {
  const secured = requiresCollateral(app.loanProduct)
  const cinfo = app.collateralInfo as Record<string, unknown> | null | undefined
  const has = Boolean(cinfo?.[BORROWER_INTAKE_KEY] ?? cinfo?.borrowerIntake)
  const section = buildIntakeReadback(app).find((s) => s.title === 'Collateral (borrower intake)')

  if (!secured) {
    return <p className="text-sm text-slate-600">This product is not a collateral workflow in the current configuration.</p>
  }
  if (!has) {
    return (
      <div className="rounded border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
        <p className="font-medium">Collateral not captured yet</p>
        <p className="mt-1">The product <span className="font-mono text-slate-800">{app.loanProduct}</span> requires security details. If intake is still in progress, the applicant may not have completed the collateral step.</p>
      </div>
    )
  }
  if (!section?.rows.length) {
    return <p className="text-sm text-slate-600">No collateral rows to display.</p>
  }
  return (
    <div className="space-y-4">
      <div className="grid gap-2 sm:grid-cols-2">
        {section.rows.map((r) => (
          <div key={r.label} className="rounded border border-slate-100 p-2 text-sm">
            <div className="text-xs font-medium text-slate-500">{r.label}</div>
            <div className="text-slate-900">{r.value}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
