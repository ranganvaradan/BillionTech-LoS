import { lmsTenureUnitLabel } from '@/catalog/lmsTenureUnits'

/** Read-only LMS config copied from the active workflow (edited on Workflows admin only). */
export function LmsWorkflowConfigReadonly({
  lmsProductCode,
  lmsTenureUnit,
}: {
  lmsProductCode: string
  lmsTenureUnit: string
}) {
  return (
    <>
      <div className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">LMS product code</span>
        <p className="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">{lmsProductCode}</p>
        <p className="mt-0.5 text-xs text-slate-500">From active workflow — change under Admin → Workflows.</p>
      </div>
      <div className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">LMS tenure type</span>
        <p className="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">
          {lmsTenureUnitLabel(lmsTenureUnit)}
        </p>
      </div>
    </>
  )
}
